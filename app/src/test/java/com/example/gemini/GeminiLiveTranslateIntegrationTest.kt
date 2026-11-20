package com.example.gemini

import com.example.model.DubbingError
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiLiveTranslateIntegrationTest {

  private val json = Json

  @Test
  fun `WebSocket handshake sends official Gemini 3_5 Live Translate setup and streams audio`() =
    runBlocking {
      val server = MockWebServer()
      server.start()
      try {
        val receivedSetupLatch = CountDownLatch(1)
        val receivedAudioLatch = CountDownLatch(1)
        var capturedSetupJson = ""
        var capturedAudioJson = ""

        server.enqueue(
          MockResponse()
            .withWebSocketUpgrade(
              object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                  if (text.contains("\"setup\"")) {
                    capturedSetupJson = text
                    receivedSetupLatch.countDown()
                    webSocket.send("""{"setupComplete":{}}""")
                  } else if (text.contains("\"realtimeInput\"")) {
                    capturedAudioJson = text
                    receivedAudioLatch.countDown()
                    val b64Part = Base64.getEncoder().encodeToString(byteArrayOf(7, 8, 9, 10))
                    val reply =
                      """
                      {
                        "serverContent": {
                          "modelTurn": {
                            "parts": [
                              {"inlineData": {"mimeType": "audio/pcm;rate=24000", "data": "$b64Part"}}
                            ]
                          },
                          "turnComplete": true
                        }
                      }
                      """.trimIndent()
                    // Send as binary WebSocket frame to test ByteString handling as well
                    webSocket.send(reply.encodeUtf8())
                  }
                }
              }
            )
        )

        val okHttp = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
        val client =
          OkHttpGeminiLiveTranslateClient(
            okHttpClient = okHttp,
            webSocketUrlProvider = { key ->
              server.url("/ws?key=$key").toString().replace("http://", "ws://")
            },
          )

        val outputAudioLatch = CountDownLatch(1)
        val receivedChunks = mutableListOf<ByteArray>()
        var turnCompleted = false

        val connectResult =
          client.connectAndSetup(
            apiKey = "test-key-xyz",
            targetLanguageCode = "fa",
            listener =
              object : LiveTranslateClient.Listener {
                override fun onAudioReceived(
                  pcm24kChunks: List<ByteArray>,
                  turnComplete: Boolean,
                  interrupted: Boolean,
                ) {
                  receivedChunks.addAll(pcm24kChunks)
                  turnCompleted = turnComplete
                  outputAudioLatch.countDown()
                }

                override fun onError(error: DubbingError) = Unit

                override fun onClosedUnexpectedly(code: Int, reason: String) = Unit
              },
          )

        assertTrue(connectResult.isSuccess)
        assertTrue(receivedSetupLatch.await(3, TimeUnit.SECONDS))

        val setupObj = json.parseToJsonElement(capturedSetupJson).jsonObject["setup"]!!.jsonObject
        assertEquals(
          "models/gemini-3.5-live-translate-preview",
          setupObj["model"]!!.jsonPrimitive.content,
        )
        assertFalse(setupObj.containsKey("inputAudioTranscription"))
        assertFalse(setupObj.containsKey("outputAudioTranscription"))
        val translationConfig =
          setupObj["generationConfig"]!!.jsonObject["translationConfig"]!!.jsonObject
        assertEquals("fa", translationConfig["targetLanguageCode"]!!.jsonPrimitive.content)
        assertFalse(translationConfig["echoTargetLanguage"]!!.jsonPrimitive.boolean)

        // Send 16kHz PCM input chunk
        val inputPcm = byteArrayOf(1, 2, 3, 4)
        assertTrue(client.sendAudioChunk(inputPcm))
        assertTrue(receivedAudioLatch.await(3, TimeUnit.SECONDS))
        assertTrue(outputAudioLatch.await(3, TimeUnit.SECONDS))

        val chunkObj =
          json.parseToJsonElement(capturedAudioJson)
            .jsonObject["realtimeInput"]!!
            .jsonObject["mediaChunks"]!!
            .jsonArray[0]
            .jsonObject
        assertEquals("audio/pcm;rate=16000", chunkObj["mimeType"]!!.jsonPrimitive.content)

        assertEquals(1, receivedChunks.size)
        assertArrayEquals(byteArrayOf(7, 8, 9, 10), receivedChunks[0])
        assertTrue(turnCompleted)

        client.close("Test complete")
      } finally {
        server.shutdown()
      }
    }

  @Test
  fun `HTTP 403 and 429 handshake failures map to InvalidApiKey and QuotaExceeded`() =
    runBlocking {
      val server = MockWebServer()
      server.start()
      try {
        server.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))
        server.enqueue(MockResponse().setResponseCode(429).setBody("Too Many Requests"))

        val okHttp = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
        val client =
          OkHttpGeminiLiveTranslateClient(
            okHttpClient = okHttp,
            webSocketUrlProvider = { key ->
              server.url("/ws?key=$key").toString().replace("http://", "ws://")
            },
          )

        val noop =
          object : LiveTranslateClient.Listener {
            override fun onAudioReceived(
              pcm24kChunks: List<ByteArray>,
              turnComplete: Boolean,
              interrupted: Boolean,
            ) = Unit

            override fun onError(error: DubbingError) = Unit

            override fun onClosedUnexpectedly(code: Int, reason: String) = Unit
          }

        val forbidden = client.connectAndSetup("bad-key", "fa", noop)
        assertTrue(forbidden.isFailure)
        assertTrue((forbidden.exceptionOrNull() as DubbingException).error is DubbingError.InvalidApiKey)

        val quota = client.connectAndSetup("rate-key", "fa", noop)
        assertTrue(quota.isFailure)
        assertTrue((quota.exceptionOrNull() as DubbingException).error is DubbingError.QuotaExceeded)
      } finally {
        server.shutdown()
      }
    }
}
