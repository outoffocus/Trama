package com.trama.app.audio

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.trama.shared.audio.AssetFileCache
import com.trama.shared.audio.CapturedAudioWindow

/**
 * Post-segment, pre-Whisper filter that rejects windows containing no human
 * speech (music, silence, ambient noise). Diagnostic data showed ~43% of
 * Whisper decodes were spent on bracket-only outputs like "[Música]" or
 * "(Puerto)" — pure waste of CPU and battery. Silero VAD distinguishes
 * speech from those at ~1 ms per frame, so the savings massively outweigh
 * the inference cost.
 *
 * Activates only when the bundled `silero_vad.onnx` model is present in
 * `assets/asr/vad/`. If the model is missing, callers degrade to the prior
 * behaviour (always invoke Whisper) by getting a `null` from `tryCreate`.
 */
class SileroVadFilter private constructor(
    private val vad: Vad,
    private val sampleRateHz: Int
) {
    data class Decision(
        val containsSpeech: Boolean,
        val reason: String,
        val elapsedMs: Long
    )

    /**
     * Run Silero on the merged PCM of [window] and decide whether it contains
     * speech. Returns true (allow Whisper) on any error or empty input — we
     * never want this filter to silently drop legitimate audio.
     */
    @Synchronized
    fun containsSpeech(window: CapturedAudioWindow): Boolean {
        return evaluate(window).containsSpeech
    }

    @Synchronized
    fun evaluate(window: CapturedAudioWindow): Decision {
        val startedAt = System.currentTimeMillis()
        val pcm = window.mergedPcm()
        if (pcm.isEmpty()) {
            return Decision(
                containsSpeech = true,
                reason = "empty_pcm_allow",
                elapsedMs = System.currentTimeMillis() - startedAt
            )
        }
        return try {
            val samples = FloatArray(pcm.size) { i -> pcm[i] / 32768f }
            vad.reset()
            vad.acceptWaveform(samples)
            vad.flush()
            val containsSpeech = vad.isSpeechDetected() || !vad.empty()
            Decision(
                containsSpeech = containsSpeech,
                reason = if (containsSpeech) "silero_vad_speech" else "silero_vad_no_speech",
                elapsedMs = System.currentTimeMillis() - startedAt
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Silero VAD failed, allowing window through", t)
            Decision(
                containsSpeech = true,
                reason = "silero_vad_error_allow:${t.javaClass.simpleName}",
                elapsedMs = System.currentTimeMillis() - startedAt
            )
        }
    }

    @Synchronized
    fun release() {
        try {
            vad.release()
        } catch (_: Throwable) {
        }
    }

    companion object {
        private const val TAG = "SileroVadFilter"
        private const val MODEL_ASSET = "asr/vad/silero_vad.onnx"
        private const val DEFAULT_SAMPLE_RATE = 16_000
        // Permissive defaults: we want to *allow* legitimate speech even when
        // mixed with background noise, not reject anything ambiguous. The
        // 0.4 threshold (vs. Silero's 0.5 default) leans toward recall.
        private const val THRESHOLD = 0.4f
        private const val MIN_SPEECH_DURATION = 0.25f
        private const val MIN_SILENCE_DURATION = 0.10f
        private const val MAX_SPEECH_DURATION = 30.0f
        private const val WINDOW_SIZE = 512

        fun tryCreate(context: Context, sampleRateHz: Int = DEFAULT_SAMPLE_RATE): SileroVadFilter? {
            val cache = AssetFileCache(context)
            if (!cache.assetExists(MODEL_ASSET)) {
                Log.i(TAG, "Silero VAD model not bundled at $MODEL_ASSET — feature disabled")
                return null
            }
            return try {
                val modelPath = cache.ensureCopied(MODEL_ASSET)
                val sileroConfig = SileroVadModelConfig.builder()
                    .setModel(modelPath)
                    .setThreshold(THRESHOLD)
                    .setMinSilenceDuration(MIN_SILENCE_DURATION)
                    .setMinSpeechDuration(MIN_SPEECH_DURATION)
                    .setMaxSpeechDuration(MAX_SPEECH_DURATION)
                    .setWindowSize(WINDOW_SIZE)
                    .build()
                val vadConfig = VadModelConfig.builder()
                    .setSileroVadModelConfig(sileroConfig)
                    .setSampleRate(sampleRateHz)
                    .setNumThreads(1)
                    .setDebug(false)
                    .build()
                SileroVadFilter(Vad(vadConfig), sampleRateHz)
            } catch (t: Throwable) {
                Log.w(TAG, "Silero VAD init failed", t)
                null
            }
        }
    }
}
