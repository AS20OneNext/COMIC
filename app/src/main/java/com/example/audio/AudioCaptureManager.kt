package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import com.example.model.AudioSourceMode
import com.example.model.DubbingError
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface AudioCaptureSource {
  val capturedChunks: Flow<ByteArray>
  val inputRms: StateFlow<Float>
  val droppedChunks: StateFlow<Long>
  val hasAudibleSignal: StateFlow<Boolean>

  fun start(
    mediaProjection: MediaProjection,
    sourceMode: AudioSourceMode,
    scope: CoroutineScope,
    onProjectionRevoked: () -> Unit,
    onCaptureError: (DubbingError) -> Unit,
  ): Boolean

  fun setStreamingEnabled(enabled: Boolean)

  fun stopAndRelease()
}

class AndroidAudioCaptureManager : AudioCaptureSource {

  private val chunkChannel =
    Channel<ByteArray>(
      capacity = BOUNDED_CHUNK_CAPACITY,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
      onUndeliveredElement = {
        val count = droppedCounter.incrementAndGet()
        _droppedChunks.value = count
        Log.w(TAG, "Input audio queue overflow; dropped chunk #$count")
      },
    )

  override val capturedChunks: Flow<ByteArray> = chunkChannel.receiveAsFlow()

  private val _inputRms = MutableStateFlow(0f)
  override val inputRms: StateFlow<Float> = _inputRms.asStateFlow()

  private val droppedCounter = AtomicLong(0L)
  private val _droppedChunks = MutableStateFlow(0L)
  override val droppedChunks: StateFlow<Long> = _droppedChunks.asStateFlow()

  private val _hasAudibleSignal = MutableStateFlow(false)
  override val hasAudibleSignal: StateFlow<Boolean> = _hasAudibleSignal.asStateFlow()

  private val isRunning = AtomicBoolean(false)
  private val streamingEnabled = AtomicBoolean(false)
  private var audioRecord: AudioRecord? = null
  private var captureJob: Job? = null
  private var activeProjection: MediaProjection? = null
  private var projectionCallback: MediaProjection.Callback? = null

  @SuppressLint("MissingPermission")
  override fun start(
    mediaProjection: MediaProjection,
    sourceMode: AudioSourceMode,
    scope: CoroutineScope,
    onProjectionRevoked: () -> Unit,
    onCaptureError: (DubbingError) -> Unit,
  ): Boolean {
    if (!isRunning.compareAndSet(false, true)) {
      return false
    }

    streamingEnabled.set(false)
    droppedCounter.set(0L)
    _droppedChunks.value = 0L
    _inputRms.value = 0f
    _hasAudibleSignal.value = false

    val callback =
      object : MediaProjection.Callback() {
        override fun onStop() {
          if (isRunning.get()) {
            onProjectionRevoked()
          }
        }
      }
    projectionCallback = callback
    activeProjection = mediaProjection
    mediaProjection.registerCallback(callback, Handler(Looper.getMainLooper()))

    val configBuilder =
      AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
        .addMatchingUsage(AudioAttributes.USAGE_GAME)
        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)

    when (sourceMode) {
      is AudioSourceMode.SpecificApp -> {
        configBuilder.addMatchingUid(sourceMode.uid)
      }
      AudioSourceMode.AllEligible -> {
        configBuilder.excludeUid(Process.myUid())
      }
    }

    val captureConfig =
      try {
        configBuilder.build()
      } catch (t: Throwable) {
        stopAndRelease()
        onCaptureError(DubbingError.UnsupportedAudioSource(t.message))
        return false
      }

    val selection = createBestAudioRecord(captureConfig)
    if (selection == null) {
      stopAndRelease()
      onCaptureError(DubbingError.UnsupportedAudioSource("AudioRecord initialization failed"))
      return false
    }

    val (record, sampleRate, channels) = selection
    audioRecord = record

    try {
      record.startRecording()
      if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
        stopAndRelease()
        onCaptureError(DubbingError.UnsupportedAudioSource("AudioRecord failed to start"))
        return false
      }
    } catch (se: SecurityException) {
      stopAndRelease()
      onCaptureError(DubbingError.AudioCaptureDenied)
      return false
    } catch (t: Throwable) {
      stopAndRelease()
      onCaptureError(DubbingError.UnsupportedAudioSource(t.message))
      return false
    }

    val adapter =
      PcmStreamAdapter(
        inputSampleRateHz = sampleRate,
        inputChannels = channels,
        targetSampleRateHz = AudioProcessor.TARGET_SAMPLE_RATE_HZ,
        chunkBytes = AudioProcessor.TARGET_CHUNK_BYTES,
      )

    captureJob =
      scope.launch(Dispatchers.IO) {
        val readSizeBytes =
          ((sampleRate * 40) / 1_000) * channels * AudioProcessor.BYTES_PER_SAMPLE
        val readBuffer = ByteArray(readSizeBytes.coerceAtLeast(1024))
        while (isActive && isRunning.get()) {
          val bytesRead =
            try {
              record.read(readBuffer, 0, readBuffer.size)
            } catch (t: Throwable) {
              if (isRunning.get()) {
                onCaptureError(DubbingError.UnsupportedAudioSource(t.message))
              }
              break
            }

          if (bytesRead < 0) {
            if (isRunning.get()) {
              onCaptureError(
                DubbingError.UnsupportedAudioSource("AudioRecord read error code: $bytesRead")
              )
            }
            break
          }

          if (bytesRead == 0) continue

          // Keep hardware pipeline primed before Gemini setup completes without inflating drop metrics
          if (!streamingEnabled.get()) continue

          try {
            adapter.process(readBuffer, bytesRead) { chunk ->
              if (!isRunning.get()) return@process
              _inputRms.value = AudioProcessor.computeNormalizedRms(chunk)
              if (!_hasAudibleSignal.value && AudioProcessor.hasAudibleEnergy(chunk)) {
                _hasAudibleSignal.value = true
              }
              val result = chunkChannel.trySend(chunk)
              if (!result.isSuccess) {
                val count = droppedCounter.incrementAndGet()
                _droppedChunks.value = count
                Log.w(TAG, "Input audio channel full; dropped chunk #$count")
              }
            }
          } catch (pcmError: InvalidPcmException) {
            onCaptureError(DubbingError.InvalidPcmInput(pcmError.message))
            break
          }
        }
      }

    return true
  }

  override fun setStreamingEnabled(enabled: Boolean) {
    streamingEnabled.set(enabled)
  }

  @SuppressLint("MissingPermission")
  private fun createBestAudioRecord(
    captureConfig: AudioPlaybackCaptureConfiguration
  ): Triple<AudioRecord, Int, Int>? {
    val candidates =
      listOf(
        16_000 to AudioFormat.CHANNEL_IN_MONO,
        48_000 to AudioFormat.CHANNEL_IN_STEREO,
        48_000 to AudioFormat.CHANNEL_IN_MONO,
        44_100 to AudioFormat.CHANNEL_IN_STEREO,
      )

    for ((sampleRate, channelMask) in candidates) {
      val channels = if (channelMask == AudioFormat.CHANNEL_IN_STEREO) 2 else 1
      val minBuf =
        AudioRecord.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
      if (minBuf <= 0) continue
      val bufferSize = maxOf(minBuf * 2, (sampleRate / 5) * channels * 2)

      val format =
        AudioFormat.Builder()
          .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
          .setSampleRate(sampleRate)
          .setChannelMask(channelMask)
          .build()

      val record =
        try {
          AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSize)
            .setAudioPlaybackCaptureConfig(captureConfig)
            .build()
        } catch (_: Throwable) {
          null
        }

      if (record != null && record.state == AudioRecord.STATE_INITIALIZED) {
        return Triple(record, sampleRate, channels)
      } else {
        record?.release()
      }
    }
    return null
  }

  override fun stopAndRelease() {
    isRunning.set(false)
    streamingEnabled.set(false)
    captureJob?.cancel()
    captureJob = null

    val record = audioRecord
    audioRecord = null
    if (record != null) {
      runCatching {
        if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
          record.stop()
        }
      }
      runCatching { record.release() }
    }

    val proj = activeProjection
    val cb = projectionCallback
    activeProjection = null
    projectionCallback = null
    if (proj != null && cb != null) {
      runCatching { proj.unregisterCallback(cb) }
    }
    if (proj != null) {
      runCatching { proj.stop() }
    }

    while (chunkChannel.tryReceive().isSuccess) {
      // drain bounded buffer
    }
    _inputRms.value = 0f
  }

  companion object {
    private const val TAG = "ComicAudioCapture"
    const val BOUNDED_CHUNK_CAPACITY = 12
  }
}
