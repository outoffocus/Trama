package com.trama.app.audio

import com.trama.shared.audio.CapturedAudioWindow

/** Immutable hand-off from contextual capture to the final ASR pipeline. */
data class ContextualCaptureEnvelope(
    val captureId: Long,
    val window: CapturedAudioWindow,
    val source: String,
    val gateTranscript: String?,
    val triggerAtSample: Int?,
    val capturedSamples: Int
)
