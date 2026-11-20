package com.example.dubbing

import android.media.projection.MediaProjection
import com.example.audio.AudioCaptureSource
import com.example.audio.AudioPlaybackSink
import com.example.data.ApiKeyAuthProvider
import com.example.gemini.DubbingException
import com.example.gemini.LiveTranslateClient
import com.example.model.AudioSourceMode
import com.example.model.DubbingError
import com.example.model.DubbingSessionState
import com.example.model.SupportedTargetLanguages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveDubbingManagerTest {

  private class FakeKeyProvider(var key: String? = "test-api-key") : ApiKeyAuthProvider {
    override suspend fun getCredential(): String? = key
  }

  private class FakeCaptureSource : AudioCaptureSource {
    val chunksFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)
    override val capturedChunks: Flow<ByteArray> = chunksFlow
    override val inputRms = MutableStateFlow(0f)
    override val droppedChunks = MutableStateFlow(0L)
    override val hasAudibleSignal = MutableStateFlow(true)

    var started = false
    var isStreamingActive = false
    var stoppedCount = 0
    var revokedCallback: (() -> Unit)? = null

    override fun start(
      mediaProjection: MediaProjection,
      sourceMode: AudioSourceMode,
      scope: CoroutineScope,
      onProjectionRevoked: () -> Unit,
      onCaptureError: (DubbingError) -> Unit,
    ): Boolean {
      started = true
      revokedCallback = onProjectionRevoked
      return true
    }

    override fun setStreamingEnabled(enabled: Boolean) {
      isStreamingActive = enabled
    }

    suspend fun emitChunk(bytes: ByteArray) {
      chunksFlow.subscriptionCount.first { it > 0 }
      chunksFlow.emit(bytes)
    }

    override fun stopAndRelease() {
      isStreamingActive = false
      stoppedCount++
    }
  }

  private class FakePlaybackSink : AudioPlaybackSink {
    override val outputRms = MutableStateFlow(0f)
    override val isPlaying = MutableStateFlow(false)
    override val underrunCount = MutableStateFlow(0)
    override val droppedChunks = MutableStateFlow(0L)

    val enqueuedChunks = mutableListOf<ByteArray>()
    var paused = false
    var stoppedCount = 0
    var lastVolume = 1.0f

    override fun start(
      scope: CoroutineScope,
      initialVolume: Float,
      onError: (DubbingError) -> Unit,
    ): Boolean {
      lastVolume = initialVolume
      return true
    }

    override fun enqueueAudio(pcm24kMonoLe: ByteArray) {
      enqueuedChunks.add(pcm24kMonoLe)
      isPlaying.value = true
    }

    override fun setVolume(volume: Float) {
      lastVolume = volume
    }

    override fun pauseAndFlush() {
      paused = true
      isPlaying.value = false
    }

    override fun resume() {
      paused = false
    }

    override fun flushQueue() = Unit

    override fun stopAndRelease() {
      stoppedCount++
      isPlaying.value = false
    }
  }

  private class FakeGeminiClient : LiveTranslateClient {
    var connectBehavior: () -> Result<Unit> = { Result.success(Unit) }
    var activeListener: LiveTranslateClient.Listener? = null
    val sentChunks = mutableListOf<ByteArray>()
    var closedCount = 0

    override suspend fun connectAndSetup(
      apiKey: String,
      targetLanguageCode: String,
      listener: LiveTranslateClient.Listener,
    ): Result<Unit> {
      activeListener = listener
      return connectBehavior()
    }

    override fun sendAudioChunk(pcm16Le16kHz: ByteArray): Boolean {
      sentChunks.add(pcm16Le16kHz)
      return true
    }

    override suspend fun testConnection(apiKey: String, targetLanguageCode: String): Result<Unit> =
      Result.success(Unit)

    override fun close(reason: String) {
      closedCount++
    }
  }

  private fun dummyProjection(): MediaProjection =
    org.robolectric.util.ReflectionHelpers.newInstance(MediaProjection::class.java)

  @Test
  fun `validation fails cleanly when API key is missing or specific app is invalid`() = runBlocking {
    val manager =
      LiveDubbingManager(
        apiKeyProvider = FakeKeyProvider(null),
        captureSourceFactory = { FakeCaptureSource() },
        playbackSinkFactory = { FakePlaybackSink() },
        geminiClientFactory = { FakeGeminiClient() },
      )

    val noKeyResult =
      manager.beginValidation(AudioSourceMode.AllEligible, SupportedTargetLanguages.DEFAULT, 1.0f)
    assertTrue(noKeyResult.isFailure)
    assertEquals(DubbingSessionState.ERROR, manager.snapshot.value.state)
    assertTrue(manager.snapshot.value.error is DubbingError.InvalidApiKey)

    manager.dismissError()
    assertEquals(DubbingSessionState.IDLE, manager.snapshot.value.state)

    val managerWithKey =
      LiveDubbingManager(
        apiKeyProvider = FakeKeyProvider("valid"),
        captureSourceFactory = { FakeCaptureSource() },
        playbackSinkFactory = { FakePlaybackSink() },
        geminiClientFactory = { FakeGeminiClient() },
      )
    val noAppResult =
      managerWithKey.beginValidation(
        AudioSourceMode.SpecificApp("", "", -1),
        SupportedTargetLanguages.DEFAULT,
        1.0f,
      )
    assertTrue(noAppResult.isFailure)
    assertTrue(managerWithKey.snapshot.value.error is DubbingError.NoAppSelected)
  }

  @Test
  fun `full 12-state lifecycle gates streaming, plays translation, pauses, resumes, and stops idempotently`() =
    runBlocking {
      val capture = FakeCaptureSource()
      val playback = FakePlaybackSink()
      val gemini = FakeGeminiClient()
      var serviceStopped = 0

      val manager =
        LiveDubbingManager(
          apiKeyProvider = FakeKeyProvider("secret-key"),
          captureSourceFactory = { capture },
          playbackSinkFactory = { playback },
          geminiClientFactory = { gemini },
        )

      assertTrue(
        manager
          .beginValidation(AudioSourceMode.AllEligible, SupportedTargetLanguages.DEFAULT, 0.9f)
          .isSuccess
      )
      assertEquals(DubbingSessionState.REQUESTING_PERMISSION, manager.snapshot.value.state)

      manager.onStartingCaptureService()
      assertEquals(DubbingSessionState.INITIALIZING_CAPTURE, manager.snapshot.value.state)

      manager.startPipelineWithProjection(dummyProjection()) { serviceStopped++ }

      assertEquals(DubbingSessionState.LISTENING, manager.snapshot.value.state)
      assertTrue(capture.started)
      assertTrue(capture.isStreamingActive)

      // Stream input PCM chunk
      capture.emitChunk(ByteArray(3200) { 1 })
      delay(80)
      assertEquals(1, gemini.sentChunks.size)
      assertEquals(DubbingSessionState.TRANSLATING, manager.snapshot.value.state)

      // Simulate Gemini audio response
      gemini.activeListener?.onAudioReceived(
        pcm24kChunks = listOf(ByteArray(4800) { 2 }),
        turnComplete = false,
        interrupted = false,
      )
      delay(50)
      assertEquals(1, playback.enqueuedChunks.size)
      assertEquals(DubbingSessionState.PLAYING, manager.snapshot.value.state)

      // Pause and resume
      manager.pauseSession()
      assertEquals(DubbingSessionState.PAUSED, manager.snapshot.value.state)
      assertTrue(playback.paused)
      assertFalse(capture.isStreamingActive)

      manager.resumeSession()
      assertEquals(DubbingSessionState.LISTENING, manager.snapshot.value.state)
      assertTrue(capture.isStreamingActive)

      // Stop idempotently twice
      manager.stopSession()
      manager.stopSession()

      assertEquals(DubbingSessionState.IDLE, manager.snapshot.value.state)
      assertEquals(1, capture.stoppedCount)
      assertEquals(1, gemini.closedCount)
      assertEquals(1, playback.stoppedCount)
      assertEquals(1, serviceStopped)
    }

  @Test
  fun `silent idle timeout transitions TRANSLATING back to LISTENING when playback is inactive`() =
    runBlocking {
      var fakeNowMs = 1_000L
      val capture = FakeCaptureSource()
      val playback = FakePlaybackSink()
      val gemini = FakeGeminiClient()

      val manager =
        LiveDubbingManager(
          apiKeyProvider = FakeKeyProvider("secret-key"),
          captureSourceFactory = { capture },
          playbackSinkFactory = { playback },
          geminiClientFactory = { gemini },
          clockMsProvider = { fakeNowMs },
        )

      manager.beginValidation(AudioSourceMode.AllEligible, SupportedTargetLanguages.DEFAULT, 1.0f)
      manager.onStartingCaptureService()
      manager.startPipelineWithProjection(dummyProjection()) {}

      // Audible chunk moves state into TRANSLATING
      capture.emitChunk(ByteArray(3200) { 40 })
      delay(60)
      assertEquals(DubbingSessionState.TRANSLATING, manager.snapshot.value.state)

      // Advance fake clock past SILENT_RETURN_TO_LISTENING_MS and send silent frames
      fakeNowMs += LiveDubbingManager.SILENT_RETURN_TO_LISTENING_MS + 250L
      capture.emitChunk(ByteArray(3200))
      delay(220)
      assertEquals(DubbingSessionState.LISTENING, manager.snapshot.value.state)

      manager.stopSession()
    }

  @Test
  fun `projection stop callback tears down resources and reports ProjectionStopped`() = runBlocking {
    val capture = FakeCaptureSource()
    val playback = FakePlaybackSink()
    val gemini = FakeGeminiClient()
    var serviceStopped = 0

    val manager =
      LiveDubbingManager(
        apiKeyProvider = FakeKeyProvider("secret-key"),
        captureSourceFactory = { capture },
        playbackSinkFactory = { playback },
        geminiClientFactory = { gemini },
      )

    manager.beginValidation(AudioSourceMode.AllEligible, SupportedTargetLanguages.DEFAULT, 1.0f)
    manager.onStartingCaptureService()
    manager.startPipelineWithProjection(dummyProjection()) { serviceStopped++ }

    capture.revokedCallback?.invoke()
    delay(80)

    assertEquals(DubbingSessionState.ERROR, manager.snapshot.value.state)
    assertTrue(manager.snapshot.value.error is DubbingError.ProjectionStopped)
    assertEquals(1, capture.stoppedCount)
    assertEquals(1, gemini.closedCount)
    assertEquals(1, playback.stoppedCount)
    assertEquals(1, serviceStopped)
  }

  @Test
  fun `non retryable model unavailable error fails without infinite retries`() = runBlocking {
    val capture = FakeCaptureSource()
    val playback = FakePlaybackSink()
    val gemini =
      FakeGeminiClient().apply {
        connectBehavior = { Result.failure(DubbingException(DubbingError.ModelUnavailable("404"))) }
      }
    var serviceStopped = 0

    val manager =
      LiveDubbingManager(
        apiKeyProvider = FakeKeyProvider("secret-key"),
        captureSourceFactory = { capture },
        playbackSinkFactory = { playback },
        geminiClientFactory = { gemini },
      )

    manager.beginValidation(AudioSourceMode.AllEligible, SupportedTargetLanguages.DEFAULT, 1.0f)
    manager.onStartingCaptureService()
    manager.startPipelineWithProjection(dummyProjection()) { serviceStopped++ }

    assertEquals(DubbingSessionState.ERROR, manager.snapshot.value.state)
    assertTrue(manager.snapshot.value.error is DubbingError.ModelUnavailable)
    assertEquals(1, serviceStopped)
  }
}
