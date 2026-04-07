package com.trama.app.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.trama.shared.speech.SpeakerProfile
import kotlin.math.abs

/**
 * Speaker enrollment and verification using SpeechRecognizer RMS energy profiles.
 *
 * Uses the same RMS values from SpeechRecognizer.onRmsChanged for both enrollment
 * and verification, ensuring consistent scale. This approach filters out TV/radio
 * (distant, lower/different RMS pattern) vs user's voice (close, louder, consistent).
 *
 * No ML model required — purely energy-based proximity heuristic.
 */
class SpeakerEnrollment(private val context: Context) {

    companion object {
        private const val TAG = "SpeakerEnrollment"
        private const val PREFS = "speaker_enrollment"
        private const val KEY_ENROLLED = "is_enrolled"
        private const val KEY_AVG_RMS = "avg_rms_db"
        private const val KEY_RMS_VARIANCE = "rms_variance_db"
        private const val KEY_ENROLLMENT_COUNT = "enrollment_count"
        private const val KEY_ENABLED = "verification_enabled"

        // How many seconds to record for each enrollment sample
        const val ENROLLMENT_DURATION_MS = 5000L

        // Enrollment phrases (user reads these)
        val ENROLLMENT_PHRASES = listOf(
            "Tengo que comprar leche y pan",
            "Debería llamar al médico mañana",
            "Recuérdame que pague la luz",
            "Necesito hablar con María",
            "Hay que preparar la presentación"
        )

        // How many phrases needed for enrollment
        const val REQUIRED_SAMPLES = 3

        // Similarity threshold — lower = more permissive
        private const val VERIFICATION_THRESHOLD = 0.45f

        // Minimum RMS dB to consider as speech (filter silence)
        private const val SPEECH_RMS_THRESHOLD = 1.0f
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun isEnrolled(): Boolean = prefs.getBoolean(KEY_ENROLLED, false)
    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getEnrollmentCount(): Int = prefs.getInt(KEY_ENROLLMENT_COUNT, 0)

    /**
     * Export enrolled voice profile as a serializable SpeakerProfile for sync to watch.
     * Returns null if not enrolled.
     */
    fun toSpeakerProfile(): SpeakerProfile? {
        if (!isEnrolled()) return null
        val features = loadFeatures() ?: return null
        return SpeakerProfile(
            avgRMS = features.avgRMS,
            rmsVariance = features.rmsVariance,
            enrollmentCount = getEnrollmentCount()
        )
    }

    /**
     * Record an enrollment sample using SpeechRecognizer's onRmsChanged.
     * This ensures enrollment and verification use the exact same RMS scale.
     * Must be called from the main thread.
     *
     * @param onResult callback with the enrollment result (called on main thread)
     */
    fun recordEnrollmentSample(onResult: (EnrollmentResult) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onResult(EnrollmentResult.Error("Reconocimiento de voz no disponible"))
            return
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val rmsValues = mutableListOf<Float>()
        val handler = Handler(Looper.getMainLooper())
        var finished = false

        fun finish() {
            if (finished) return
            finished = true

            try { recognizer.destroy() } catch (_: Exception) {}

            if (rmsValues.size < 10) {
                onResult(EnrollmentResult.Error("No se detectó suficiente voz. Habla más alto y durante más tiempo."))
                return
            }

            val rmsDoubles = rmsValues.map { it.toDouble() }
            val features = RmsFeatures(
                avgRMS = rmsDoubles.average(),
                rmsVariance = variance(rmsDoubles)
            )

            val count = prefs.getInt(KEY_ENROLLMENT_COUNT, 0)
            if (count == 0) {
                saveFeatures(features)
            } else {
                val existing = loadFeatures()
                if (existing != null) {
                    val merged = RmsFeatures(
                        avgRMS = (existing.avgRMS * count + features.avgRMS) / (count + 1),
                        rmsVariance = (existing.rmsVariance * count + features.rmsVariance) / (count + 1)
                    )
                    saveFeatures(merged)
                } else {
                    saveFeatures(features)
                }
            }

            val newCount = count + 1
            prefs.edit()
                .putInt(KEY_ENROLLMENT_COUNT, newCount)
                .putBoolean(KEY_ENROLLED, newCount >= REQUIRED_SAMPLES)
                .apply()

            Log.i(TAG, "Enrollment sample $newCount: avgRMS=${"%.2f".format(features.avgRMS)}dB, " +
                "var=${"%.2f".format(features.rmsVariance)}, frames=${rmsValues.size}")

            onResult(
                if (newCount >= REQUIRED_SAMPLES) EnrollmentResult.Complete(newCount)
                else EnrollmentResult.SampleRecorded(newCount, REQUIRED_SAMPLES)
            )
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.i(TAG, "Enrollment: ready, speak now")
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                if (rmsdB > SPEECH_RMS_THRESHOLD) rmsValues.add(rmsdB)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                Log.w(TAG, "Enrollment SpeechRecognizer error: $error")
                finish()
            }
            override fun onResults(results: Bundle?) { finish() }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, ENROLLMENT_DURATION_MS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        }

        recognizer.startListening(intent)

        // Safety timeout
        handler.postDelayed({
            try { recognizer.stopListening() } catch (_: Exception) {}
            finish()
        }, ENROLLMENT_DURATION_MS + 2000)
    }

    /**
     * Verify if audio matches the enrolled speaker using RMS energy profile.
     *
     * @param rmsValues RMS dB values from SpeechRecognizer.onRmsChanged
     * @return similarity score 0.0-1.0 (above VERIFICATION_THRESHOLD = match)
     */
    fun verify(rmsValues: List<Double>): VerificationResult {
        if (!isEnrolled() || !isEnabled()) {
            return VerificationResult(1.0f, true)
        }

        val enrolled = loadFeatures() ?: return VerificationResult(1.0f, true)

        // Filter to speech frames only
        val speechFrames = rmsValues.filter { it > SPEECH_RMS_THRESHOLD }

        if (speechFrames.size < 5) {
            Log.d(TAG, "Verification: not enough speech RMS samples (${speechFrames.size}), accepting")
            return VerificationResult(0.7f, true)
        }

        val currentAvgRMS = speechFrames.average()
        val currentRMSVar = variance(speechFrames)

        // Compare RMS features using normalized distance
        val rmsSim = 1.0 - minOf(
            abs(currentAvgRMS - enrolled.avgRMS) / maxOf(abs(enrolled.avgRMS), 1.0),
            1.0
        )
        val rmsVarSim = 1.0 - minOf(
            abs(currentRMSVar - enrolled.rmsVariance) / maxOf(enrolled.rmsVariance, 0.1),
            1.0
        )

        val similarity = (rmsSim * 0.65 + rmsVarSim * 0.35).toFloat()
        val isMatch = similarity >= VERIFICATION_THRESHOLD

        Log.d(TAG, "Verification: sim=${"%.2f".format(similarity)} " +
            "(rms=${"%.2f".format(rmsSim)}, rmsVar=${"%.2f".format(rmsVarSim)}, " +
            "avgRMS=${"%.1f".format(currentAvgRMS)}dB, enrolled=${"%.1f".format(enrolled.avgRMS)}dB) " +
            "→ ${if (isMatch) "MATCH" else "REJECT"}")

        return VerificationResult(similarity, isMatch)
    }

    fun resetEnrollment() {
        prefs.edit()
            .remove(KEY_ENROLLED)
            .remove(KEY_AVG_RMS)
            .remove(KEY_RMS_VARIANCE)
            .remove(KEY_ENROLLMENT_COUNT)
            .apply()
        Log.i(TAG, "Enrollment reset")
    }

    private fun saveFeatures(features: RmsFeatures) {
        prefs.edit()
            .putFloat(KEY_AVG_RMS, features.avgRMS.toFloat())
            .putFloat(KEY_RMS_VARIANCE, features.rmsVariance.toFloat())
            .apply()
    }

    private fun loadFeatures(): RmsFeatures? {
        if (!prefs.contains(KEY_AVG_RMS)) return null
        return RmsFeatures(
            avgRMS = prefs.getFloat(KEY_AVG_RMS, 0f).toDouble(),
            rmsVariance = prefs.getFloat(KEY_RMS_VARIANCE, 0f).toDouble()
        )
    }

    private fun variance(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return values.sumOf { (it - mean) * (it - mean) } / values.size
    }

    data class RmsFeatures(
        val avgRMS: Double,
        val rmsVariance: Double
    )

    data class VerificationResult(
        val similarity: Float,
        val isMatch: Boolean
    )

    sealed class EnrollmentResult {
        data class SampleRecorded(val current: Int, val required: Int) : EnrollmentResult()
        data class Complete(val totalSamples: Int) : EnrollmentResult()
        data class Error(val message: String) : EnrollmentResult()
    }
}
