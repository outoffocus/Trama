package com.trama.shared.speech

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

/**
 * Serializable speaker voice profile for sync between phone and watch.
 * Contains RMS energy features from SpeechRecognizer.onRmsChanged.
 *
 * Enrollment happens on the phone. The profile is synced to the watch
 * via Wearable DataClient so the watch can also verify the speaker.
 */
@Serializable
data class SpeakerProfile(
    val avgRMS: Double,
    val rmsVariance: Double,
    val enrollmentCount: Int
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        // Similarity threshold — lower = more permissive
        private const val VERIFICATION_THRESHOLD = 0.45f

        // Minimum RMS dB to consider as speech (filter silence)
        const val SPEECH_RMS_THRESHOLD = 1.0

        fun serialize(profile: SpeakerProfile): String = json.encodeToString(serializer(), profile)

        fun deserialize(jsonStr: String): SpeakerProfile? {
            return try {
                json.decodeFromString(serializer(), jsonStr)
            } catch (_: Exception) {
                null
            }
        }

        /**
         * Verify if RMS values from SpeechRecognizer.onRmsChanged match this profile.
         * @param rmsValues raw dB values from onRmsChanged (same scale as enrollment)
         * @param profile enrolled speaker profile
         * @return similarity 0.0-1.0 and whether it's a match
         */
        fun verify(rmsValues: List<Double>, profile: SpeakerProfile, threshold: Float = VERIFICATION_THRESHOLD): VerificationResult {
            // Filter to speech frames only
            val speechFrames = rmsValues.filter { it > SPEECH_RMS_THRESHOLD }

            if (speechFrames.size < 5) {
                // Not enough data — accept rather than wrongly reject
                return VerificationResult(0.7f, true)
            }

            val currentAvgRMS = speechFrames.average()
            val currentRMSVar = variance(speechFrames)

            val rmsSim = 1.0 - minOf(
                abs(currentAvgRMS - profile.avgRMS) / maxOf(abs(profile.avgRMS), 1.0),
                1.0
            )
            val rmsVarSim = 1.0 - minOf(
                abs(currentRMSVar - profile.rmsVariance) / maxOf(profile.rmsVariance, 0.1),
                1.0
            )

            val similarity = (rmsSim * 0.65 + rmsVarSim * 0.35).toFloat()
            val isMatch = similarity >= threshold

            return VerificationResult(similarity, isMatch)
        }

        private fun variance(values: List<Double>): Double {
            if (values.size < 2) return 0.0
            val mean = values.average()
            return values.sumOf { (it - mean) * (it - mean) } / values.size
        }
    }

    @Serializable
    data class VerificationResult(
        val similarity: Float,
        val isMatch: Boolean
    )
}
