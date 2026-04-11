package com.trama.app.speech.speaker

import com.trama.app.audio.CapturedAudioWindow

/**
 * High-level contract for a future "solo mi voz" feature.
 *
 * Design goals:
 * - verify after the reminder has already been transcribed by Whisper
 * - avoid early false negatives in the live gate
 * - keep enrollment/profile storage independent from the embedding model
 */
interface SpeakerVerificationManager {
    val isConfigured: Boolean
    val mode: SpeakerVerificationMode

    suspend fun enrollSample(window: CapturedAudioWindow): SpeakerEnrollmentStep

    suspend fun verify(window: CapturedAudioWindow): SpeakerVerificationResult

    suspend fun reset()
}

enum class SpeakerVerificationMode {
    DISABLED,
    OFFLINE_EMBEDDING
}

sealed interface SpeakerEnrollmentStep {
    data class SampleAccepted(
        val acceptedSamples: Int,
        val remainingSamples: Int
    ) : SpeakerEnrollmentStep

    data class EnrollmentReady(
        val acceptedSamples: Int
    ) : SpeakerEnrollmentStep

    data class Rejected(
        val reason: String
    ) : SpeakerEnrollmentStep
}
