package com.trama.wear.speech

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

/**
 * Speaker enrollment on the watch using its own microphone.
 * Captures RMS energy profile from SpeechRecognizer.onRmsChanged
 * so verification uses the same mic as enrollment.
 */
class WatchSpeakerEnrollment(private val context: Context) {

    companion object {
        private const val TAG = "WatchEnrollment"
        private const val PREFS = "watch_speaker_enrollment"
        private const val KEY_PROFILE_JSON = "profile_json"
        private const val KEY_ENROLLED = "is_enrolled"
        private const val KEY_ENROLLMENT_COUNT = "enrollment_count"

        const val REQUIRED_SAMPLES = 3
        const val ENROLLMENT_DURATION_MS = 5000L

        private const val SPEECH_RMS_THRESHOLD = 1.0f

        val ENROLLMENT_PHRASES = listOf(
            "Recordar comprar leche y pan",
            "Anotar llamar al médico mañana",
            "Acordarme de mover el coche"
        )
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun isEnrolled(): Boolean = prefs.getBoolean(KEY_ENROLLED, false)
    fun getEnrollmentCount(): Int = prefs.getInt(KEY_ENROLLMENT_COUNT, 0)

    fun getProfile(): SpeakerProfile? {
        val json = prefs.getString(KEY_PROFILE_JSON, null) ?: return null
        return SpeakerProfile.deserialize(json)
    }

    /**
     * Record one enrollment sample. Must be called from main thread.
     */
    fun recordSample(onResult: (EnrollmentResult) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onResult(EnrollmentResult.Error("Reconocimiento no disponible"))
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
                onResult(EnrollmentResult.Error("No se detectó suficiente voz"))
                return
            }

            val rmsDoubles = rmsValues.map { it.toDouble() }
            val avgRMS = rmsDoubles.average()
            val rmsVariance = variance(rmsDoubles)

            val count = prefs.getInt(KEY_ENROLLMENT_COUNT, 0)
            val existingProfile = getProfile()

            val newProfile = if (count == 0 || existingProfile == null) {
                SpeakerProfile(avgRMS, rmsVariance, 1)
            } else {
                // Merge with existing profile (running average)
                SpeakerProfile(
                    avgRMS = (existingProfile.avgRMS * count + avgRMS) / (count + 1),
                    rmsVariance = (existingProfile.rmsVariance * count + rmsVariance) / (count + 1),
                    enrollmentCount = count + 1
                )
            }

            val newCount = count + 1
            prefs.edit()
                .putString(KEY_PROFILE_JSON, SpeakerProfile.serialize(newProfile))
                .putInt(KEY_ENROLLMENT_COUNT, newCount)
                .putBoolean(KEY_ENROLLED, newCount >= REQUIRED_SAMPLES)
                .apply()

            Log.i(TAG, "Sample $newCount: avgRMS=${"%.2f".format(avgRMS)}, var=${"%.2f".format(rmsVariance)}, frames=${rmsValues.size}")

            onResult(
                if (newCount >= REQUIRED_SAMPLES) EnrollmentResult.Complete(newCount)
                else EnrollmentResult.SampleRecorded(newCount, REQUIRED_SAMPLES)
            )
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                if (rmsdB > SPEECH_RMS_THRESHOLD) rmsValues.add(rmsdB)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                Log.w(TAG, "Enrollment error: $error")
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

        handler.postDelayed({
            try { recognizer.stopListening() } catch (_: Exception) {}
            finish()
        }, ENROLLMENT_DURATION_MS + 2000)
    }

    fun resetEnrollment() {
        prefs.edit().clear().apply()
        Log.i(TAG, "Enrollment reset")
    }

    private fun variance(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return values.sumOf { (it - mean) * (it - mean) } / values.size
    }

    sealed class EnrollmentResult {
        data class SampleRecorded(val current: Int, val required: Int) : EnrollmentResult()
        data class Complete(val totalSamples: Int) : EnrollmentResult()
        data class Error(val message: String) : EnrollmentResult()
    }
}
