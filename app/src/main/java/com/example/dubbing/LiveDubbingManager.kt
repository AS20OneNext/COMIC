package com.example.dubbing

import android.media.projection.MediaProjection
import com.example.audio.AudioCaptureSource
import com.example.audio.AudioPlaybackSink
import com.example.audio.AudioProcessor
import com.example.data.ApiKeyAuthProvider
import com.example.gemini.DubbingException
import com.example.gemini.LiveTranslateClient
import com.example.model.AudioSourceMode
import com.example.model.DubbingError
import com.example.model.DubbingSessionState
import com.example.model.DubbingUiSnapshot
import com.example.model.SessionPerformanceMetrics
import com.example.model.TargetLanguage
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Deterministic 12-state FSM orchestrating COMIC V0.2 live speech-to-speech dubbing.
 * Enforces single-session exclusivity, structured coroutine cancellation, silent-idle recovery,
 * bounded reconnection backoff, and safe idempotent Stop cleanup.
 */
class LiveDubbingManager(
  private val apiKeyProvider: ApiKeyAuthProvider,
  private val captureSourceFactory: () -> AudioCaptureSource,
  private val playbackSinkFactory: () -> AudioPlaybackSink,
  private val geminiClientFactory: () -> LiveTranslateClient,
  private val androidApiLevelProvider: () -> Int = { android.os.Build.VERSION.SDK_INT },
  private val clockMsProvider: () -> Long = { System.currentTimeMillis() },
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val stateMutex = Mutex()
  private val sessionGeneration = AtomicLong(0L)

  private val _snapshot = MutableStateFlow(DubbingUiSnapshot())
  val snapshot: StateFlow<DubbingUiSnapshot> = _snapshot.asStateFlow()

  private var activeCapture: AudioCaptureSource? = null
  private var activePlayback: AudioPlaybackSink? = null
  private var activeGemini: LiveTranslateClient? = null

  private var streamingJob: Job? = null
  private var metricsJob: Job? = null
  private var timerJob: Job? = null
  private var watchdogJob: Job? = null
  private var reconnectJob: Job? = null

  private var currentApiKey: String? = null
  private var currentVolume: Float = 1.0f
  private var serviceTeardownCallback: (() -> Unit)? = null
  private var firstAudioSentTimeMs: Long = 0L
  private var firstTranslationReceived: Boolean = false
  @Volatile private var lastAudibleInputMs: Long = 0L

  suspend fun beginValidation(
    sourceMode: AudioSourceMode,
    targetLanguage: TargetLanguage,
    initialVolume: Float,
  ): Result<Unit> =
    stateMutex.withLock {
      val currentState = _snapshot.value.state
      if (currentState.isRunningOrStarting) {
        return Result.failure(IllegalStateException("Session already active: $currentState"))
      }

      if (androidApiLevelProvider() < MIN_ANDROID_API) {
        _snapshot.value =
          DubbingUiSnapshot(
            state = DubbingSessionState.ERROR,
            sourceMode = sourceMode,
            targetLanguage = targetLanguage,
            error = DubbingError.UnsupportedAndroidConfig,
          )
        return Result.failure(DubbingException(DubbingError.UnsupportedAndroidConfig))
      }

      currentVolume = initialVolume.coerceIn(0f, 1.5f)
      _snapshot.value =
        DubbingUiSnapshot(
          state = DubbingSessionState.VALIDATING,
          sourceMode = sourceMode,
          targetLanguage = targetLanguage,
        )

      if (sourceMode is AudioSourceMode.SpecificApp && sourceMode.uid <= 0) {
        _snapshot.update {
          it.copy(state = DubbingSessionState.ERROR, error = DubbingError.NoAppSelected)
        }
        return Result.failure(DubbingException(DubbingError.NoAppSelected))
      }

      val apiKey = apiKeyProvider.getCredential()?.trim()
      if (apiKey.isNullOrEmpty()) {
        _snapshot.update {
          it.copy(state = DubbingSessionState.ERROR, error = DubbingError.InvalidApiKey())
        }
        return Result.failure(DubbingException(DubbingError.InvalidApiKey()))
      }

      currentApiKey = apiKey
      _snapshot.update {
        it.copy(state = DubbingSessionState.REQUESTING_PERMISSION, error = null)
      }
      return Result.success(Unit)
    }

  suspend fun onPermissionDenied(error: DubbingError = DubbingError.AudioCaptureDenied) =
    stateMutex.withLock {
      val st = _snapshot.value.state
      if (st == DubbingSessionState.VALIDATING || st == DubbingSessionState.REQUESTING_PERMISSION) {
        _snapshot.update { it.copy(state = DubbingSessionState.ERROR, error = error) }
      }
    }

  suspend fun onPermissionCancelled() =
    stateMutex.withLock {
      if (_snapshot.value.state == DubbingSessionState.REQUESTING_PERMISSION) {
        _snapshot.update { it.copy(state = DubbingSessionState.IDLE, error = null) }
      }
    }

  suspend fun onStartingCaptureService() =
    stateMutex.withLock {
      if (_snapshot.value.state == DubbingSessionState.REQUESTING_PERMISSION) {
        _snapshot.update {
          it.copy(state = DubbingSessionState.INITIALIZING_CAPTURE, error = null)
        }
      }
    }

  suspend fun onForegroundServiceFailed(detail: String?) =
    stopSessionInternal(DubbingError.ForegroundServiceFailure(detail))

  suspend fun startPipelineWithProjection(
    mediaProjection: MediaProjection?,
    onTeardownService: () -> Unit,
  ) {
    val gen: Long
    val sourceMode: AudioSourceMode
    val targetLanguage: TargetLanguage
    val apiKey: String

    stateMutex.withLock {
      val current = _snapshot.value
      if (current.state != DubbingSessionState.INITIALIZING_CAPTURE &&
        current.state != DubbingSessionState.REQUESTING_PERMISSION
      ) {
        mediaProjection?.stop()
        return
      }
      if (mediaProjection == null) {
        _snapshot.update {
          it.copy(state = DubbingSessionState.ERROR, error = DubbingError.AudioCaptureDenied)
        }
        onTeardownService()
        return
      }

      val key = currentApiKey
      if (key.isNullOrBlank()) {
        mediaProjection.stop()
        _snapshot.update {
          it.copy(state = DubbingSessionState.ERROR, error = DubbingError.InvalidApiKey())
        }
        onTeardownService()
        return
      }

      gen = sessionGeneration.incrementAndGet()
      serviceTeardownCallback = onTeardownService
      sourceMode = current.sourceMode
      targetLanguage = current.targetLanguage
      apiKey = key
      firstAudioSentTimeMs = 0L
      firstTranslationReceived = false
      lastAudibleInputMs = 0L

      _snapshot.update {
        it.copy(
          state = DubbingSessionState.INITIALIZING_CAPTURE,
          elapsedSeconds = 0L,
          noAudioWarning = false,
          reconnectAttempt = 0,
          error = null,
          metrics = SessionPerformanceMetrics(),
        )
      }
    }

    val captureStartTime = clockMsProvider()
    val capture = captureSourceFactory()
    val playback = playbackSinkFactory()
    val gemini = geminiClientFactory()

    activeCapture = capture
    activePlayback = playback
    activeGemini = gemini

    val validProjection = mediaProjection ?: return
    val captureStarted =
      capture.start(
        mediaProjection = validProjection,
        sourceMode = sourceMode,
        scope = scope,
        onProjectionRevoked = {
          scope.launch {
            if (sessionGeneration.get() == gen && _snapshot.value.state.isRunningOrStarting) {
              stopSessionInternal(DubbingError.ProjectionStopped)
            }
          }
        },
        onCaptureError = { captureErr ->
          scope.launch {
            if (sessionGeneration.get() == gen && _snapshot.value.state.isRunningOrStarting) {
              stopSessionInternal(captureErr)
            }
          }
        },
      )

    if (!captureStarted || sessionGeneration.get() != gen) {
      if (sessionGeneration.get() == gen) {
        stopSessionInternal(DubbingError.UnsupportedAudioSource("Capture failed to initialize"))
      }
      return
    }

    val captureInitDelay = (clockMsProvider() - captureStartTime).coerceAtLeast(0L)

    val playbackStarted =
      playback.start(scope, currentVolume) { playbackErr ->
        scope.launch {
          if (sessionGeneration.get() == gen && _snapshot.value.state.isRunningOrStarting) {
            stopSessionInternal(playbackErr)
          }
        }
      }

    if (!playbackStarted || sessionGeneration.get() != gen) {
      if (sessionGeneration.get() == gen) {
        stopSessionInternal(DubbingError.AudioOutputFailure("Playback failed to initialize"))
      }
      return
    }

    stateMutex.withLock {
      if (sessionGeneration.get() != gen) return
      _snapshot.update {
        it.copy(
          state = DubbingSessionState.CONNECTING,
          metrics = it.metrics.copy(captureInitDelayMs = captureInitDelay),
        )
      }
    }

    val geminiConnectStart = clockMsProvider()
    val listener = createGeminiListener(gen)
    val connectResult = gemini.connectAndSetup(apiKey, targetLanguage.code, listener)

    if (sessionGeneration.get() != gen) {
      gemini.close("Session superseded")
      return
    }

    if (connectResult.isFailure) {
      val err =
        (connectResult.exceptionOrNull() as? DubbingException)?.error
          ?: DubbingError.GeminiConnectionFailure(connectResult.exceptionOrNull()?.message)
      stopSessionInternal(err)
      return
    }

    val geminiConnectDelay = (clockMsProvider() - geminiConnectStart).coerceAtLeast(0L)

    stateMutex.withLock {
      if (sessionGeneration.get() != gen) return
      capture.setStreamingEnabled(true)
      _snapshot.update {
        it.copy(
          state = DubbingSessionState.LISTENING,
          metrics = it.metrics.copy(geminiConnectDelayMs = geminiConnectDelay),
        )
      }
    }

    launchSessionCoroutines(gen, capture, playback, gemini)
  }

  private fun createGeminiListener(gen: Long): LiveTranslateClient.Listener =
    object : LiveTranslateClient.Listener {
      override fun onAudioReceived(
        pcm24kChunks: List<ByteArray>,
        turnComplete: Boolean,
        interrupted: Boolean,
      ) {
        if (sessionGeneration.get() != gen) return
        val currentState = _snapshot.value.state
        if (currentState == DubbingSessionState.PAUSED ||
          currentState == DubbingSessionState.STOPPING ||
          currentState == DubbingSessionState.IDLE ||
          currentState == DubbingSessionState.ERROR
        ) {
          return
        }

        val playback = activePlayback ?: return
        if (interrupted) {
          playback.flushQueue()
        }

        var totalBytes = 0L
        for (chunk in pcm24kChunks) {
          if (chunk.isNotEmpty()) {
            playback.enqueueAudio(chunk)
            totalBytes += chunk.size
          }
        }

        if (totalBytes > 0L) {
          if (!firstTranslationReceived && firstAudioSentTimeMs > 0L) {
            firstTranslationReceived = true
            val latency = (clockMsProvider() - firstAudioSentTimeMs).coerceAtLeast(0L)
            _snapshot.update {
              it.copy(
                state = DubbingSessionState.PLAYING,
                metrics =
                  it.metrics.copy(
                    firstTranslationDelayMs = latency,
                    receivedAudioBytes = it.metrics.receivedAudioBytes + totalBytes,
                  ),
              )
            }
          } else {
            _snapshot.update {
              it.copy(
                state = DubbingSessionState.PLAYING,
                metrics =
                  it.metrics.copy(
                    receivedAudioBytes = it.metrics.receivedAudioBytes + totalBytes
                  ),
              )
            }
          }
        } else if (turnComplete &&
          (_snapshot.value.state == DubbingSessionState.PLAYING ||
            _snapshot.value.state == DubbingSessionState.TRANSLATING)
        ) {
          if (!playback.isPlaying.value) {
            _snapshot.update { it.copy(state = DubbingSessionState.LISTENING) }
          }
        }
      }

      override fun onError(error: DubbingError) {
        scope.launch {
          if (sessionGeneration.get() != gen) return@launch
          handleGeminiFailure(gen, error)
        }
      }

      override fun onClosedUnexpectedly(code: Int, reason: String) {
        scope.launch {
          if (sessionGeneration.get() != gen) return@launch
          handleGeminiFailure(gen, DubbingError.WebSocketClosedUnexpectedly(reason))
        }
      }
    }

  private fun launchSessionCoroutines(
    gen: Long,
    capture: AudioCaptureSource,
    playback: AudioPlaybackSink,
    gemini: LiveTranslateClient,
  ) {
    streamingJob?.cancel()
    streamingJob =
      scope.launch(Dispatchers.IO) {
        capture.capturedChunks.collect { chunk ->
          if (!isActive || sessionGeneration.get() != gen) return@collect
          val st = _snapshot.value.state
          if (st == DubbingSessionState.PAUSED ||
            st == DubbingSessionState.RECONNECTING ||
            st == DubbingSessionState.STOPPING
          ) {
            return@collect
          }
          val client = activeGemini ?: gemini
          val sent = client.sendAudioChunk(chunk)
          if (sent) {
            val now = clockMsProvider()
            if (firstAudioSentTimeMs == 0L) {
              firstAudioSentTimeMs = now
            }
            val chunkAudible = AudioProcessor.hasAudibleEnergy(chunk)
            if (chunkAudible) {
              lastAudibleInputMs = now
            }
            _snapshot.update { snap ->
              val nextState =
                when {
                  snap.state == DubbingSessionState.LISTENING && chunkAudible ->
                    DubbingSessionState.TRANSLATING
                  snap.state == DubbingSessionState.TRANSLATING &&
                    !snap.isPlaybackActive &&
                    lastAudibleInputMs > 0L &&
                    (now - lastAudibleInputMs) > SILENT_RETURN_TO_LISTENING_MS ->
                    DubbingSessionState.LISTENING
                  else -> snap.state
                }
              snap.copy(
                state = nextState,
                metrics =
                  snap.metrics.copy(
                    sentAudioBytes = snap.metrics.sentAudioBytes + chunk.size
                  ),
              )
            }
          }
        }
      }

    metricsJob?.cancel()
    metricsJob =
      scope.launch {
        launch {
          capture.inputRms.collect { rms ->
            if (sessionGeneration.get() == gen) {
              _snapshot.update { it.copy(inputRmsLevel = rms) }
            }
          }
        }
        launch {
          capture.droppedChunks.collect { dropped ->
            if (sessionGeneration.get() == gen) {
              _snapshot.update {
                it.copy(metrics = it.metrics.copy(droppedInputChunks = dropped))
              }
            }
          }
        }
        launch {
          playback.outputRms.collect { outRms ->
            if (sessionGeneration.get() == gen) {
              _snapshot.update { it.copy(outputRmsLevel = outRms) }
            }
          }
        }
        launch {
          playback.isPlaying.collect { playing ->
            if (sessionGeneration.get() == gen) {
              _snapshot.update { snap ->
                val adjustedState =
                  if (!playing && snap.state == DubbingSessionState.PLAYING) {
                    DubbingSessionState.LISTENING
                  } else {
                    snap.state
                  }
                snap.copy(state = adjustedState, isPlaybackActive = playing)
              }
            }
          }
        }
        launch {
          playback.underrunCount.collect { underruns ->
            if (sessionGeneration.get() == gen) {
              _snapshot.update {
                it.copy(metrics = it.metrics.copy(playbackUnderruns = underruns))
              }
            }
          }
        }
        launch {
          playback.droppedChunks.collect { droppedOut ->
            if (sessionGeneration.get() == gen) {
              _snapshot.update {
                it.copy(metrics = it.metrics.copy(droppedPlaybackChunks = droppedOut))
              }
            }
          }
        }
        launch {
          capture.hasAudibleSignal.collect { audible ->
            if (sessionGeneration.get() == gen && audible) {
              _snapshot.update { it.copy(noAudioWarning = false) }
            }
          }
        }
      }

    timerJob?.cancel()
    timerJob =
      scope.launch {
        while (isActive && sessionGeneration.get() == gen) {
          delay(1_000L)
          if (sessionGeneration.get() != gen) break
          val state = _snapshot.value.state
          if (state.isActive && state != DubbingSessionState.PAUSED) {
            _snapshot.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1L) }
          }
        }
      }

    watchdogJob?.cancel()
    watchdogJob =
      scope.launch {
        delay(NO_AUDIO_WARNING_DELAY_MS)
        if (sessionGeneration.get() == gen &&
          _snapshot.value.state.isActive &&
          !capture.hasAudibleSignal.value
        ) {
          _snapshot.update { it.copy(noAudioWarning = true) }
        }
      }
  }

  private suspend fun handleGeminiFailure(gen: Long, error: DubbingError) {
    if (sessionGeneration.get() != gen || !_snapshot.value.state.isRunningOrStarting) return

    val isRetryable =
      error is DubbingError.GeminiConnectionFailure ||
        error is DubbingError.WebSocketClosedUnexpectedly

    if (!isRetryable) {
      stopSessionInternal(error)
      return
    }

    if (reconnectJob?.isActive == true) return

    reconnectJob =
      scope.launch {
        val apiKey = currentApiKey
        val targetLang = _snapshot.value.targetLanguage.code
        if (apiKey.isNullOrBlank()) {
          stopSessionInternal(DubbingError.InvalidApiKey())
          return@launch
        }

        for (attempt in 1..MAX_RECONNECT_ATTEMPTS) {
          if (sessionGeneration.get() != gen) return@launch
          _snapshot.update {
            it.copy(
              state = DubbingSessionState.RECONNECTING,
              reconnectAttempt = attempt,
              metrics = it.metrics.copy(reconnectAttempts = it.metrics.reconnectAttempts + 1),
            )
          }

          val backoffMs = INITIAL_RECONNECT_BACKOFF_MS * (1L shl (attempt - 1))
          delay(backoffMs)
          if (sessionGeneration.get() != gen) return@launch

          activeGemini?.close("Reconnecting attempt $attempt")
          val replacement = geminiClientFactory()
          activeGemini = replacement
          val result = replacement.connectAndSetup(apiKey, targetLang, createGeminiListener(gen))

          if (sessionGeneration.get() != gen) {
            replacement.close("Session ended during reconnect")
            return@launch
          }

          if (result.isSuccess) {
            _snapshot.update {
              it.copy(state = DubbingSessionState.LISTENING, reconnectAttempt = 0)
            }
            return@launch
          } else {
            val attemptErr = (result.exceptionOrNull() as? DubbingException)?.error
            if (attemptErr is DubbingError.InvalidApiKey ||
              attemptErr is DubbingError.QuotaExceeded ||
              attemptErr is DubbingError.ModelUnavailable
            ) {
              stopSessionInternal(attemptErr)
              return@launch
            }
          }
        }

        if (sessionGeneration.get() == gen) {
          stopSessionInternal(DubbingError.NetworkInterrupted)
        }
      }
  }

  suspend fun pauseSession() =
    stateMutex.withLock {
      val st = _snapshot.value.state
      if (st == DubbingSessionState.LISTENING ||
        st == DubbingSessionState.TRANSLATING ||
        st == DubbingSessionState.PLAYING
      ) {
        activeCapture?.setStreamingEnabled(false)
        activePlayback?.pauseAndFlush()
        _snapshot.update { it.copy(state = DubbingSessionState.PAUSED, isPlaybackActive = false) }
      }
    }

  suspend fun resumeSession() =
    stateMutex.withLock {
      if (_snapshot.value.state == DubbingSessionState.PAUSED) {
        activePlayback?.resume()
        activeCapture?.setStreamingEnabled(true)
        _snapshot.update { it.copy(state = DubbingSessionState.LISTENING) }
      }
    }

  fun setVolume(volume: Float) {
    currentVolume = volume.coerceIn(0f, 1.5f)
    activePlayback?.setVolume(currentVolume)
  }

  suspend fun stopSession() {
    stopSessionInternal(error = null)
  }

  suspend fun dismissError() =
    stateMutex.withLock {
      if (_snapshot.value.state == DubbingSessionState.ERROR) {
        _snapshot.update {
          it.copy(
            state = DubbingSessionState.IDLE,
            error = null,
            elapsedSeconds = 0L,
            inputRmsLevel = 0f,
            outputRmsLevel = 0f,
            isPlaybackActive = false,
            noAudioWarning = false,
            reconnectAttempt = 0,
          )
        }
      }
    }

  private suspend fun stopSessionInternal(error: DubbingError?) {
    val teardownCallback: (() -> Unit)?
    stateMutex.withLock {
      val current = _snapshot.value.state
      if (current == DubbingSessionState.IDLE && error == null) {
        return
      }
      if (current == DubbingSessionState.STOPPING) {
        return
      }

      sessionGeneration.incrementAndGet()
      _snapshot.update { it.copy(state = DubbingSessionState.STOPPING) }

      reconnectJob?.cancel()
      reconnectJob = null
      streamingJob?.cancel()
      streamingJob = null
      metricsJob?.cancel()
      metricsJob = null
      timerJob?.cancel()
      timerJob = null
      watchdogJob?.cancel()
      watchdogJob = null

      val capture = activeCapture
      activeCapture = null
      capture?.stopAndRelease()

      val gemini = activeGemini
      activeGemini = null
      gemini?.close("Session stopped")

      val playback = activePlayback
      activePlayback = null
      playback?.stopAndRelease()

      teardownCallback = serviceTeardownCallback
      serviceTeardownCallback = null
      currentApiKey = null

      _snapshot.update {
        it.copy(
          state = if (error != null) DubbingSessionState.ERROR else DubbingSessionState.IDLE,
          error = error,
          inputRmsLevel = 0f,
          outputRmsLevel = 0f,
          isPlaybackActive = false,
          noAudioWarning = false,
          reconnectAttempt = 0,
        )
      }
    }
    teardownCallback?.invoke()
  }

  companion object {
    const val MIN_ANDROID_API = 29
    const val MAX_RECONNECT_ATTEMPTS = 3
    const val INITIAL_RECONNECT_BACKOFF_MS = 1_000L
    const val NO_AUDIO_WARNING_DELAY_MS = 6_000L
    const val SILENT_RETURN_TO_LISTENING_MS = 1_800L
  }
}
