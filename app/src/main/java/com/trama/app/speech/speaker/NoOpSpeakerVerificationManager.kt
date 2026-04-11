package com.trama.app.speech.speaker

import com.trama.app.audio.CapturedAudioWindow

/**
 * Explicit placeholder until a real embedding backend is integrated.
 *
 * Keeping this implementation separate makes it clear that the feature is currently
 * absent by design, not silently "working" via a weak heuristic.
 */
class NoOpSpeakerVerificationManager : SpeakerVerificationManager {
    override val isConfigured: Boolean = false
    override val mode: SpeakerVerificationMode = SpeakerVerificationMode.DISABLED

    override suspend fun enrollSample(window: CapturedAudioWindow): SpeakerEnrollmentStep =
        SpeakerEnrollmentStep.Rejected("Speaker verification not configured")

    override suspend fun verify(window: CapturedAudioWindow): SpeakerVerificationResult =
        SpeakerVerificationResult(
            accepted = true,
            similarity = 1.0f,
            reason = "disabled"
        )

    override suspend fun reset() = Unit
}
