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
        private const val PERIODIC_FALLBACK_WINDOW_MS = 10_000L
        private const val GATE_EVAL_BACKOFF_THRESHOLD = 3
        private const val GATE_EVAL_SKIP_THRESHOLD = 6
        // Pre-filter for periodic gate evals: require this much sustained VAD-speech
        // (in wall ms) since the previous eval before invoking the gate ASR.
        // 600 ms covers a full trigger word with margin while filtering noise.
        private const val MIN_SPEECH_MS_BEFORE_GATE_EVAL = 600L
        // After this many consecutive capture finalizations with no trigger match,
        // suspend periodic gate evals: only the final eval at finalize time runs.
        // This is the dominant cost saver in continuous-speech environments where
        // the energy-based VAD pre-filter cannot help.
        private const val AMBIENT_BACKOFF_THRESHOLD = 1
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
        var lastGateEvalCoveredSample: Int = 0,
        var gateJob: Job? = null,
        var postRollRemainingSamples: Int = -1,
        var capturedSamples: Int = 0,
        var consecutiveEmptyGateEvals: Int = 0
    )

    @Volatile
    private var config: ContextualCaptureConfig = sanitize(initialConfig)

    @Volatile
    private var running = false

    var onWindowCaptured: ((CapturedAudioWindow, String) -> Unit)? = null
    var onStatusChanged: ((String) -> Unit)? = null
    var onGateMatch: ((String) -> Unit)? = null
    var onGateEvaluated: ((String, Boolean, String) -> Unit)? = null
    var onSegmentFinalized: ((String, Long, Int, Boolean) -> Unit)? = null
    var onGateEvalSkipped: ((reason: String, speechMs: Long, thresholdMs: Long) -> Unit)? = null
    var shouldCaptureUnmatchedFinalWindow: ((CapturedAudioWindow, String, String) -> Boolean)? = null
    var shouldCaptureUnmatchedGateWindow: ((Long, String, String, Boolean) -> Boolean)? = null

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
        // Engine-level: counts captures finalized without a trigger match.
        // Resets to 0 on the first match. Drives the ambient-speech backoff.
        var consecutiveCapsWithoutMatch = 0

        fun launchGateEval(capture: ActiveCapture, isFinal: Boolean) {
            if (capture.gateJob?.isActive == true) return
            val coveredThroughSample = capture.capturedSamples
            val snapshot = capture.assembler.finalizeWindow(capture.preRollWindow)
            capture.gateJob = scope.launch(Dispatchers.IO) {
                try {
                    val eval = evaluateGateWindows(
                        snapshot,
                        isFinal = isFinal,
                        config = capture.config,
                        includeFullWindow = isFinal
                    )
                    onGateEvaluated?.invoke(eval.bestTranscript, eval.matched, eval.debugSummary)
                    capture.lastGateTranscript = eval.bestTranscript
                    if (!isFinal) {
                        capture.lastGateEvalCoveredSample = maxOf(
                            capture.lastGateEvalCoveredSample,
                            coveredThroughSample
                        )
                        if (eval.bestTranscript.isBlank()) {
                            capture.consecutiveEmptyGateEvals += 1
                        } else {
                            capture.consecutiveEmptyGateEvals = 0
                        }
                    }
                    if (eval.matched && !capture.triggerAlreadyDetected) {
                        capture.triggerAlreadyDetected = true
                        if (!capture.triggerMatched) {
                            capture.triggerMatched = true
                            capture.firstTriggerAtSample = capture.capturedSamples
                            onGateMatch?.invoke(eval.bestTranscript)
                            onStatusChanged?.invoke("trigger_detected")
                        }
                    } else if (!eval.matched && !isFinal) {
                        val fallbackWindowMs = minOf(snapshot.durationMs(), PERIODIC_FALLBACK_WINDOW_MS)
                        if (shouldCaptureUnmatchedGateWindow?.invoke(
                                fallbackWindowMs,
                                eval.bestTranscript,
                                eval.debugSummary,
                                false
                            ) == true
                        ) {
                            val fallbackWindow = snapshot.tailWindow(PERIODIC_FALLBACK_WINDOW_MS)
                            onStatusChanged?.invoke("trigger_uncertain")
                            onWindowCaptured?.invoke(fallbackWindow, "uncertain_fallback")
                        }
                    }
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    throw ce
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
            val dropped = capture.assembler.droppedSampleCount
            onSegmentFinalized?.invoke(
                reason,
                finalWindow.durationMs(),
                dropped,
                capture.triggerMatched
            )
            if (dropped > 0) {
                Log.w(TAG, "Capture cap reached, dropped ${dropped} samples (reason=$reason). " +
                    "Engine likely failed to stop — check silence detection.")
            }
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
                if (capture.triggerMatched) {
                    consecutiveCapsWithoutMatch = 0
                    onStatusChanged?.invoke("trigger_detected")
                    onWindowCaptured?.invoke(finalWindow, "trigger")
                    onStatusChanged?.invoke("rearmed")
                    onStatusChanged?.invoke("listening")
                    return
                }

                // Cancel any in-flight periodic gate job and run a final eval.
                // If periodic gate windows already covered the segment up to the
                // final tail, avoid re-transcribing the full capture at close.
                capture.gateJob?.cancel()
                val finalTailMs = shortestGateTailMs(capture.config)
                val finalTailSamples = ((finalTailMs * capture.config.sampleRateHz) / 1000L).toInt()
                val periodicCoverageReachesFinalTail =
                    capture.lastGateEvalCoveredSample > 0 &&
                        capture.capturedSamples - capture.lastGateEvalCoveredSample <= finalTailSamples
                scope.launch(Dispatchers.IO) {
                    try {
                        val eval = evaluateGateWindows(
                            finalWindow,
                            isFinal = true,
                            config = capture.config,
                            includeFullWindow = !periodicCoverageReachesFinalTail
                        )
                        onGateEvaluated?.invoke(eval.bestTranscript, eval.matched, eval.debugSummary)
                        if (eval.matched || capture.triggerMatched) {
                            consecutiveCapsWithoutMatch = 0
                            if (eval.matched) {
                                onGateMatch?.invoke(eval.bestTranscript)
                            }
                            onStatusChanged?.invoke("trigger_detected")
                            onWindowCaptured?.invoke(finalWindow, "trigger")
                        } else if (shouldCaptureUnmatchedFinalWindow?.invoke(
                                finalWindow,
                                eval.bestTranscript,
                                eval.debugSummary
                            ) == true ||
                            shouldCaptureUnmatchedGateWindow?.invoke(
                                finalWindow.durationMs(),
                                eval.bestTranscript,
                                eval.debugSummary,
                                true
                            ) == true
                        ) {
                            consecutiveCapsWithoutMatch += 1
                            onStatusChanged?.invoke("trigger_uncertain")
                            onWindowCaptured?.invoke(finalWindow, "uncertain_fallback")
                        } else {
                            consecutiveCapsWithoutMatch += 1
                        }
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        throw ce
                    } catch (t: Throwable) {
                        Log.w(TAG, "Final gate eval failed", t)
                        onGateEvaluated?.invoke("(error: ${t.message})", false, "exception")
                    } finally {
                        onStatusChanged?.invoke("rearmed")
                        onStatusChanged?.invoke("listening")
                    }
                }
            } else {
                onWindowCaptured?.invoke(finalWindow, "no_gate")
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
                val frameDurationMs = (read.toLong() * 1000L) / loopConfig.sampleRateHz
                vad.accumulateIfSpeaking(frameDurationMs)

                activeCapture?.let { capture ->
                    val chunk = if (read == buffer.size) buffer.copyOf() else buffer.copyOf(read)
                    capture.assembler.appendPostRoll(chunk)
                    capture.capturedSamples += read

                    if (gateAsr.isAvailable && capture.postRollRemainingSamples < 0) {
                        val minCheckSamples = ((MIN_TRIGGER_CHECK_MS * capture.config.sampleRateHz) / 1000L).toInt()
                        val ambientBackoff = consecutiveCapsWithoutMatch >= AMBIENT_BACKOFF_THRESHOLD
                        val backoffFactor = when {
                            ambientBackoff -> Int.MAX_VALUE
                            capture.consecutiveEmptyGateEvals >= GATE_EVAL_SKIP_THRESHOLD -> Int.MAX_VALUE
                            capture.consecutiveEmptyGateEvals >= GATE_EVAL_BACKOFF_THRESHOLD -> 2
                            else -> 1
                        }
                        if (ambientBackoff &&
                            capture.capturedSamples >= minCheckSamples &&
                            capture.lastGateCheckSample == 0
                        ) {
                            // Surface once per capture so the diagnostics show why
                            // periodic evals are silent.
                            capture.lastGateCheckSample = capture.capturedSamples
                            onGateEvalSkipped?.invoke(
                                "ambient_backoff",
                                consecutiveCapsWithoutMatch.toLong(),
                                AMBIENT_BACKOFF_THRESHOLD.toLong()
                            )
                        }
                        if (backoffFactor != Int.MAX_VALUE) {
                            val intervalSamples =
                                ((GATE_CHECK_INTERVAL_MS * capture.config.sampleRateHz) / 1000L).toInt() * backoffFactor

                            if (capture.capturedSamples >= minCheckSamples &&
                                capture.capturedSamples - capture.lastGateCheckSample >= intervalSamples
                            ) {
                                val speechMs = vad.peekSpeechMs()
                                if (speechMs >= MIN_SPEECH_MS_BEFORE_GATE_EVAL) {
                                    vad.consumeSpeechMs()
                                    capture.lastGateCheckSample = capture.capturedSamples
                                    launchGateEval(capture, isFinal = false)
                                } else {
                                    // Advance the cursor anyway so we re-check on the next interval
                                    // instead of spinning every frame.
                                    capture.lastGateCheckSample = capture.capturedSamples
                                    onGateEvalSkipped?.invoke(
                                        "insufficient_speech",
                                        speechMs,
                                        MIN_SPEECH_MS_BEFORE_GATE_EVAL
                                    )
                                }
                            }
                        }
                    }

                    val maxAfterTriggerSamples = capture.config.postRollSeconds * capture.config.sampleRateHz
                    val reachedTriggerCap = capture.triggerMatched &&
                        capture.firstTriggerAtSample >= 0 &&
                        (capture.capturedSamples - capture.firstTriggerAtSample) >= maxAfterTriggerSamples

                    if (reachedTriggerCap) {
                        finalizeCapture("post_roll_cap")
                        if (vad.isSpeaking && activeCapture == null) {
                            startCapture()
                        }
                    } else if (capture.postRollRemainingSamples >= 0) {
                        capture.postRollRemainingSamples -= read
                        if (capture.postRollRemainingSamples <= 0) {
                            finalizeCapture("silence_stop")
                            if (vad.isSpeaking && activeCapture == null) {
                                startCapture()
                            }
                        }
                    } else if (ContextualCapturePolicy.shouldRotateUnmatchedSegment(
                            triggerMatched = capture.triggerMatched,
                            capturedSamples = capture.capturedSamples,
                            sampleRateHz = capture.config.sampleRateHz
                        )
                    ) {
                        finalizeCapture("unmatched_segment_cap")
                        if (vad.isSpeaking && activeCapture == null) {
                            startCapture()
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

    private suspend fun evaluateGateWindows(
        window: CapturedAudioWindow,
        isFinal: Boolean,
        config: ContextualCaptureConfig,
        includeFullWindow: Boolean = isFinal
    ): GateEvaluation {
        val candidateWindows = if (isFinal) {
            config.gateEvalWindowsMs
                .filter { it > 0L }
                .map(window::tailWindow)
                .let { tails -> if (includeFullWindow) tails.plus(window) else tails }
                .distinctBy { it.durationMs() }
        } else {
            // Periodic gate eval runs every ~2.5s of speech: a single shortest tail
            // is enough to detect trigger words and keeps ASR cost ~3x lower.
            listOf(window.tailWindow(shortestGateTailMs(config)))
        }

        val debugParts = mutableListOf<String>()
        var bestTranscript = ""

        for (candidate in candidateWindows) {
            val transcript = runCatching {
                gateAsr.transcribe(candidate, languageTag = "es")
            }.getOrNull()?.trim().orEmpty()

            if (bestTranscript.isBlank() && transcript.isNotBlank()) {
                bestTranscript = transcript
            }

            val matched = transcript.isNotBlank() && triggerDetector(transcript)
            val label = if (candidate.durationMs() >= window.durationMs()) "full" else "tail"
            debugParts += "$label(${candidate.durationMs()}ms): '${transcript.ifBlank { "-" }}'${if (matched) " [MATCH]" else ""}"
            if (matched) {
                return GateEvaluation(
                    matched = true,
                    bestTranscript = transcript,
                    debugSummary = debugParts.joinToString(" | ")
                )
            }
        }

        return GateEvaluation(
            matched = false,
            bestTranscript = bestTranscript,
            debugSummary = debugParts.joinToString(" | ")
        )
    }

    private fun shortestGateTailMs(config: ContextualCaptureConfig): Long {
        return config.gateEvalWindowsMs
            .filter { it > 0L }
            .minOrNull()
            ?: 3_000L
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
