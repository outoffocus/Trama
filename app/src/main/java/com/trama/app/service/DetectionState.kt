package com.trama.app.service

import com.trama.shared.speech.IntentDetector.DetectionResult

/**
 * Per-cycle recognition flags shared between the ASR callbacks and the
 * detection pipeline. Grouped into one holder so new cycles can reset the
 * whole set in a single call instead of five scattered assignments.
 */
class DetectionState {
    @Volatile var partialAlreadySaved: Boolean = false
    @Volatile var partialAlreadyVibrated: Boolean = false
    @Volatile var confirmedAlreadyVibrated: Boolean = false
    @Volatile var pendingPartialDetection: DetectionResult? = null
    @Volatile var pendingGateDetection: DetectionResult? = null

    /** Reset everything — call when a fresh recognition cycle begins. */
    fun resetAll() {
        partialAlreadySaved = false
        partialAlreadyVibrated = false
        confirmedAlreadyVibrated = false
        pendingPartialDetection = null
        pendingGateDetection = null
    }

    /** Reset everything except [partialAlreadySaved] — used when the gate re-arms mid-cycle. */
    fun resetForRearm() {
        partialAlreadyVibrated = false
        confirmedAlreadyVibrated = false
        pendingPartialDetection = null
        pendingGateDetection = null
    }

    /** Clear the two latched pending detections after they've been consumed. */
    fun clearPending() {
        pendingPartialDetection = null
        pendingGateDetection = null
    }
}
