package com.trama.app.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.trama.shared.speech.SimpleVAD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Captures continuous PCM audio in memory, keeping a rolling pre-roll buffer and emitting
 * bounded windows around detected voice activity.
 *
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
        private const val MAX_SPEECH_SECONDS = 30
    }

    private data class ActiveCapture(
        val config: ContextualCaptureConfig,
        val assembler: ContextualCaptureAssembler,
        val preRollWindow: CapturedAudioWindow,
        var gateTranscript: String? = null,
        var triggerMatched: Boolean = false,
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

        fun finalizeCapture() {
            val capture = activeCapture ?: return
            val finalWindow = capture.assembler.finalizeWindow(capture.preRollWindow)
            activeCapture = null
            val shouldEmit = !gateAsr.isAvailable || capture.triggerMatched
            if (shouldEmit && finalWindow.mergedPcm().isNotEmpty()) {
                onWindowCaptured?.invoke(finalWindow)
            }
            onStatusChanged?.invoke("listening")
        }

        fun discardCapture() {
            activeCapture ?: return
            activeCapture = null
            onStatusChanged?.invoke("listening")
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
                if (!capture.triggerMatched && gateAsr.isAvailable) {
                    capture.postRollRemainingSamples = 0
                } else {
                    capture.postRollRemainingSamples = capture.config.postRollSeconds * capture.config.sampleRateHz
                }
            }
        }

        running = true
        audioRecord.startRecording()
        onStatusChanged?.invoke("listening")
        Log.i(TAG, "Contextual audio capture started")

        try {
            val buffer = ShortArray(FRAME_SIZE)
            while (running && isActive) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read <= 0) continue

                vad.processFrame(buffer, read)

                activeCapture?.let { capture ->
                    val chunk = if (read == buffer.size) buffer.copyOf() else buffer.copyOf(read)
                    capture.assembler.appendPostRoll(chunk)
                    capture.capturedSamples += read

                    val maxSamples = MAX_SPEECH_SECONDS * capture.config.sampleRateHz
                    if (capture.capturedSamples >= maxSamples) {
                        if (gateAsr.isAvailable && !capture.triggerMatched) {
                            discardCapture()
                        } else {
                            finalizeCapture()
                        }
                    } else if (capture.postRollRemainingSamples >= 0) {
                        capture.postRollRemainingSamples -= read
                        if (capture.postRollRemainingSamples <= 0) {
                            if (gateAsr.isAvailable && !capture.triggerMatched) {
                                val gateWindow = capture.assembler.finalizeWindow(capture.preRollWindow)
                                val transcript = runCatching { transcribeWithGate(gateWindow) }
                                    .getOrNull()
                                    ?.trim()
                                    .orEmpty()
                                capture.gateTranscript = transcript
                                if (transcript.isNotBlank() && triggerDetector(transcript)) {
                                    capture.triggerMatched = true
                                    onGateMatch?.invoke(transcript)
                                    onStatusChanged?.invoke("trigger_detected")
                                    capture.postRollRemainingSamples = capture.config.postRollSeconds * capture.config.sampleRateHz
                                } else {
                                    discardCapture()
                                }
                            } else {
                                finalizeCapture()
                            }
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
            preRollSeconds = raw.preRollSeconds.coerceIn(1, 10),
            postRollSeconds = raw.postRollSeconds.coerceIn(1, 15),
            sampleRateHz = raw.sampleRateHz.coerceAtLeast(8_000),
            silenceStopMs = raw.silenceStopMs.coerceAtLeast(250L)
        )
    }

    private suspend fun transcribeWithGate(window: CapturedAudioWindow): String? {
        return gateAsr.transcribe(window, languageTag = "es")
    }
}
