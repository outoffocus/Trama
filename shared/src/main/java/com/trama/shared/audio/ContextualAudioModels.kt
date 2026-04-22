package com.trama.shared.audio

data class ContextualCaptureConfig(
    val preRollSeconds: Int,
    val postRollSeconds: Int,
    val sampleRateHz: Int = 16_000,
    val silenceStopMs: Long = 1_500L,
    val gateEvalWindowsMs: List<Long> = listOf(0L, 15_000L, 12_000L, 8_000L, 5_000L, 3_000L),
    val maxCaptureSeconds: Int = 180
)

data class CapturedAudioWindow(
    val preRollPcm: ShortArray,
    val livePcm: ShortArray,
    val sampleRateHz: Int
) {
    fun mergedPcm(): ShortArray {
        if (preRollPcm.isEmpty()) return livePcm
        if (livePcm.isEmpty()) return preRollPcm
        val merged = ShortArray(preRollPcm.size + livePcm.size)
        preRollPcm.copyInto(merged, 0)
        livePcm.copyInto(merged, preRollPcm.size)
        return merged
    }

    fun durationMs(): Long {
        val totalSamples = preRollPcm.size + livePcm.size
        if (sampleRateHz <= 0 || totalSamples <= 0) return 0L
        return (totalSamples * 1000L) / sampleRateHz
    }

    fun tailWindow(maxDurationMs: Long): CapturedAudioWindow {
        if (sampleRateHz <= 0) return this
        val maxSamples = ((maxDurationMs * sampleRateHz) / 1000L).toInt()
        if (maxSamples <= 0) return this

        val merged = mergedPcm()
        if (merged.size <= maxSamples) return CapturedAudioWindow(
            preRollPcm = shortArrayOf(),
            livePcm = merged,
            sampleRateHz = sampleRateHz
        )

        val tail = merged.copyOfRange(merged.size - maxSamples, merged.size)
        return CapturedAudioWindow(
            preRollPcm = shortArrayOf(),
            livePcm = tail,
            sampleRateHz = sampleRateHz
        )
    }
}

data class AsrTranscript(
    val text: String,
    val confidence: Float? = null
)
