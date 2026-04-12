package com.trama.app.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.trama.shared.audio.CircularAudioBuffer
import com.trama.shared.audio.CapturedAudioWindow
import com.trama.shared.audio.ContextualCaptureAssembler
import com.trama.shared.audio.ContextualCaptureConfig
import com.trama.shared.audio.LightweightGateAsr
import com.trama.shared.audio.NoOpLightweightGateAsr
import com.trama.shared.speech.SimpleVAD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Captures continuous PCM audio in memory, keeping a rolling pre-roll buffer and emitting
 * bounded windows around detected voice activity.
 *
 * Gate ASR runs in separate coroutines so the audio read loop is never blocked.
 * Audio is never persisted. Windows are assembled entirely in RAM and handed off to the ASR layer.
 */
class ContextualAudioCaptureEngine(
    private val context: Context,
    initialConfig: ContextualCaptureConfig,
    private val gateAsr: LightweightGateAsr = NoOpLightweightGateAsr,
    private val triggerDetector: (String) -> Boolean = { false }
) {
    companion object {
        private const val TAG = "ContextualAudioCapture"
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val FRAME_SIZE = 512
        private const val MAX_RING_BUFFER_SECONDS = 20
        private const val MIN_TRIGGER_CHECK_MS = 2_000L
        private const val GATE_CHECK_INTERVAL_MS = 2_500L
        private const val MIN_CAPTURE_AFTER_TRIGGER_MS = 2_500L
        private const val MAX_CONSECUTIVE_EMPTY_READS = 120
        private const val ADAPTIVE_TRIGGER_SILENCE_MS = 2_500L
        private const val ADAPTIVE_SHORT_PHRASE_SILENCE_MS = 3_000L
    }

    private data class ActiveCapture(
        val config: ContextualCaptureConfig,
        val assembler: ContextualCaptureAssembler,
        val preRollWindow: CapturedAudioWindow,
        var triggerMatched: Boolean = false,
        var triggerAlreadyDetected: Boolean = false,
        var lastGateTranscript: String = "",
        var firstTriggerAtSample: Int = -1,
        var lastGateCheckSample: Int = 0,
        var gateJob: Job? = null,
        var postRollRemainingSamples: Int = -1,
        var capturedSamples: Int = 0
    )

    @Volatile
    private var config: ContextualCaptureConfig = sanitize(initialConfig)

    @Volatile
    private var running = false

    var onWindowCaptured: ((CapturedAudioWindow) -> Unit)? = null
    var onStatusChanged: ((String) -> Unit)? = null
    var onGateMatch: ((String) -> Unit)? = null
    var onGateEvaluated: ((String, Boolean, String) -> Unit)? = null

    fun updateConfig(newConfig: ContextualCaptureConfig) {
        config = sanitize(newConfig)
    }

    suspend fun start() = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            return@withContext
        }

        val scope = CoroutineScope(coroutineContext)

        val loopConfig = config
        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(loopConfig.sampleRateHz, CHANNEL, ENCODING),
            FRAME_SIZE * 2
        )

        val audioRecord = try {
            @Suppress("MissingPermission")
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                loopConfig.sampleRateHz,
                CHANNEL,
                ENCODING,
                bufferSize
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioRecord", e)
            return@withContext
        }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not initialized")
            audioRecord.release()
            return@withContext
        }

        val rollingBuffer = CircularAudioBuffer(
            sampleRateHz = loopConfig.sampleRateHz,
            maxSeconds = max(MAX_RING_BUFFER_SECONDS, loopConfig.preRollSeconds + 2)
        )
        val vad = SimpleVAD()
        var activeCapture: ActiveCapture? = null

        fun launchGateEval(capture: ActiveCapture, isFinal: Boolean) {
            if (capture.gateJob?.isActive == true) return
            val snapshot = capture.assembler.finalizeWindow(capture.preRollWindow)
            capture.gateJob = scope.launch(Dispatchers.IO) {
                try {
                    val eval = evaluateGateWindows(snapshot)
                    onGateEvaluated?.invoke(eval.bestTranscript, eval.matched, eval.debugSummary)
                    capture.lastGateTranscript = eval.bestTranscript
                    if (eval.matched && !capture.triggerAlreadyDetected) {
                        capture.triggerAlreadyDetected = true
                        if (!capture.triggerMatched) {
                            capture.triggerMatched = true
                            capture.firstTriggerAtSample = capture.capturedSamples
                            onGateMatch?.invoke(eval.bestTranscript)
                            onStatusChanged?.invoke("trigger_detected")
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Gate eval failed", t)
                    if (isFinal) onGateEvaluated?.invoke("(error: ${t.message})", false, "exception")
                }
            }
        }

        fun startCapture() {
            val captureConfig = config
            val assembler = ContextualCaptureAssembler(captureConfig)
            activeCapture = ActiveCapture(
                config = captureConfig,
                assembler = assembler,
                preRollWindow = assembler.beginCapture(rollingBuffer)
            )
            onStatusChanged?.invoke(if (gateAsr.isAvailable) "gating" else "capturing")
        }

        fun finalizeCapture(reason: String) {
            val capture = activeCapture ?: return
            activeCapture = null

            val finalWindow = capture.assembler.finalizeWindow(capture.preRollWindow)
            if (finalWindow.mergedPcm().isEmpty()) {
                onStatusChanged?.invoke("rearmed")
                onStatusChanged?.invoke("listening")
                return
            }

            Log.i(
                TAG,
                "Finalizing capture ($reason): triggerMatched=${capture.triggerMatched}, " +
                    "capturedMs=${(capture.capturedSamples * 1000L) / capture.config.sampleRateHz}, " +
                    "windowMs=${finalWindow.durationMs()}"
            )

            if (gateAsr.isAvailable) {
                // Cancel any in-flight periodic gate job and run a final eval
                capture.gateJob?.cancel()
                scope.launch(Dispatchers.IO) {
                    try {
                        val eval = evaluateGateWindows(finalWindow)
                        onGateEvaluated?.invoke(eval.bestTranscript, eval.matched, eval.debugSummary)
                        if (eval.matched) {
                            onGateMatch?.invoke(eval.bestTranscript)
                            onStatusChanged?.invoke("trigger_detected")
                            onWindowCaptured?.invoke(finalWindow)
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "Final gate eval failed", t)
                        onGateEvaluated?.invoke("(error: ${t.message})", false, "exception")
                    } finally {
                        onStatusChanged?.invoke("rearmed")
                        onStatusChanged?.invoke("listening")
                    }
                }
            } else {
                onWindowCaptured?.invoke(finalWindow)
                onStatusChanged?.invoke("rearmed")
                onStatusChanged?.invoke("listening")
            }
        }

        vad.onVoiceStart = {
            if (activeCapture == null) {
                startCapture()
            } else {
                activeCapture?.postRollRemainingSamples = -1
            }
        }
        vad.onVoiceEnd = {
            activeCapture?.let { capture ->
                val adaptiveSilenceMs = adaptiveSilenceStopMs(capture)
                val silenceSamples =
                    ((adaptiveSilenceMs * capture.config.sampleRateHz) / 1000L).toInt()
                if (capture.triggerMatched && capture.firstTriggerAtSample >= 0) {
                    val minAfterTriggerSamples =
                        ((MIN_CAPTURE_AFTER_TRIGGER_MS * capture.config.sampleRateHz) / 1000L).toInt()
                    val capturedAfterTrigger = capture.capturedSamples - capture.firstTriggerAtSample
                    val remainingToMinimum = (minAfterTriggerSamples - capturedAfterTrigger).coerceAtLeast(0)
                    capture.postRollRemainingSamples = maxOf(silenceSamples, remainingToMinimum)
                } else {
                    capture.postRollRemainingSamples = silenceSamples
                }
            }
        }

        running = true
        audioRecord.startRecording()
        onStatusChanged?.invoke("listening")
        Log.i(TAG, "Contextual audio capture started")

        try {
            val buffer = ShortArray(FRAME_SIZE)
            var consecutiveEmptyReads = 0
            while (running && isActive) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read <= 0) {
                    consecutiveEmptyReads++
                    if (consecutiveEmptyReads >= MAX_CONSECUTIVE_EMPTY_READS) {
                        Log.w(TAG, "AudioRecord stalled ($consecutiveEmptyReads empty reads), restarting capture loop")
                        onStatusChanged?.invoke("stalled")
                        return@withContext
                    }
                    continue
                }
                consecutiveEmptyReads = 0

                vad.processFrame(buffer, read)

                activeCapture?.let { capture ->
                    val chunk = if (read == buffer.size) buffer.copyOf() else buffer.copyOf(read)
                    capture.assembler.appendPostRoll(chunk)
                    capture.capturedSamples += read

                    if (gateAsr.isAvailable && capture.postRollRemainingSamples < 0) {
                        val minCheckSamples = ((MIN_TRIGGER_CHECK_MS * capture.config.sampleRateHz) / 1000L).toInt()
                        val intervalSamples = ((GATE_CHECK_INTERVAL_MS * capture.config.sampleRateHz) / 1000L).toInt()

                        if (capture.capturedSamples >= minCheckSamples &&
                            capture.capturedSamples - capture.lastGateCheckSample >= intervalSamples
                        ) {
                            capture.lastGateCheckSample = capture.capturedSamples
                            launchGateEval(capture, isFinal = false)
                        }
                    }

                    val maxAfterTriggerSamples = capture.config.postRollSeconds * capture.config.sampleRateHz
                    val reachedTriggerCap = capture.triggerMatched &&
                        capture.firstTriggerAtSample >= 0 &&
                        (capture.capturedSamples - capture.firstTriggerAtSample) >= maxAfterTriggerSamples

                    if (reachedTriggerCap) {
                        finalizeCapture("post_roll_cap")
                    } else if (capture.postRollRemainingSamples >= 0) {
                        capture.postRollRemainingSamples -= read
                        if (capture.postRollRemainingSamples <= 0) {
                            finalizeCapture("silence_stop")
                        }
                    }
                }

                rollingBuffer.append(buffer, read)
            }
        } finally {
            running = false
            try {
                audioRecord.stop()
            } catch (_: Exception) {
            }
            audioRecord.release()
            Log.i(TAG, "Contextual audio capture stopped")
        }
    }

    fun stop() {
        running = false
    }

    private fun sanitize(raw: ContextualCaptureConfig): ContextualCaptureConfig {
        return raw.copy(
            preRollSeconds = raw.preRollSeconds.coerceIn(1, 30),
            postRollSeconds = raw.postRollSeconds.coerceIn(1, 30),
            sampleRateHz = raw.sampleRateHz.coerceAtLeast(8_000),
            silenceStopMs = raw.silenceStopMs.coerceAtLeast(250L),
            gateEvalWindowsMs = raw.gateEvalWindowsMs
                .ifEmpty { listOf(0L) }
                .map { it.coerceAtLeast(0L) }
                .distinct()
        )
    }

    private suspend fun evaluateGateWindows(window: CapturedAudioWindow): GateEvaluation {
        val transcript = runCatching {
            gateAsr.transcribe(window, languageTag = "es")
        }.getOrNull()?.trim().orEmpty()

        val matched = transcript.isNotBlank() && triggerDetector(transcript)
        val debug = "full(${window.durationMs()}ms): '${transcript.ifBlank { "-" }}'${if (matched) " [MATCH]" else ""}"

        return GateEvaluation(
            matched = matched,
            bestTranscript = transcript,
            debugSummary = debug
        )
    }

    private fun adaptiveSilenceStopMs(capture: ActiveCapture): Long {
        val base = capture.config.silenceStopMs
        val transcriptWordCount = capture.lastGateTranscript
            .split("\\s+".toRegex())
            .count { it.isNotBlank() }

        val triggerAware = if (capture.triggerMatched) {
            max(base, ADAPTIVE_TRIGGER_SILENCE_MS)
        } else {
            base
        }

        return if (transcriptWordCount in 1..4) {
            max(triggerAware, ADAPTIVE_SHORT_PHRASE_SILENCE_MS)
        } else {
            triggerAware
        }
    }

    private data class GateEvaluation(
        val matched: Boolean,
        val bestTranscript: String,
        val debugSummary: String
    )
}
