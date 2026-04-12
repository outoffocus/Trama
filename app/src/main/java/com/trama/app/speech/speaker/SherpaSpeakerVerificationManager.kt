package com.trama.app.speech.speaker

import android.content.Context
import com.trama.shared.audio.CapturedAudioWindow
import kotlin.math.sqrt

class SherpaSpeakerVerificationManager(
    context: Context
) : SpeakerVerificationManager {
    companion object {
        private const val PREFS = "speaker_verification_v2"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_THRESHOLD = "threshold"
        private const val KEY_SAMPLES = "samples_csv"

        const val REQUIRED_SAMPLES = 3
        const val MAX_SAMPLES = 5
        const val DEFAULT_THRESHOLD = 0.72f
        private const val MIN_ENROLLMENT_DURATION_MS = 1_500L
        private const val MIN_VERIFY_DURATION_MS = 800L
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val engine = SherpaSpeakerEmbeddingEngine(appContext)

    val isBackendAvailable: Boolean
        get() = engine.isAvailable

    val sampleCount: Int
        get() = loadSamples().size

    val threshold: Float
        get() = prefs.getFloat(KEY_THRESHOLD, DEFAULT_THRESHOLD)

    val isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)

    override val isConfigured: Boolean
        get() = sampleCount >= REQUIRED_SAMPLES

    override val mode: SpeakerVerificationMode
        get() = if (isBackendAvailable) SpeakerVerificationMode.OFFLINE_EMBEDDING else SpeakerVerificationMode.DISABLED

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled && isConfigured && isBackendAvailable).apply()
    }

    fun setThreshold(value: Float) {
        prefs.edit().putFloat(KEY_THRESHOLD, value.coerceIn(0.4f, 0.95f)).apply()
    }

    override suspend fun enrollSample(window: CapturedAudioWindow): SpeakerEnrollmentStep {
        if (!isBackendAvailable) {
            return SpeakerEnrollmentStep.Rejected("Falta el modelo offline de reconocimiento de voz")
        }
        if (window.durationMs() < MIN_ENROLLMENT_DURATION_MS) {
            return SpeakerEnrollmentStep.Rejected("La muestra es demasiado corta")
        }

        val embedding = engine.embed(window)
            ?: return SpeakerEnrollmentStep.Rejected("No he podido extraer una huella de voz usable")

        val samples = loadSamples().toMutableList()
        samples += embedding.vector
        while (samples.size > MAX_SAMPLES) {
            samples.removeAt(0)
        }
        saveSamples(samples)

        val accepted = samples.size
        return if (accepted >= REQUIRED_SAMPLES) {
            prefs.edit().putBoolean(KEY_ENABLED, true).apply()
            SpeakerEnrollmentStep.EnrollmentReady(acceptedSamples = accepted)
        } else {
            SpeakerEnrollmentStep.SampleAccepted(
                acceptedSamples = accepted,
                remainingSamples = REQUIRED_SAMPLES - accepted
            )
        }
    }

    override suspend fun verify(window: CapturedAudioWindow): SpeakerVerificationResult {
        if (!isEnabled || !isConfigured) {
            return SpeakerVerificationResult(true, 1.0f, "disabled")
        }
        if (!isBackendAvailable) {
            return SpeakerVerificationResult(true, 1.0f, "backend_unavailable")
        }
        if (window.durationMs() < MIN_VERIFY_DURATION_MS) {
            return SpeakerVerificationResult(true, 0.0f, "too_short")
        }

        val current = engine.embed(window)
            ?: return SpeakerVerificationResult(true, 0.0f, "embedding_failed")

        val samples = loadSamples()
        if (samples.isEmpty()) {
            return SpeakerVerificationResult(true, 1.0f, "no_profile")
        }

        val profile = meanEmbedding(samples)
        val similarity = cosineSimilarity(profile, current.vector)
        val accepted = similarity >= threshold
        return SpeakerVerificationResult(
            accepted = accepted,
            similarity = similarity,
            reason = if (accepted) "accepted" else "below_threshold"
        )
    }

    override suspend fun reset() {
        prefs.edit()
            .remove(KEY_ENABLED)
            .remove(KEY_SAMPLES)
            .remove(KEY_THRESHOLD)
            .apply()
    }

    private fun loadSamples(): List<FloatArray> {
        val raw = prefs.getString(KEY_SAMPLES, null)?.trim().orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw
            .split("|")
            .mapNotNull { sample ->
                val values = sample
                    .split(",")
                    .mapNotNull { it.toFloatOrNull() }
                values.takeIf { it.isNotEmpty() }?.toFloatArray()
            }
    }

    private fun saveSamples(samples: List<FloatArray>) {
        val encoded = samples.joinToString("|") { sample ->
            sample.joinToString(",") { value -> value.toString() }
        }
        prefs.edit().putString(KEY_SAMPLES, encoded).apply()
    }

    private fun meanEmbedding(samples: List<FloatArray>): FloatArray {
        val dim = samples.firstOrNull()?.size ?: 0
        if (dim == 0) return FloatArray(0)
        val mean = FloatArray(dim)
        samples.forEach { sample ->
            for (i in 0 until minOf(dim, sample.size)) {
                mean[i] += sample[i]
            }
        }
        for (i in 0 until dim) {
            mean[i] /= samples.size.toFloat()
        }
        return mean
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        val size = minOf(a.size, b.size)
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in 0 until size) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA <= 0.0 || normB <= 0.0) return 0f
        return (dot / (sqrt(normA) * sqrt(normB))).toFloat()
    }
}
