package com.example.gemini

import com.example.model.SupportedTargetLanguages
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiLiveConfigTest {

  private val json = Json

  @Test
  fun `setup payload matches official Gemini 3_5 Live Translate schema without transcription`() {
    val raw = GeminiLiveConfig.buildSetupPayload("fa")
    val obj = json.parseToJsonElement(raw).jsonObject
    val setup = obj["setup"]!!.jsonObject

    assertEquals("models/gemini-3.5-live-translate-preview", setup["model"]!!.jsonPrimitive.content)
    assertFalse(setup.containsKey("inputAudioTranscription"))
    assertFalse(setup.containsKey("outputAudioTranscription"))

    val genConfig = setup["generationConfig"]!!.jsonObject
    val modalities = genConfig["responseModalities"]!!.jsonArray.map { it.jsonPrimitive.content }
    assertEquals(listOf("AUDIO"), modalities)

    val translationConfig = genConfig["translationConfig"]!!.jsonObject
    assertEquals("fa", translationConfig["targetLanguageCode"]!!.jsonPrimitive.content)
    assertFalse(translationConfig["echoTargetLanguage"]!!.jsonPrimitive.boolean)
  }

  @Test
  fun `realtime audio payload encodes PCM16 16kHz base64 chunk`() {
    val pcm = byteArrayOf(1, 2, 3, 4, 5, 6)
    val raw = GeminiLiveConfig.buildRealtimeAudioPayload(pcm)
    val obj = json.parseToJsonElement(raw).jsonObject
    val chunk = obj["realtimeInput"]!!.jsonObject["mediaChunks"]!!.jsonArray[0].jsonObject

    assertEquals("audio/pcm;rate=16000", chunk["mimeType"]!!.jsonPrimitive.content)
    val decoded = java.util.Base64.getDecoder().decode(chunk["data"]!!.jsonPrimitive.content)
    assertArrayEquals(pcm, decoded)
  }

  @Test
  fun `parseServerMessage extracts setupComplete and multiple inline audio parts`() {
    val setupAck = GeminiLiveConfig.parseServerMessage("""{"setupComplete":{}}""")
    assertTrue(setupAck is GeminiServerEvent.SetupComplete)

    val p1 = java.util.Base64.getEncoder().encodeToString(byteArrayOf(10, 20, 30, 40))
    val p2 = java.util.Base64.getEncoder().encodeToString(byteArrayOf(50, 60, 70, 80))
    val multiPartJson =
      """
      {
        "serverContent": {
          "modelTurn": {
            "parts": [
              {"inlineData": {"mimeType": "audio/pcm;rate=24000", "data": "$p1"}},
              {"inlineData": {"mimeType": "audio/pcm;rate=24000", "data": "$p2"}}
            ]
          },
          "turnComplete": true
        }
      }
      """.trimIndent()

    val parsed = GeminiLiveConfig.parseServerMessage(multiPartJson) as GeminiServerEvent.AudioContent
    assertEquals(2, parsed.audioChunks.size)
    assertArrayEquals(byteArrayOf(10, 20, 30, 40), parsed.audioChunks[0])
    assertArrayEquals(byteArrayOf(50, 60, 70, 80), parsed.audioChunks[1])
    assertTrue(parsed.turnComplete)
  }

  @Test
  fun `parseServerMessage parses server error and rejects malformed JSON`() {
    val errJson = """{"error":{"code":403,"status":"PERMISSION_DENIED","message":"API key invalid"}}"""
    val errEvent = GeminiLiveConfig.parseServerMessage(errJson) as GeminiServerEvent.ServerError
    assertEquals(403, errEvent.code)
    assertEquals("API key invalid", errEvent.message)

    val badEvent = GeminiLiveConfig.parseServerMessage("{not-valid-json")
    assertTrue(badEvent is GeminiServerEvent.InvalidPayload)
  }

  @Test
  fun `supported target languages defaults to Persian and includes curated 12`() {
    assertEquals("fa", SupportedTargetLanguages.DEFAULT.code)
    assertEquals(12, SupportedTargetLanguages.ALL.size)
    assertEquals("fa", SupportedTargetLanguages.findByCode("unknown").code)
    assertEquals("ja", SupportedTargetLanguages.findByCode("ja").code)
  }
}
