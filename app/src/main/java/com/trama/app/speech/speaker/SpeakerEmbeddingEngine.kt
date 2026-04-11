package com.trama.app.speech.speaker

import com.trama.app.audio.CapturedAudioWindow

/**
 * Real speaker verification must operate on audio embeddings, not on RMS heuristics.
 *
 * This interface is intentionally model-agnostic so we can plug an on-device backend
 * later (for example ECAPA/TitaNet-style embeddings) without coupling the rest of
 * the app to a specific runtime.
 */
interface SpeakerEmbeddingEngine {
    val name: String
    val isAvailable: Boolean

    suspend fun embed(window: CapturedAudioWindow): SpeakerEmbedding?
}

data class SpeakerEmbedding(
    val vector: FloatArray,
    val sampleRateHz: Int,
    val durationMs: Long
)

data class SpeakerVerificationResult(
    val accepted: Boolean,
    val similarity: Float,
    val reason: String
)
