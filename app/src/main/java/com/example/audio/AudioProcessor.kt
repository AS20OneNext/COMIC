package com.example.audio

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

class InvalidPcmException(message: String) : IllegalArgumentException(message)

/**
 * Low-latency real-time PCM16 little-endian processor.
 * Adapts arbitrary capture sample rates and channel counts into Gemini Live's required
 * 16,000 Hz, 16-bit linear PCM, mono, little-endian stream emitted in ~100 ms (3,200-byte) chunks.
 */
object AudioProcessor {
  const val TARGET_SAMPLE_RATE_HZ = 16_000
  const val OUTPUT_SAMPLE_RATE_HZ = 24_000
  const val BYTES_PER_SAMPLE = 2
  const val CHUNK_DURATION_MS = 100
  const val TARGET_CHUNK_SAMPLES = (TARGET_SAMPLE_RATE_HZ * CHUNK_DURATION_MS) / 1_000 // 1,600
  const val TARGET_CHUNK_BYTES = TARGET_CHUNK_SAMPLES * BYTES_PER_SAMPLE // 3,200

  fun decodePcm16LeToShorts(bytes: ByteArray, length: Int = bytes.size): ShortArray {
    if (length < 0 || length > bytes.size || length % BYTES_PER_SAMPLE != 0) {
      throw InvalidPcmException("Invalid PCM16 byte length: $length")
    }
    val sampleCount = length / BYTES_PER_SAMPLE
    val out = ShortArray(sampleCount)
    decodePcm16LeInto(bytes, length, out)
    return out
  }

  fun decodePcm16LeInto(bytes: ByteArray, length: Int, destination: ShortArray): Int {
    if (length < 0 || length > bytes.size || length % BYTES_PER_SAMPLE != 0) {
      throw InvalidPcmException("Invalid PCM16 byte length: $length")
    }
    val sampleCount = length / BYTES_PER_SAMPLE
    for (i in 0 until sampleCount) {
      val low = bytes[i * 2].toInt() and 0xFF
      val high = bytes[i * 2 + 1].toInt()
      destination[i] = ((high shl 8) or low).toShort()
    }
    return sampleCount
  }

  fun encodeShortsToPcm16Le(samples: ShortArray, count: Int = samples.size): ByteArray {
    val out = ByteArray(count * BYTES_PER_SAMPLE)
    encodeShortsInto(samples, count, out)
    return out
  }

  fun encodeShortsInto(samples: ShortArray, count: Int, destination: ByteArray): Int {
    for (i in 0 until count) {
      val s = samples[i].toInt()
      destination[i * 2] = (s and 0xFF).toByte()
      destination[i * 2 + 1] = ((s ushr 8) and 0xFF).toByte()
    }
    return count * BYTES_PER_SAMPLE
  }

  fun downmixToMono(samples: ShortArray, channels: Int): ShortArray {
    if (channels <= 0) throw InvalidPcmException("Unsupported channel count: $channels")
    if (channels == 1) return samples
    if (samples.size % channels != 0) {
      throw InvalidPcmException(
        "Sample count ${samples.size} is not divisible by channel count $channels"
      )
    }
    val frames = samples.size / channels
    val mono = ShortArray(frames)
    downmixInto(samples, samples.size, channels, mono)
    return mono
  }

  fun downmixInto(
    samples: ShortArray,
    sampleCount: Int,
    channels: Int,
    destination: ShortArray,
  ): Int {
    if (channels <= 0) throw InvalidPcmException("Unsupported channel count: $channels")
    if (sampleCount % channels != 0) {
      throw InvalidPcmException(
        "Sample count $sampleCount is not divisible by channel count $channels"
      )
    }
    val frames = sampleCount / channels
    when (channels) {
      1 -> System.arraycopy(samples, 0, destination, 0, frames)
      2 -> {
        for (i in 0 until frames) {
          val left = samples[i * 2].toInt()
          val right = samples[i * 2 + 1].toInt()
          destination[i] = ((left + right) / 2).toShort()
        }
      }
      else -> {
        for (i in 0 until frames) {
          var sum = 0
          val base = i * channels
          for (c in 0 until channels) {
            sum += samples[base + c].toInt()
          }
          destination[i] = (sum / channels).toShort()
        }
      }
    }
    return frames
  }

  fun resampleLinear(
    monoSamples: ShortArray,
    inputSampleRateHz: Int,
    outputSampleRateHz: Int = TARGET_SAMPLE_RATE_HZ,
  ): ShortArray {
    if (inputSampleRateHz <= 0 || outputSampleRateHz <= 0) {
      throw InvalidPcmException(
        "Invalid sample rate: in=$inputSampleRateHz out=$outputSampleRateHz"
      )
    }
    if (monoSamples.isEmpty() || inputSampleRateHz == outputSampleRateHz) {
      return monoSamples
    }
    val ratio = inputSampleRateHz.toDouble() / outputSampleRateHz.toDouble()
    val outputLength = (monoSamples.size.toDouble() / ratio).toInt().coerceAtLeast(1)
    val output = ShortArray(outputLength)
    for (i in 0 until outputLength) {
      val srcPos = i * ratio
      val idx0 = srcPos.toInt().coerceIn(0, monoSamples.lastIndex)
      val idx1 = (idx0 + 1).coerceIn(0, monoSamples.lastIndex)
      val frac = srcPos - idx0
      val s0 = monoSamples[idx0].toDouble()
      val s1 = monoSamples[idx1].toDouble()
      val interpolated = s0 + (s1 - s0) * frac
      output[i] =
        interpolated
          .roundToInt()
          .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
          .toShort()
    }
    return output
  }

  fun computeNormalizedRms(pcm16Le: ByteArray, length: Int = pcm16Le.size): Float {
    if (length < 2) return 0f
    val sampleCount = length / 2
    var sumSquares = 0.0
    for (i in 0 until sampleCount) {
      val low = pcm16Le[i * 2].toInt() and 0xFF
      val high = pcm16Le[i * 2 + 1].toInt()
      val sample = ((high shl 8) or low).toShort().toDouble() / Short.MAX_VALUE.toDouble()
      sumSquares += sample * sample
    }
    val rms = sqrt(sumSquares / sampleCount)
    return (rms * 3.2).toFloat().coerceIn(0f, 1f)
  }

  fun hasAudibleEnergy(
    pcm16Le: ByteArray,
    length: Int = pcm16Le.size,
    thresholdAmplitude: Int = 120,
  ): Boolean {
    val sampleCount = length / 2
    for (i in 0 until sampleCount) {
      val low = pcm16Le[i * 2].toInt() and 0xFF
      val high = pcm16Le[i * 2 + 1].toInt()
      val sample = ((high shl 8) or low).toShort().toInt()
      if (abs(sample) >= thresholdAmplitude) return true
    }
    return false
  }

  fun applyPcm16Gain(pcm16Le: ByteArray, gain: Float): ByteArray {
    if (pcm16Le.isEmpty() || abs(gain - 1.0f) < 0.01f) return pcm16Le
    if (pcm16Le.size % 2 != 0) throw InvalidPcmException("Invalid PCM16 length: ${pcm16Le.size}")
    val out = ByteArray(pcm16Le.size)
    applyPcm16GainInto(pcm16Le, out, pcm16Le.size, gain)
    return out
  }

  fun applyPcm16GainInto(
    source: ByteArray,
    destination: ByteArray,
    length: Int,
    gain: Float,
  ) {
    if (length % 2 != 0) throw InvalidPcmException("Invalid PCM16 length: $length")
    val clampedGain = gain.coerceIn(0f, 2.0f)
    val sampleCount = length / 2
    for (i in 0 until sampleCount) {
      val low = source[i * 2].toInt() and 0xFF
      val high = source[i * 2 + 1].toInt()
      val sample = ((high shl 8) or low).toShort().toFloat()
      val scaled =
        (sample * clampedGain)
          .roundToInt()
          .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
      destination[i * 2] = (scaled and 0xFF).toByte()
      destination[i * 2 + 1] = ((scaled ushr 8) and 0xFF).toByte()
    }
  }
}

/**
 * Primitive, zero-boxing streaming adapter that converts incoming PCM16 frames at any supported
 * sample rate and channel layout into fixed 3,200-byte (100ms, 16kHz, mono, PCM16LE) chunks.
 */
class PcmStreamAdapter(
  private val inputSampleRateHz: Int,
  private val inputChannels: Int,
  private val targetSampleRateHz: Int = AudioProcessor.TARGET_SAMPLE_RATE_HZ,
  private val chunkBytes: Int = AudioProcessor.TARGET_CHUNK_BYTES,
) {
  init {
    if (inputSampleRateHz <= 0) {
      throw InvalidPcmException("Invalid input sample rate: $inputSampleRateHz")
    }
    if (inputChannels !in 1..8) {
      throw InvalidPcmException("Unsupported input channel count: $inputChannels")
    }
    if (chunkBytes <= 0 || chunkBytes % 2 != 0) {
      throw InvalidPcmException("Invalid chunk byte size: $chunkBytes")
    }
  }

  private val pendingBytes = ByteArray(chunkBytes)
  private var pendingSize = 0
  private var previousSample: Short = 0
  private var hasPreviousSample = false
  private var phaseOffset = 0.0

  // Reusable primitive scratch buffers to eliminate per-frame boxing and array allocations
  private var interleavedScratch = ShortArray(4096)
  private var monoScratch = ShortArray(2048)
  private var resampledScratch = ShortArray(2048)
  private var encodedScratch = ByteArray(4096)

  fun process(inputPcm16Le: ByteArray, length: Int = inputPcm16Le.size): List<ByteArray> {
    if (length == 0) return emptyList()
    val out = ArrayList<ByteArray>(4)
    process(inputPcm16Le, length) { chunk -> out.add(chunk) }
    return out
  }

  fun process(
    inputPcm16Le: ByteArray,
    length: Int = inputPcm16Le.size,
    onChunkReady: (ByteArray) -> Unit,
  ) {
    if (length == 0) return
    val frameBytes = AudioProcessor.BYTES_PER_SAMPLE * inputChannels
    if (length < 0 || length > inputPcm16Le.size || length % frameBytes != 0) {
      throw InvalidPcmException(
        "Unaligned PCM frame block: length=$length, frameBytes=$frameBytes"
      )
    }

    val totalSamples = length / AudioProcessor.BYTES_PER_SAMPLE
    if (interleavedScratch.size < totalSamples) {
      interleavedScratch = ShortArray(totalSamples)
    }
    AudioProcessor.decodePcm16LeInto(inputPcm16Le, length, interleavedScratch)

    val frameCount = totalSamples / inputChannels
    if (monoScratch.size < frameCount) {
      monoScratch = ShortArray(frameCount)
    }
    AudioProcessor.downmixInto(interleavedScratch, totalSamples, inputChannels, monoScratch)

    val resampledCount = resampleContinuousInto(monoScratch, frameCount)
    val encodedBytes = resampledCount * AudioProcessor.BYTES_PER_SAMPLE
    if (encodedScratch.size < encodedBytes) {
      encodedScratch = ByteArray(encodedBytes)
    }
    AudioProcessor.encodeShortsInto(resampledScratch, resampledCount, encodedScratch)

    var offset = 0
    while (offset < encodedBytes) {
      val spaceLeft = chunkBytes - pendingSize
      val toCopy = minOf(spaceLeft, encodedBytes - offset)
      System.arraycopy(encodedScratch, offset, pendingBytes, pendingSize, toCopy)
      pendingSize += toCopy
      offset += toCopy

      if (pendingSize == chunkBytes) {
        onChunkReady(pendingBytes.copyOf(chunkBytes))
        pendingSize = 0
      }
    }
  }

  fun reset() {
    pendingSize = 0
    previousSample = 0
    hasPreviousSample = false
    phaseOffset = 0.0
  }

  private fun resampleContinuousInto(mono: ShortArray, monoCount: Int): Int {
    if (monoCount == 0) return 0
    if (inputSampleRateHz == targetSampleRateHz) {
      if (resampledScratch.size < monoCount) {
        resampledScratch = ShortArray(monoCount)
      }
      System.arraycopy(mono, 0, resampledScratch, 0, monoCount)
      return monoCount
    }

    val step = inputSampleRateHz.toDouble() / targetSampleRateHz.toDouble()
    val maxExpected = ((monoCount.toDouble() - phaseOffset) / step).toInt().coerceAtLeast(0) + 4
    if (resampledScratch.size < maxExpected) {
      resampledScratch = ShortArray(maxExpected)
    }

    var outIdx = 0
    var pos = phaseOffset
    while (pos < monoCount.toDouble()) {
      val interpolated: Double =
        if (pos < 0.0) {
          val prev = if (hasPreviousSample) previousSample.toDouble() else mono[0].toDouble()
          val frac = pos + 1.0
          prev + (mono[0].toDouble() - prev) * frac
        } else {
          val idx0 = pos.toInt()
          val idx1 = idx0 + 1
          if (idx1 >= monoCount) {
            break
          }
          val frac = pos - idx0
          val s0 = mono[idx0].toDouble()
          val s1 = mono[idx1].toDouble()
          s0 + (s1 - s0) * frac
        }
      resampledScratch[outIdx++] =
        interpolated
          .roundToInt()
          .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
          .toShort()
      pos += step
    }

    phaseOffset = pos - monoCount.toDouble()
    previousSample = mono[monoCount - 1]
    hasPreviousSample = true
    return outIdx
  }
}
