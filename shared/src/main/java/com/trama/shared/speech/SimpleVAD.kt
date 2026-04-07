package com.trama.shared.speech

import kotlin.math.sqrt

/**
 * Simple Voice Activity Detector using audio energy (RMS threshold).
 *
 * Ultra-lightweight — zero dependencies, works on watch.
 * Analyzes audio frames and determines if someone is speaking.
 *
 * Battery optimized (gentle):
 * - Processes 1 of every [FRAME_SKIP] frames to reduce CPU (~50% saving)
 * - Uses pre-computed RMS thresholds to avoid log10()
 * - Caller should add delay(INTER_FRAME_DELAY_MS) between reads
 * - Designed to trigger fast (~90ms) so SpeechRecognizer catches full phrase
 */
class SimpleVAD {

    companion object {
        // Require sustained speech/silence to avoid false triggers
        // SPEECH_FRAMES_REQUIRED = 2 → triggers in ~2 processed frames (~90ms)
        private const val SPEECH_FRAMES_REQUIRED = 2
        private const val SILENCE_FRAMES_REQUIRED = 20

        // Process 1 of every N frames to save CPU (2 = 50% saving)
        const val FRAME_SKIP = 2

        // Delay between AudioRecord reads (ms) — lets CPU sleep briefly
        const val INTER_FRAME_DELAY_MS = 10L

        // VAD timeout: fallback to SpeechRecognizer if no voice detected
        // ~30s = 30000ms / (32ms frame + 10ms delay) / FRAME_SKIP ≈ 357 processed frames
        const val FALLBACK_TIMEOUT_FRAMES = 357

        // Pre-computed RMS thresholds — sensitive for watch mic
        // dBFS = 20 * log10(rms / 32768)
        // rms = 32768 * 10^(dBFS/20)
        // -48 dBFS → rms ≈ 130.4  (speech)
        // -55 dBFS → rms ≈ 58.3   (silence)
        private const val SPEECH_RMS_THRESHOLD = 130.4
        private const val SILENCE_RMS_THRESHOLD = 58.3
    }

    private var speechFrameCount = 0
    private var silenceFrameCount = 0
    private var frameCounter = 0
    private var processedFrameCount = 0

    @Volatile
    var isSpeaking = false
        private set

    var onVoiceStart: (() -> Unit)? = null
    var onVoiceEnd: (() -> Unit)? = null
    var onTimeout: (() -> Unit)? = null

    /**
     * Process a frame of 16-bit PCM audio.
     * Skips [FRAME_SKIP-1] out of every [FRAME_SKIP] frames.
     *
     * @return true if this frame was actually processed (not skipped)
     */
    fun processFrame(buffer: ShortArray, length: Int): Boolean {
        frameCounter++
        if (frameCounter % FRAME_SKIP != 0) return false

        processedFrameCount++
        val rms = calculateRMS(buffer, length)

        if (rms > SPEECH_RMS_THRESHOLD) {
            speechFrameCount++
            silenceFrameCount = 0

            if (!isSpeaking && speechFrameCount >= SPEECH_FRAMES_REQUIRED) {
                isSpeaking = true
                onVoiceStart?.invoke()
            }
        } else if (rms < SILENCE_RMS_THRESHOLD) {
            silenceFrameCount++
            speechFrameCount = 0

            if (isSpeaking && silenceFrameCount >= SILENCE_FRAMES_REQUIRED) {
                isSpeaking = false
                onVoiceEnd?.invoke()
            }
        }

        // Timeout: if no voice detected, notify caller to fallback
        if (!isSpeaking && processedFrameCount >= FALLBACK_TIMEOUT_FRAMES) {
            processedFrameCount = 0
            onTimeout?.invoke()
        }

        return true
    }

    fun reset() {
        speechFrameCount = 0
        silenceFrameCount = 0
        frameCounter = 0
        processedFrameCount = 0
        isSpeaking = false
    }

    private fun calculateRMS(buffer: ShortArray, length: Int): Double {
        var sum = 0L
        for (i in 0 until length) {
            val sample = buffer[i].toLong()
            sum += sample * sample
        }
        return sqrt(sum.toDouble() / length)
    }
}
