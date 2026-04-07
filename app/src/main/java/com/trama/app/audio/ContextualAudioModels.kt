package com.trama.app.audio

data class ContextualCaptureConfig(
    val preRollSeconds: Int,
    val postRollSeconds: Int,
    val sampleRateHz: Int = 16_000,
    val silenceStopMs: Long = 1_500L
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
}

data class AsrTranscript(
    val text: String,
    val confidence: Float? = null
)
