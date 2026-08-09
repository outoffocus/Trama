package com.trama.app.audio

/** Pure correlation rules used when an asynchronous gate result returns. */
object CaptureGateResultPolicy {
    fun belongsToActiveCapture(activeCaptureId: Long, resultCaptureId: Long): Boolean {
        return activeCaptureId == resultCaptureId
    }

    fun firstTriggerAtSample(
        currentValue: Int,
        matched: Boolean,
        coveredThroughSample: Int
    ): Int {
        if (currentValue >= 0 || !matched) return currentValue
        return coveredThroughSample.coerceAtLeast(0)
    }
}
