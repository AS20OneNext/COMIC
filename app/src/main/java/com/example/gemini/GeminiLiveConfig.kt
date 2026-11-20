package com.example.gemini

import com.example.audio.AudioProcessor
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object GeminiLiveConfig {
  const val API_VERSION = "v1beta"
  const val MODEL_ID = "gemini-3.5-live-translate-preview"
  const val MODEL_RESOURCE = "models/$MODEL_ID"
  const val WEBSOCKET_ENDPOINT =
    "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.$API_VERSION.GenerativeService.BidiGenerateContent"
  const val INPUT_AUDIO_MIME_TYPE = "audio/pcm;rate=${AudioProcessor.TARGET_SAMPLE_RATE_HZ}"
  const val OUTPUT_AUDIO_MIME_PREFIX = "audio/pcm"

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  fun buildWebSocketUrl(apiKey: String): String =
    "$WEBSOCKET_ENDPOINT?key=${apiKey.trim()}"

  /**
   * Constructs the official BidiGenerateContentSetup payload for gemini-3.5-live-translate-preview.
   * Strictly AUDIO modality only, with translationConfig and NO transcription fields.
   */
  fun buildSetupPayload(targetLanguageCode: String): String {
    val payload = buildJsonObject {
      put(
        "setup",
        buildJsonObject {
          put("model", MODEL_RESOURCE)
          put(
            "generationConfig",
            buildJsonObject {
              put("responseModalities", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("AUDIO")) })
              put(
                "translationConfig",
                buildJsonObject {
                  put("targetLanguageCode", targetLanguageCode)
                  put("echoTargetLanguage", false)
                },
              )
            },
          )
        },
      )
    }
    return payload.toString()
  }

  /**
   * Constructs a BidiGenerateContentRealtimeInput frame with 16kHz PCM16 mono base64 chunk.
   */
  fun buildRealtimeAudioPayload(pcm16Bytes: ByteArray): String {
    val base64Audio = Base64.getEncoder().encodeToString(pcm16Bytes)
    val payload = buildJsonObject {
      put(
        "realtimeInput",
        buildJsonObject {
          put(
            "mediaChunks",
            buildJsonArray {
              add(
                buildJsonObject {
                  put("mimeType", INPUT_AUDIO_MIME_TYPE)
                  put("data", base64Audio)
                }
              )
            },
          )
        },
      )
    }
    return payload.toString()
  }

  fun parseServerMessage(rawJson: String): GeminiServerEvent {
    val root =
      try {
        json.parseToJsonElement(rawJson).jsonObject
      } catch (t: Throwable) {
        return GeminiServerEvent.InvalidPayload(t.message ?: "Malformed JSON")
      }

    // 1. Check explicit API error block
    val errorObj = root["error"] as? JsonObject
    if (errorObj != null) {
      val code = errorObj["code"]?.jsonPrimitive?.intOrNull
      val status = errorObj["status"]?.jsonPrimitive?.content
      val message = errorObj["message"]?.jsonPrimitive?.content ?: "Gemini Live API error"
      return GeminiServerEvent.ServerError(code = code, status = status, message = message)
    }

    // 2. Check setupComplete acknowledgment
    if (root.containsKey("setupComplete")) {
      return GeminiServerEvent.SetupComplete
    }

    // 3. Parse serverContent (may contain multiple audio parts in modelTurn.parts)
    val serverContent = root["serverContent"] as? JsonObject
    if (serverContent != null) {
      val interrupted = serverContent["interrupted"]?.jsonPrimitive?.booleanOrNull == true
      val turnComplete = serverContent["turnComplete"]?.jsonPrimitive?.booleanOrNull == true
      val audioChunks = ArrayList<ByteArray>()

      val modelTurn = serverContent["modelTurn"] as? JsonObject
      val parts = modelTurn?.get("parts") as? JsonArray
      if (parts != null) {
        for (element in parts) {
          val partObj = element as? JsonObject ?: continue
          val inlineData = partObj["inlineData"] as? JsonObject ?: continue
          val mimeType = inlineData["mimeType"]?.jsonPrimitive?.content ?: continue
          if (!mimeType.startsWith(OUTPUT_AUDIO_MIME_PREFIX, ignoreCase = true)) continue
          val base64Data = inlineData["data"]?.jsonPrimitive?.content ?: continue
          if (base64Data.isNotEmpty()) {
            val decoded =
              try {
                Base64.getDecoder().decode(base64Data)
              } catch (e: IllegalArgumentException) {
                return GeminiServerEvent.InvalidPayload("Invalid base64 audio part: ${e.message}")
              }
            if (decoded.isNotEmpty()) {
              audioChunks.add(decoded)
            }
          }
        }
      }

      if (audioChunks.isNotEmpty() || turnComplete || interrupted) {
        return GeminiServerEvent.AudioContent(
          audioChunks = audioChunks,
          turnComplete = turnComplete,
          interrupted = interrupted,
        )
      }
    }

    return GeminiServerEvent.Ignored
  }
}

sealed interface GeminiServerEvent {
  data object SetupComplete : GeminiServerEvent

  data class AudioContent(
    val audioChunks: List<ByteArray>,
    val turnComplete: Boolean,
    val interrupted: Boolean,
  ) : GeminiServerEvent

  data class ServerError(
    val code: Int?,
    val status: String?,
    val message: String,
  ) : GeminiServerEvent

  data class InvalidPayload(val reason: String) : GeminiServerEvent

  data object Ignored : GeminiServerEvent
}
