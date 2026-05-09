package com.trama.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.trama.app.NotificationConfig
import com.trama.app.R
import com.trama.app.MainActivity
import com.trama.app.audio.OfflineDictationCapture
import com.trama.app.audio.SherpaWhisperAsrEngine
import com.trama.app.diagnostics.CaptureLog
import com.trama.app.summary.AsrHallucinationDetector
import com.trama.shared.audio.CapturedAudioWindow
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.Recording
import com.trama.shared.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Foreground service for continuous voice recording + transcription.
 * Runs independently of the UI — the user can navigate freely while recording.
 *
 * Uses local AudioRecord capture + offline Whisper transcription on stop.
 * State is shared via RecordingState singleton.
 */
class RecordingService : LifecycleService() {

    companion object {
        private const val TAG = "RecordingService"
        private const val CHANNEL_ID = NotificationConfig.CHANNEL_RECORDING
        private const val NOTIFICATION_ID = NotificationConfig.ID_RECORDING
        const val ACTION_START = "com.trama.RECORD_START"
        const val ACTION_STOP = "com.trama.RECORD_STOP"
        private const val ASR_CHUNK_MS = 25_000L
        private const val MIN_CHUNK_MS = 700L
    }

    private var fullText = ""
    private var currentPartial = ""
    private var startTimeMs = 0L
    private var maxRecordingDurationMs = 60L * 60L * 1000L  // Default 60 minutes, loaded from settings
    private var capture: OfflineDictationCapture? = null
    private var captureJob: Job? = null
    private var timerJob: Job? = null
    private var stopJob: Job? = null
    private var isActive = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, buildNotification(0),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(0))
        }

        // Load recording duration setting (in minutes)
        lifecycleScope.launch(Dispatchers.IO) {
            val settings = com.trama.app.ui.SettingsDataStore(applicationContext)
            val durationMinutes = settings.recordingDuration.first()
            maxRecordingDurationMs = durationMinutes * 60L * 1000L
            Log.i(TAG, "Loaded manual recording duration limit: ${durationMinutes} minutes (${maxRecordingDurationMs}ms)")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
        }

        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (isActive) return
        isActive = true
        fullText = ""
        currentPartial = "Grabando offline"
        startTimeMs = System.currentTimeMillis()

        val asrEngine = SherpaWhisperAsrEngine(applicationContext)
        if (!asrEngine.isAvailable) {
            isActive = false
            currentPartial = ""
            RecordingState.notifyError("ASR local no disponible")
            CaptureLog.event(
                gate = CaptureLog.Gate.RECORDING,
                result = CaptureLog.Result.REJECT,
                text = "offline_asr_unavailable"
            )
            stopSelf()
            return
        }

        RecordingState.update(true, 0, "", currentPartial)

        // Pause keyword listener to avoid mic conflict (native crash).
        // ServiceController also checks the persisted user intent, because this
        // service can be started after the in-memory StateFlow was recreated.
        Log.i(TAG, "Pausing KeywordListenerService for recording if needed")
        ServiceController.pauseListeningForRecording(this)

        // Start timer
        timerJob = lifecycleScope.launch {
            while (isActive) {
                val elapsed = (System.currentTimeMillis() - startTimeMs) / 1000
                RecordingState.update(true, elapsed, fullText, currentPartial)
                updateNotification(elapsed)
                delay(1000)
            }
        }

        val activeCapture = OfflineDictationCapture()
        capture = activeCapture
        captureJob = lifecycleScope.launch(Dispatchers.IO) {
            val window = activeCapture.capture(maxDurationMs = maxRecordingDurationMs)
            if (isActive) {
                Log.i(TAG, "Recording reached max duration; stopping")
                isActive = false
                timerJob?.cancel()
            }
            if (window == null) {
                currentPartial = ""
                RecordingState.notifyError("No se pudo capturar audio")
                finishAfterStop()
                return@launch
            }
            stopJob = lifecycleScope.launch(Dispatchers.IO) {
                persistCapturedRecording(
                    asrEngine = asrEngine,
                    window = window,
                    elapsed = ((System.currentTimeMillis() - startTimeMs) / 1000).toInt()
                )
            }
        }

        Log.i(TAG, "Recording started")
    }

    private fun stopRecording() {
        if (!isActive || stopJob != null) return
        isActive = false
        timerJob?.cancel()
        currentPartial = "Transcribiendo offline..."
        RecordingState.update(
            recording = false,
            elapsed = ((System.currentTimeMillis() - startTimeMs) / 1000),
            text = fullText,
            partial = currentPartial,
            processing = true
        )
        capture?.requestStop()
    }

    override fun onDestroy() {
        isActive = false
        timerJob?.cancel()
        capture?.requestStop()
        captureJob?.cancel()
        if (stopJob == null) {
            RecordingState.reset()
        }
        super.onDestroy()
    }

    private suspend fun persistCapturedRecording(
        asrEngine: SherpaWhisperAsrEngine,
        window: CapturedAudioWindow,
        elapsed: Int
    ) {
        try {
            val result = transcribeInChunks(asrEngine, window)
            val transcript = result.text
            CaptureLog.event(
                gate = CaptureLog.Gate.RECORDING,
                result = if (transcript.isNotBlank() && result.rejectReason == null) {
                    CaptureLog.Result.OK
                } else {
                    CaptureLog.Result.NO_MATCH
                },
                text = when {
                    transcript.isBlank() -> "empty_offline_transcript"
                    result.rejectReason != null -> "offline_asr_hallucination"
                    else -> transcript
                },
                meta = mapOf(
                    "engine" to asrEngine.name,
                    "windowMs" to window.durationMs(),
                    "decodeMs" to result.totalDecodeMs,
                    "offline" to true,
                    "transcriptPreview" to transcript.take(120),
                    "rejectReason" to (result.rejectReason ?: ""),
                    "chunks" to result.chunkCount,
                    "acceptedChunks" to result.acceptedChunks,
                    "rejectedChunks" to result.rejectedChunks
                )
            )
            if (transcript.isBlank()) {
                RecordingState.notifyError("La transcripción local salió vacía")
                return
            }
            if (result.rejectReason != null) {
                RecordingState.notifyError("No he detectado voz útil en la grabación")
                return
            }

            fullText = transcript
            currentPartial = ""
            RecordingState.update(
                recording = false,
                elapsed = elapsed.toLong(),
                text = transcript,
                partial = "",
                processing = false
            )

            val repository = DatabaseProvider.getRepository(applicationContext)
            val recordingId = repository.insertRecording(
                Recording(
                    transcription = transcript,
                    durationSeconds = elapsed,
                    source = Source.PHONE,
                    processedLocally = true,
                    processedBy = asrEngine.name
                )
            )

            RecordingState.notifySaved(recordingId)
            Log.i(
                TAG,
                "Offline recording saved (id=$recordingId, ${elapsed}s, " +
                    "chunks=${result.acceptedChunks}/${result.chunkCount}, decode=${result.totalDecodeMs}ms)"
            )

            com.trama.app.summary.RecordingProcessorWorker.enqueue(applicationContext, recordingId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to transcribe/persist offline recording", e)
            RecordingState.notifyError("No se pudo transcribir la grabación local")
        } finally {
            finishAfterStop()
        }
    }

    private data class RecordingTranscriptionResult(
        val text: String,
        val totalDecodeMs: Long,
        val chunkCount: Int,
        val acceptedChunks: Int,
        val rejectedChunks: Int,
        val rejectReason: String?
    )

    private suspend fun transcribeInChunks(
        asrEngine: SherpaWhisperAsrEngine,
        window: CapturedAudioWindow
    ): RecordingTranscriptionResult {
        val chunks = splitWindow(window, ASR_CHUNK_MS)
        var totalDecodeMs = 0L
        var acceptedChunks = 0
        var rejectedChunks = 0
        val acceptedText = mutableListOf<String>()
        val rejectReasons = mutableListOf<String>()

        chunks.forEachIndexed { index, chunk ->
            RecordingState.update(
                recording = false,
                elapsed = ((System.currentTimeMillis() - startTimeMs) / 1000),
                text = acceptedText.joinToString(" "),
                partial = "Transcribiendo ${index + 1}/${chunks.size}",
                processing = true
            )

            val stats = audioStats(chunk.mergedPcm())
            val startedAt = System.currentTimeMillis()
            val text = asrEngine.transcribe(chunk, languageTag = "es")?.text?.trim().orEmpty()
            val decodeMs = System.currentTimeMillis() - startedAt
            totalDecodeMs += decodeMs

            val hallucinationReason = AsrHallucinationDetector.detect(
                text,
                singleWordIsHallucination = false
            )
            val rejectReason = when {
                text.isBlank() -> "blank"
                hallucinationReason != null -> hallucinationReason
                else -> null
            }

            if (rejectReason == null) {
                acceptedChunks += 1
                acceptedText += text
            } else {
                rejectedChunks += 1
                rejectReasons += rejectReason
            }

            CaptureLog.event(
                gate = CaptureLog.Gate.RECORDING,
                result = if (rejectReason == null) CaptureLog.Result.OK else CaptureLog.Result.NO_MATCH,
                text = if (rejectReason == null) text else "offline_asr_chunk_rejected",
                meta = mapOf(
                    "engine" to asrEngine.name,
                    "chunkIndex" to index,
                    "chunkCount" to chunks.size,
                    "windowMs" to chunk.durationMs(),
                    "decodeMs" to decodeMs,
                    "transcriptPreview" to text.take(120),
                    "rejectReason" to (rejectReason ?: ""),
                    "rms" to "%.1f".format(stats.rms),
                    "peak" to stats.peak,
                    "nonZeroRatio" to "%.3f".format(stats.nonZeroRatio)
                )
            )
        }

        val text = acceptedText.joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return RecordingTranscriptionResult(
            text = text,
            totalDecodeMs = totalDecodeMs,
            chunkCount = chunks.size,
            acceptedChunks = acceptedChunks,
            rejectedChunks = rejectedChunks,
            rejectReason = if (text.isBlank()) {
                rejectReasons.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "no_chunks"
            } else {
                null
            }
        )
    }

    private fun splitWindow(window: CapturedAudioWindow, chunkMs: Long): List<CapturedAudioWindow> {
        val pcm = window.mergedPcm()
        if (pcm.isEmpty() || window.sampleRateHz <= 0) return emptyList()
        val chunkSamples = ((chunkMs * window.sampleRateHz) / 1000L).toInt().coerceAtLeast(1)
        val minSamples = ((MIN_CHUNK_MS * window.sampleRateHz) / 1000L).toInt().coerceAtLeast(1)
        return buildList {
            var start = 0
            while (start < pcm.size) {
                val end = minOf(start + chunkSamples, pcm.size)
                if (end - start >= minSamples || isEmpty()) {
                    add(
                        CapturedAudioWindow(
                            preRollPcm = shortArrayOf(),
                            livePcm = pcm.copyOfRange(start, end),
                            sampleRateHz = window.sampleRateHz
                        )
                    )
                }
                start = end
            }
        }
    }

    private data class AudioStats(
        val rms: Double,
        val peak: Int,
        val nonZeroRatio: Double
    )

    private fun audioStats(pcm: ShortArray): AudioStats {
        if (pcm.isEmpty()) return AudioStats(rms = 0.0, peak = 0, nonZeroRatio = 0.0)
        var sumSquares = 0.0
        var peak = 0
        var nonZero = 0
        pcm.forEach { sample ->
            val value = sample.toInt()
            val magnitude = abs(value)
            if (magnitude > peak) peak = magnitude
            if (value != 0) nonZero += 1
            sumSquares += value.toDouble() * value.toDouble()
        }
        return AudioStats(
            rms = sqrt(sumSquares / pcm.size),
            peak = peak,
            nonZeroRatio = nonZero.toDouble() / pcm.size
        )
    }

    private fun finishAfterStop() {
        RecordingState.reset()

        if (ServiceController.shouldBeRunning(this@RecordingService)) {
            Log.i(TAG, "Resuming KeywordListenerService after recording")
            ServiceController.start(this@RecordingService)
        }

        capture = null
        captureJob = null
        stopJob = null
        stopSelf()
        Log.i(TAG, "Recording stopped")
    }

    // ── Notification ──

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Grabación",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Grabación de voz activa" }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(elapsedSeconds: Long): Notification {
        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60
        val timeStr = "%02d:%02d".format(minutes, seconds)

        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Grabando $timeStr")
            .setContentText("Toca para abrir · Parar desde aquí")
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_media_pause, "Parar", stopPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(elapsedSeconds: Long) {
        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, buildNotification(elapsedSeconds))
    }
}
