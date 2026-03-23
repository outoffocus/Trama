package com.mydiary.shared.speech

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Simple Voice Activity Detector using audio energy (dB threshold).
 *
 * Ultra-lightweight — zero dependencies, works on watch.
 * Analyzes audio frames and determines if someone is speaking.
 *
 * Usage:
 * 1. Feed audio frames via processFrame()
 * 2. Check isSpeaking to determine if SpeechRecognizer should be active
 */
class SimpleVAD {

    companion object {
        // Thresholds (dBFS - decibels relative to full scale)
        private const val SPEECH_THRESHOLD_DB = -38.0
        private const val SILENCE_THRESHOLD_DB = -45.0

        // Require sustained speech/silence to avoid false triggers
        private const val SPEECH_FRAMES_REQUIRED = 8    // ~256ms
        private const val SILENCE_FRAMES_REQUIRED = 60  // ~2s
    }

    private var speechFrameCount = 0
    private var silenceFrameCount = 0

    @Volatile
    var isSpeaking = false
        private set

    var onVoiceStart: (() -> Unit)? = null
    var onVoiceEnd: (() -> Unit)? = null

    /**
     * Process a frame of 16-bit PCM audio.
     * Call this for each audio buffer chunk.
     *
     * @param buffer PCM 16-bit audio samples
     * @param length number of valid samples in buffer
     */
    fun processFrame(buffer: ShortArray, length: Int) {
        val rms = calculateRMS(buffer, length)
        val dbFS = if (rms > 0) 20.0 * log10(rms / 32768.0) else -100.0

        if (dbFS > SPEECH_THRESHOLD_DB) {
            speechFrameCount++
            silenceFrameCount = 0

            if (!isSpeaking && speechFrameCount >= SPEECH_FRAMES_REQUIRED) {
                isSpeaking = true
                onVoiceStart?.invoke()
            }
        } else if (dbFS < SILENCE_THRESHOLD_DB) {
            silenceFrameCount++
            speechFrameCount = 0

            if (isSpeaking && silenceFrameCount >= SILENCE_FRAMES_REQUIRED) {
                isSpeaking = false
                onVoiceEnd?.invoke()
            }
        }
    }

    /**
     * Reset state. Call when restarting the detection cycle.
     */
    fun reset() {
        speechFrameCount = 0
        silenceFrameCount = 0
        isSpeaking = false
    }

    private fun calculateRMS(buffer: ShortArray, length: Int): Double {
        var sum = 0.0
        for (i in 0 until length) {
            val sample = buffer[i].toDouble()
            sum += sample * sample
        }
        return sqrt(sum / length)
    }
}
