package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.model.DubbingError
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface AudioPlaybackSink {
  val outputRms: StateFlow<Float>
  val isPlaying: StateFlow<Boolean>
  val underrunCount: StateFlow<Int>
  val droppedChunks: StateFlow<Long>

  fun start(scope: CoroutineScope, initialVolume: Float, onError: (DubbingError) -> Unit): Boolean

  fun enqueueAudio(pcm24kMonoLe: ByteArray)

  fun setVolume(volume: Float)

  fun pauseAndFlush()

  fun resume()

  fun flushQueue()

  fun stopAndRelease()
}

class AndroidAudioPlaybackManager : AudioPlaybackSink {

  private val playbackQueue =
    Channel<ByteArray>(
      capacity = BOUNDED_PLAYBACK_CHUNKS,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
      onUndeliveredElement = {
        val count = droppedCounter.incrementAndGet()
        _droppedChunks.value = count
        Log.w(TAG, "Playback queue overflow; dropped oldest audio chunk #$count")
      },
    )

  private val _outputRms = MutableStateFlow(0f)
  override val outputRms: StateFlow<Float> = _outputRms.asStateFlow()

  private val _isPlaying = MutableStateFlow(false)
  override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

  private val _underrunCount = MutableStateFlow(0)
  override val underrunCount: StateFlow<Int> = _underrunCount.asStateFlow()

  private val droppedCounter = AtomicLong(0L)
  private val _droppedChunks = MutableStateFlow(0L)
  override val droppedChunks: StateFlow<Long> = _droppedChunks.asStateFlow()

  private val isStarted = AtomicBoolean(false)
  private val isPaused = AtomicBoolean(false)

  @Volatile private var volumeLevel: Float = 1.0f
  private var audioTrack: AudioTrack? = null
  private var writerJob: Job? = null

  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
  override fun start(
    scope: CoroutineScope,
    initialVolume: Float,
    onError: (DubbingError) -> Unit,
  ): Boolean {
    if (!isStarted.compareAndSet(false, true)) {
      return false
    }

    volumeLevel = initialVolume.coerceIn(0f, 1.5f)
    isPaused.set(false)
    droppedCounter.set(0L)
    _droppedChunks.value = 0L
    _underrunCount.value = 0
    _outputRms.value = 0f
    _isPlaying.value = false

    val sampleRate = AudioProcessor.OUTPUT_SAMPLE_RATE_HZ
    val minBufferBytes =
      AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
      )
    if (minBufferBytes <= 0) {
      stopAndRelease()
      onError(DubbingError.AudioOutputFailure("Invalid AudioTrack minBufferSize: $minBufferBytes"))
      return false
    }

    val bufferSize = maxOf(minBufferBytes, (sampleRate / 5) * AudioProcessor.BYTES_PER_SAMPLE)

    val track =
      try {
        AudioTrack.Builder()
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
              .build()
          )
          .setAudioFormat(
            AudioFormat.Builder()
              .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
              .setSampleRate(sampleRate)
              .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
              .build()
          )
          .setBufferSizeInBytes(bufferSize)
          .setTransferMode(AudioTrack.MODE_STREAM)
          .build()
      } catch (t: Throwable) {
        stopAndRelease()
        onError(DubbingError.AudioOutputFailure(t.message))
        return false
      }

    if (track.state != AudioTrack.STATE_INITIALIZED) {
      runCatching { track.release() }
      stopAndRelease()
      onError(DubbingError.AudioOutputFailure("AudioTrack did not initialize"))
      return false
    }

    audioTrack = track
    applyTrackVolume(track, volumeLevel)
    runCatching { track.play() }.onFailure {
      stopAndRelease()
      onError(DubbingError.AudioOutputFailure(it.message))
      return false
    }

    writerJob =
      scope.launch(Dispatchers.IO) {
        var gainScratch = ByteArray(9600)
        for (pcm in playbackQueue) {
          if (!isActive || !isStarted.get()) break
          if (isPaused.get() || pcm.isEmpty()) continue
          if (pcm.size % 2 != 0) {
            onError(DubbingError.InvalidPcmInput("Received odd PCM byte length: ${pcm.size}"))
            continue
          }

          val currentTrack = audioTrack ?: break
          val gain = volumeLevel
          val payloadToWrite: ByteArray
          if (gain > 1.0f) {
            if (gainScratch.size < pcm.size) {
              gainScratch = ByteArray(pcm.size)
            }
            AudioProcessor.applyPcm16GainInto(pcm, gainScratch, pcm.size, gain)
            payloadToWrite = gainScratch
          } else {
            payloadToWrite = pcm
          }

          _outputRms.value = AudioProcessor.computeNormalizedRms(payloadToWrite, pcm.size)
          _isPlaying.value = true

          var offset = 0
          while (offset < pcm.size && isActive && isStarted.get() && !isPaused.get()) {
            val written =
              currentTrack.write(
                payloadToWrite,
                offset,
                pcm.size - offset,
                AudioTrack.WRITE_BLOCKING,
              )
            if (written < 0) {
              if (isStarted.get()) {
                onError(DubbingError.AudioOutputFailure("AudioTrack write error: $written"))
              }
              break
            }
            offset += written
          }

          runCatching { _underrunCount.value = currentTrack.underrunCount }
          if (playbackQueue.isEmpty) {
            _isPlaying.value = false
            _outputRms.value = 0f
          }
        }
        _isPlaying.value = false
        _outputRms.value = 0f
      }

    return true
  }

  override fun enqueueAudio(pcm24kMonoLe: ByteArray) {
    if (!isStarted.get() || isPaused.get() || pcm24kMonoLe.isEmpty()) return
    val res = playbackQueue.trySend(pcm24kMonoLe)
    if (!res.isSuccess) {
      val count = droppedCounter.incrementAndGet()
      _droppedChunks.value = count
      Log.w(TAG, "Playback queue full; dropped chunk #$count")
    }
  }

  override fun setVolume(volume: Float) {
    volumeLevel = volume.coerceIn(0f, 1.5f)
    audioTrack?.let { applyTrackVolume(it, volumeLevel) }
  }

  private fun applyTrackVolume(track: AudioTrack, volume: Float) {
    runCatching { track.setVolume(volume.coerceIn(0f, 1f)) }
  }

  override fun pauseAndFlush() {
    isPaused.set(true)
    _isPlaying.value = false
    _outputRms.value = 0f
    flushQueue()
    audioTrack?.let { track ->
      runCatching {
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
          track.pause()
          track.flush()
        }
      }
    }
  }

  override fun resume() {
    if (!isStarted.get()) return
    isPaused.set(false)
    audioTrack?.let { track ->
      runCatching {
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
          track.play()
        }
      }
    }
  }

  override fun flushQueue() {
    while (playbackQueue.tryReceive().isSuccess) {
      // clear queued translated audio
    }
  }

  override fun stopAndRelease() {
    isStarted.set(false)
    isPaused.set(false)
    writerJob?.cancel()
    writerJob = null
    flushQueue()

    val track = audioTrack
    audioTrack = null
    if (track != null) {
      runCatching {
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING ||
          track.playState == AudioTrack.PLAYSTATE_PAUSED
        ) {
          track.pause()
          track.flush()
          track.stop()
        }
      }
      runCatching { track.release() }
    }
    _isPlaying.value = false
    _outputRms.value = 0f
  }

  companion object {
    private const val TAG = "ComicAudioPlayback"
    const val BOUNDED_PLAYBACK_CHUNKS = 24
  }
}
