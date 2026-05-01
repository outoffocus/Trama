package com.trama.app.audio

internal object ContextualCapturePolicy {
    const val UNMATCHED_SEGMENT_CAP_MS = 12_000L

    fun shouldRotateUnmatchedSegment(
        triggerMatched: Boolean,
        capturedSamples: Int,
        sampleRateHz: Int,
        capMs: Long = UNMATCHED_SEGMENT_CAP_MS
    ): Boolean {
        if (triggerMatched || sampleRateHz <= 0 || capMs <= 0) return false
        val capSamples = ((capMs * sampleRateHz) / 1000L).toInt()
        return capturedSamples >= capSamples
    }
}
