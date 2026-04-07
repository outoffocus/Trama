package com.trama.app.audio

import kotlin.math.max

/**
 * Fixed-size ring buffer for mono PCM16 audio.
 * Keeps the latest audio in memory only; callers can snapshot the most recent window.
 */
class CircularAudioBuffer(
    sampleRateHz: Int,
    maxSeconds: Int
) {
    private val capacitySamples = max(1, sampleRateHz * max(1, maxSeconds))
    private val data = ShortArray(capacitySamples)
    private var writeIndex = 0
    private var size = 0

    @Synchronized
    fun append(chunk: ShortArray, length: Int = chunk.size) {
        val safeLength = length.coerceIn(0, chunk.size)
        for (i in 0 until safeLength) {
            data[writeIndex] = chunk[i]
            writeIndex = (writeIndex + 1) % capacitySamples
        }
        size = (size + safeLength).coerceAtMost(capacitySamples)
    }

    @Synchronized
    fun snapshotLast(seconds: Int, sampleRateHz: Int): ShortArray {
        val requestedSamples = (seconds.coerceAtLeast(0) * sampleRateHz).coerceAtMost(size)
        if (requestedSamples <= 0) return shortArrayOf()

        val result = ShortArray(requestedSamples)
        val start = (writeIndex - requestedSamples + capacitySamples) % capacitySamples
        for (i in 0 until requestedSamples) {
            result[i] = data[(start + i) % capacitySamples]
        }
        return result
    }

    @Synchronized
    fun clear() {
        writeIndex = 0
        size = 0
    }
}
