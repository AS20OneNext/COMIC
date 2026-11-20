package com.example.gemini

import com.example.model.DubbingError
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

interface LiveTranslateClient {
  suspend fun connectAndSetup(
    apiKey: String,
    targetLanguageCode: String,
    listener: Listener,
  ): Result<Unit>

  fun sendAudioChunk(pcm16Le16kHz: ByteArray): Boolean

  suspend fun testConnection(apiKey: String, targetLanguageCode: String): Result<Unit>

  fun close(reason: String = "Session ended")

  interface Listener {
    fun onAudioReceived(pcm24kChunks: List<ByteArray>, turnComplete: Boolean, interrupted: Boolean)
    fun onError(error: DubbingError)
    fun onClosedUnexpectedly(code: Int, reason: String)
  }
}

class OkHttpGeminiLiveTranslateClient(
  private val okHttpClient: OkHttpClient = defaultOkHttpClient(),
  private val webSocketUrlProvider: (String) -> String = GeminiLiveConfig::buildWebSocketUrl,
) : LiveTranslateClient {

  @Volatile private var activeSocket: WebSocket? = null
  @Volatile private var activeListener: LiveTranslateClient.Listener? = null
  private val closedByClient = AtomicBoolean(false)
  private val setupCompleteFlag = AtomicBoolean(false)

  override suspend fun connectAndSetup(
    apiKey: String,
    targetLanguageCode: String,
    listener: LiveTranslateClient.Listener,
  ): Result<Unit> {
    close("Replacing existing socket")
    closedByClient.set(false)
    setupCompleteFlag.set(false)
    activeListener = listener

    val trimmedKey = apiKey.trim()
    if (trimmedKey.isEmpty()) {
      return Result.failure(DubbingException(DubbingError.InvalidApiKey()))
    }

    val setupDeferred = CompletableDeferred<Result<Unit>>()
    val request =
      Request.Builder()
        .url(webSocketUrlProvider(trimmedKey))
        .build()

    val socket =
      okHttpClient.newWebSocket(
        request,
        object : WebSocketListener() {
          override fun onOpen(webSocket: WebSocket, response: Response) {
            val setupMessage = GeminiLiveConfig.buildSetupPayload(targetLanguageCode)
            val sent = webSocket.send(setupMessage)
            if (!sent && !setupDeferred.isCompleted) {
              setupDeferred.complete(
                Result.failure(DubbingException(DubbingError.GeminiConnectionFailure("Failed to send setup frame")))
              )
            }
          }

          override fun onMessage(webSocket: WebSocket, text: String) {
            handleIncomingJson(text, setupDeferred)
          }

          override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            handleIncomingJson(bytes.utf8(), setupDeferred)
          }

          override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
          }

          override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (activeSocket === webSocket) {
              activeSocket = null
            }
            if (!setupDeferred.isCompleted) {
              val mapped = mapCloseToError(code, reason)
              setupDeferred.complete(Result.failure(DubbingException(mapped)))
            } else if (!closedByClient.get()) {
              activeListener?.onClosedUnexpectedly(code, reason.ifBlank { "code $code" })
            }
          }

          override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (activeSocket === webSocket) {
              activeSocket = null
            }
            val mapped = mapFailureToError(t, response)
            if (!setupDeferred.isCompleted) {
              setupDeferred.complete(Result.failure(DubbingException(mapped)))
            } else if (!closedByClient.get()) {
              activeListener?.onError(mapped)
            }
          }
        },
      )

    activeSocket = socket

    return try {
      withTimeout(SETUP_TIMEOUT_MS) { setupDeferred.await() }
    } catch (t: Throwable) {
      close("Setup timeout")
      Result.failure(
        DubbingException(DubbingError.GeminiConnectionFailure(t.message ?: "Connection timed out"))
      )
    }
  }

  private fun handleIncomingJson(
    rawJson: String,
    setupDeferred: CompletableDeferred<Result<Unit>>,
  ) {
    when (val event = GeminiLiveConfig.parseServerMessage(rawJson)) {
      GeminiServerEvent.SetupComplete -> {
        setupCompleteFlag.set(true)
        if (!setupDeferred.isCompleted) {
          setupDeferred.complete(Result.success(Unit))
        }
      }
      is GeminiServerEvent.AudioContent -> {
        if (event.audioChunks.isNotEmpty() || event.interrupted || event.turnComplete) {
          activeListener?.onAudioReceived(
            pcm24kChunks = event.audioChunks,
            turnComplete = event.turnComplete,
            interrupted = event.interrupted,
          )
        }
      }
      is GeminiServerEvent.ServerError -> {
        val mapped = mapServerError(event)
        if (!setupDeferred.isCompleted) {
          setupDeferred.complete(Result.failure(DubbingException(mapped)))
        } else {
          activeListener?.onError(mapped)
        }
      }
      is GeminiServerEvent.InvalidPayload -> {
        val err = DubbingError.InvalidPcmInput(event.reason)
        if (!setupDeferred.isCompleted) {
          setupDeferred.complete(Result.failure(DubbingException(err)))
        } else {
          activeListener?.onError(err)
        }
      }
      GeminiServerEvent.Ignored -> Unit
    }
  }

  override fun sendAudioChunk(pcm16Le16kHz: ByteArray): Boolean {
    val socket = activeSocket ?: return false
    if (!setupCompleteFlag.get() || closedByClient.get()) return false
    val frame = GeminiLiveConfig.buildRealtimeAudioPayload(pcm16Le16kHz)
    return socket.send(frame)
  }

  override suspend fun testConnection(
    apiKey: String,
    targetLanguageCode: String,
  ): Result<Unit> {
    val noopListener =
      object : LiveTranslateClient.Listener {
        override fun onAudioReceived(
          pcm24kChunks: List<ByteArray>,
          turnComplete: Boolean,
          interrupted: Boolean,
        ) = Unit

        override fun onError(error: DubbingError) = Unit

        override fun onClosedUnexpectedly(code: Int, reason: String) = Unit
      }
    val result = connectAndSetup(apiKey, targetLanguageCode, noopListener)
    close("Connection test finished")
    return result
  }

  override fun close(reason: String) {
    closedByClient.set(true)
    setupCompleteFlag.set(false)
    activeListener = null
    val socket = activeSocket
    activeSocket = null
    if (socket != null) {
      runCatching { socket.close(1000, reason.take(120)) }
      runCatching { socket.cancel() }
    }
  }

  private fun mapFailureToError(t: Throwable, response: Response?): DubbingError {
    val httpCode = response?.code
    val detail = response?.message ?: t.message
    return when (httpCode) {
      401, 403 -> DubbingError.InvalidApiKey(detail)
      404 -> DubbingError.ModelUnavailable(detail)
      429 -> DubbingError.QuotaExceeded(detail)
      else -> DubbingError.GeminiConnectionFailure(detail)
    }
  }

  private fun mapCloseToError(code: Int, reason: String): DubbingError {
    val lower = reason.lowercase()
    return when {
      lower.contains("api key") ||
        lower.contains("unauthorized") ||
        lower.contains("permission") ||
        code == 1008 && lower.contains("key") -> DubbingError.InvalidApiKey(reason)
      lower.contains("quota") || lower.contains("resource_exhausted") || lower.contains("rate") ->
        DubbingError.QuotaExceeded(reason)
      lower.contains("not found") || lower.contains("model") || lower.contains("unsupported") ->
        DubbingError.ModelUnavailable(reason)
      else -> DubbingError.WebSocketClosedUnexpectedly(reason.ifBlank { "code $code" })
    }
  }

  private fun mapServerError(event: GeminiServerEvent.ServerError): DubbingError {
    val lower = "${event.status.orEmpty()} ${event.message}".lowercase()
    return when {
      event.code == 401 || event.code == 403 || lower.contains("api key") || lower.contains("unauthenticated") ->
        DubbingError.InvalidApiKey(event.message)
      event.code == 429 || lower.contains("quota") || lower.contains("resource_exhausted") ->
        DubbingError.QuotaExceeded(event.message)
      event.code == 404 || lower.contains("model") || lower.contains("not found") ->
        DubbingError.ModelUnavailable(event.message)
      else -> DubbingError.GeminiConnectionFailure(event.message)
    }
  }

  companion object {
    private const val SETUP_TIMEOUT_MS = 15_000L

    fun defaultOkHttpClient(): OkHttpClient =
      OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Required for persistent WebSockets
        .writeTimeout(12, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
  }
}

class DubbingException(val error: DubbingError) : Exception(error.detail ?: error.toString())
