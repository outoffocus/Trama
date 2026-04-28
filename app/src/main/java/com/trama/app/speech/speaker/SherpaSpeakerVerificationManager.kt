package com.trama.app.speech.speaker

import android.content.Context
import android.content.SharedPreferences
import com.trama.shared.audio.CapturedAudioWindow
import kotlin.math.abs
import kotlin.math.log10
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
        const val DEFAULT_THRESHOLD = 0.60f
        private const val MIN_ENROLLMENT_DURATION_MS = 1_500L
        private const val MIN_VERIFY_DURATION_MS = 800L

        private const val DIAGNOSTICS_CAPACITY = 30
        private const val CLIPPING_THRESHOLD_AMPLITUDE = 32_700
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val engine = SherpaSpeakerEmbeddingEngine(appContext)

    private val cacheLock = Any()

    @Volatile
    private var cachedSamples: List<FloatArray>? = null

    @Volatile
    private var cachedProfile: FloatArray? = null

    @Volatile
    private var cachedDispersion: ProfileDispersion? = null

    private val diagnosticsLock = Any()
    private val diagnostics: ArrayDeque<VerificationDiagnostic> = ArrayDeque(DIAGNOSTICS_CAPACITY)

    // Held as a field so SharedPreferences keeps a strong reference to the listener.
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == KEY_SAMPLES) {
            invalidateSampleCache()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    val isBackendAvailable: Boolean
        get() = engine.isAvailable

    val sampleCount: Int
        get() = cachedSamplesOrLoad().size

    val threshold: Float
        get() = if (prefs.contains(KEY_THRESHOLD)) {
            prefs.getFloat(KEY_THRESHOLD, DEFAULT_THRESHOLD)
        } else {
            defaultThresholdForSampleCount(sampleCount)
        }

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

        val samples = cachedSamplesOrLoad().toMutableList()
        samples += embedding.vector
        while (samples.size > MAX_SAMPLES) {
            samples.removeAt(0)
        }
        saveSamples(samples)
        invalidateSampleCache()

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
        val durationMs = window.durationMs()
        val audioSignal = pcmSignalStats(window)

        if (!isEnabled || !isConfigured) {
            return SpeakerVerificationResult(true, 1.0f, "disabled")
        }
        if (!isBackendAvailable) {
            return SpeakerVerificationResult(true, 1.0f, "backend_unavailable")
        }
        if (durationMs < MIN_VERIFY_DURATION_MS) {
            recordDiagnostic(
                durationMs = durationMs,
                profileSimilarity = Float.NaN,
                bestSampleSimilarity = Float.NaN,
                similarity = 0f,
                threshold = threshold,
                accepted = true,
                reason = "too_short",
                signal = audioSignal
            )
            return SpeakerVerificationResult(true, 0.0f, "too_short")
        }

        val current = engine.embed(window)
            ?: run {
                recordDiagnostic(
                    durationMs = durationMs,
                    profileSimilarity = Float.NaN,
                    bestSampleSimilarity = Float.NaN,
                    similarity = 0f,
                    threshold = threshold,
                    accepted = true,
                    reason = "embedding_failed",
                    signal = audioSignal
                )
                return SpeakerVerificationResult(true, 0.0f, "embedding_failed")
            }

        val samples = cachedSamplesOrLoad()
        if (samples.isEmpty()) {
            recordDiagnostic(
                durationMs = durationMs,
                profileSimilarity = Float.NaN,
                bestSampleSimilarity = Float.NaN,
                similarity = 1f,
                threshold = threshold,
                accepted = true,
                reason = "no_profile",
                signal = audioSignal
            )
            return SpeakerVerificationResult(true, 1.0f, "no_profile")
        }

        val profile = cachedProfileOrCompute(samples)
        val profileSimilarity = cosineSimilarity(profile, current.vector)
        val bestSampleSimilarity = samples.maxOfOrNull { sample ->
            cosineSimilarity(sample, current.vector)
        } ?: profileSimilarity
        val similarity = maxOf(profileSimilarity, bestSampleSimilarity)
        val activeThreshold = threshold
        val accepted = similarity >= activeThreshold
        val reason = if (accepted) {
            if (bestSampleSimilarity > profileSimilarity) "accepted_best_sample" else "accepted_profile"
        } else {
            "below_threshold"
        }
        recordDiagnostic(
            durationMs = durationMs,
            profileSimilarity = profileSimilarity,
            bestSampleSimilarity = bestSampleSimilarity,
            similarity = similarity,
            threshold = activeThreshold,
            accepted = accepted,
            reason = reason,
            signal = audioSignal
        )
        return SpeakerVerificationResult(
            accepted = accepted,
            similarity = similarity,
            reason = reason
        )
    }

    override suspend fun reset() {
        prefs.edit()
            .remove(KEY_ENABLED)
            .remove(KEY_SAMPLES)
            .remove(KEY_THRESHOLD)
            .apply()
        invalidateSampleCache()
        synchronized(diagnosticsLock) { diagnostics.clear() }
    }

    /**
     * Snapshot of the most recent verifications kept entirely in RAM.
     * Read-only telemetry — never persisted.
     */
    fun recentDiagnostics(): List<VerificationDiagnostic> =
        synchronized(diagnosticsLock) { diagnostics.toList() }

    /**
     * Mean and standard deviation of pairwise cosine similarities among the enrolled
     * samples. Low std on a high mean means the enrolment is uniform; ideal profiles
     * typically show some spread because they cover several speaking conditions.
     * Returns null if there are fewer than two samples.
     */
    fun profileDispersion(): ProfileDispersion? {
        cachedDispersion?.let { return it }
        val samples = cachedSamplesOrLoad()
        if (samples.size < 2) return null
        var sum = 0.0
        var sumSq = 0.0
        var pairs = 0
        for (i in samples.indices) {
            for (j in i + 1 until samples.size) {
                val sim = cosineSimilarity(samples[i], samples[j]).toDouble()
                sum += sim
                sumSq += sim * sim
                pairs++
            }
        }
        if (pairs == 0) return null
        val mean = sum / pairs
        val variance = (sumSq / pairs - mean * mean).coerceAtLeast(0.0)
        val stdDev = sqrt(variance)
        val computed = ProfileDispersion(
            sampleCount = samples.size,
            pairCount = pairs,
            meanSimilarity = mean.toFloat(),
            stdSimilarity = stdDev.toFloat()
        )
        cachedDispersion = computed
        return computed
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

    private fun defaultThresholdForSampleCount(sampleCount: Int): Float {
        return when {
            sampleCount <= REQUIRED_SAMPLES -> 0.50f
            sampleCount == 4 -> 0.55f
            else -> DEFAULT_THRESHOLD
        }
    }

    private fun cachedSamplesOrLoad(): List<FloatArray> {
        cachedSamples?.let { return it }
        return synchronized(cacheLock) {
            cachedSamples ?: loadSamples().also { cachedSamples = it }
        }
    }

    private fun cachedProfileOrCompute(samples: List<FloatArray>): FloatArray {
        cachedProfile?.let { return it }
        return synchronized(cacheLock) {
            cachedProfile ?: meanEmbedding(samples).also { cachedProfile = it }
        }
    }

    private fun invalidateSampleCache() {
        synchronized(cacheLock) {
            cachedSamples = null
            cachedProfile = null
            cachedDispersion = null
        }
    }

    private fun pcmSignalStats(window: CapturedAudioWindow): SignalStats {
        val pcm = window.mergedPcm()
        val total = pcm.size
        if (total == 0) return SignalStats(0f, Float.NEGATIVE_INFINITY)
        var clipped = 0
        var sumSq = 0.0
        for (i in 0 until total) {
            val v = pcm[i].toInt()
            if (abs(v) >= CLIPPING_THRESHOLD_AMPLITUDE) clipped++
            sumSq += v.toDouble() * v.toDouble()
        }
        val rms = sqrt(sumSq / total)
        val rmsDbfs = if (rms > 0.0) {
            (20.0 * log10(rms / 32_768.0)).toFloat()
        } else {
            Float.NEGATIVE_INFINITY
        }
        return SignalStats(
            clippingFraction = clipped.toFloat() / total.toFloat(),
            rmsDbfs = rmsDbfs
        )
    }

    private fun recordDiagnostic(
        durationMs: Long,
        profileSimilarity: Float,
        bestSampleSimilarity: Float,
        similarity: Float,
        threshold: Float,
        accepted: Boolean,
        reason: String,
        signal: SignalStats
    ) {
        val entry = VerificationDiagnostic(
            timestampMs = System.currentTimeMillis(),
            durationMs = durationMs,
            similarity = similarity,
            profileSimilarity = profileSimilarity,
            bestSampleSimilarity = bestSampleSimilarity,
            threshold = threshold,
            accepted = accepted,
            reason = reason,
            clippingFraction = signal.clippingFraction,
            rmsDbfs = signal.rmsDbfs
        )
        synchronized(diagnosticsLock) {
            if (diagnostics.size >= DIAGNOSTICS_CAPACITY) {
                diagnostics.removeFirst()
            }
            diagnostics.addLast(entry)
        }
    }

    private data class SignalStats(
        val clippingFraction: Float,
        val rmsDbfs: Float
    )
}

/**
 * Read-only snapshot of one verification call. Kept in memory only.
 * NaN values for similarity fields mean the stage was skipped (e.g. too short).
 */
data class VerificationDiagnostic(
    val timestampMs: Long,
    val durationMs: Long,
    val similarity: Float,
    val profileSimilarity: Float,
    val bestSampleSimilarity: Float,
    val threshold: Float,
    val accepted: Boolean,
    val reason: String,
    val clippingFraction: Float,
    val rmsDbfs: Float
)

data class ProfileDispersion(
    val sampleCount: Int,
    val pairCount: Int,
    val meanSimilarity: Float,
    val stdSimilarity: Float
)
