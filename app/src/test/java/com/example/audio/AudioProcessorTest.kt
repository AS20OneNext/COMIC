package com.example.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioProcessorTest {

  @Test
  fun `downmix stereo to mono averages channels without overflow`() {
    val stereo = shortArrayOf(10_000, -10_000, 30_000, 30_000, -20_000, -10_000)
    val mono = AudioProcessor.downmixToMono(stereo, channels = 2)
    assertEquals(3, mono.size)
    assertEquals(0.toShort(), mono[0])
    assertEquals(30_000.toShort(), mono[1])
    assertEquals((-15_000).toShort(), mono[2])
  }

  @Test
  fun `resample 48kHz to 16kHz reduces sample count by factor of three`() {
    val input = ShortArray(4_800) { idx -> (idx % 1000).toShort() }
    val output = AudioProcessor.resampleLinear(input, inputSampleRateHz = 48_000, outputSampleRateHz = 16_000)
    assertEquals(1_600, output.size)
  }

  @Test
  fun `stream adapter emits exact 3200 byte 100ms chunks from 48kHz stereo PCM`() {
    val adapter =
      PcmStreamAdapter(
        inputSampleRateHz = 48_000,
        inputChannels = 2,
        targetSampleRateHz = 16_000,
        chunkBytes = 3_200,
      )
    // 250ms of 48kHz stereo = 12,000 frames = 24,000 shorts = 48,000 bytes
    val samples = ShortArray(24_000) { i -> ((i * 13) % 8000).toShort() }
    val inputBytes = AudioProcessor.encodeShortsToPcm16Le(samples)

    val chunks = adapter.process(inputBytes)
    assertEquals(2, chunks.size)
    chunks.forEach { chunk ->
      assertEquals(AudioProcessor.TARGET_CHUNK_BYTES, chunk.size)
    }
  }

  @Test
  fun `zero-box stream adapter preserves phase across consecutive 20ms buffers`() {
    val adapter =
      PcmStreamAdapter(
        inputSampleRateHz = 48_000,
        inputChannels = 2,
        targetSampleRateHz = 16_000,
        chunkBytes = 3_200,
      )
    // 20ms of 48kHz stereo = 960 frames = 1,920 shorts = 3,840 bytes.
    // 10 consecutive buffers = 200ms -> should emit exactly 2 x 100ms chunks.
    val frameSamples = ShortArray(1_920) { idx -> ((idx * 17) % 12_000).toShort() }
    val frameBytes = AudioProcessor.encodeShortsToPcm16Le(frameSamples)

    val emitted = mutableListOf<ByteArray>()
    repeat(10) {
      emitted += adapter.process(frameBytes)
    }
    assertEquals(2, emitted.size)
    emitted.forEach { chunk ->
      assertEquals(3_200, chunk.size)
    }
  }

  @Test(expected = InvalidPcmException::class)
  fun `decodePcm16Le rejects odd byte lengths`() {
    AudioProcessor.decodePcm16LeToShorts(byteArrayOf(1, 2, 3))
  }

  @Test(expected = InvalidPcmException::class)
  fun `stream adapter rejects unaligned frame lengths`() {
    val adapter = PcmStreamAdapter(inputSampleRateHz = 48_000, inputChannels = 2)
    // Stereo 16-bit frame is 4 bytes; 6 bytes is invalid
    adapter.process(ByteArray(6))
  }

  @Test
  fun `applyPcm16Gain and applyPcm16GainInto clamp saturation safely`() {
    val samples = shortArrayOf(25_000, -25_000)
    val bytes = AudioProcessor.encodeShortsToPcm16Le(samples)
    val boosted = AudioProcessor.applyPcm16Gain(bytes, 1.5f)
    val decoded = AudioProcessor.decodePcm16LeToShorts(boosted)
    assertEquals(Short.MAX_VALUE, decoded[0])
    assertEquals(Short.MIN_VALUE, decoded[1])

    val scratch = ByteArray(bytes.size)
    AudioProcessor.applyPcm16GainInto(bytes, scratch, bytes.size, 0.5f)
    val attenuated = AudioProcessor.decodePcm16LeToShorts(scratch)
    assertEquals(12_500.toShort(), attenuated[0])
    assertEquals((-12_500).toShort(), attenuated[1])
  }

  @Test
  fun `audible energy and normalized RMS detect signal vs silence`() {
    val silence = ByteArray(320)
    assertFalse(AudioProcessor.hasAudibleEnergy(silence))
    assertEquals(0f, AudioProcessor.computeNormalizedRms(silence), 0.0001f)

    val speech = AudioProcessor.encodeShortsToPcm16Le(ShortArray(160) { 3_000 })
    assertTrue(AudioProcessor.hasAudibleEnergy(speech))
    assertTrue(AudioProcessor.computeNormalizedRms(speech) > 0.1f)
  }
}
