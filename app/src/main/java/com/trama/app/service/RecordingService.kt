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
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.Recording
import com.trama.shared.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
        private const val MAX_RECORDING_DURATION_MS = 2L * 60L * 60L * 1000L
    }

    private var fullText = ""
    private var currentPartial = ""
    private var startTimeMs = 0L
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

        // Pause keyword listener to avoid mic conflict (native crash)
        if (ServiceController.isRunning.value) {
            Log.i(TAG, "Pausing KeywordListenerService for recording")
            ServiceController.stopByWatch(this)  // stops without clearing user pref
        }

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
            val window = activeCapture.capture(maxDurationMs = MAX_RECORDING_DURATION_MS)
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
            recording = true,
            elapsed = ((System.currentTimeMillis() - startTimeMs) / 1000),
            text = fullText,
            partial = currentPartial
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
        window: com.trama.shared.audio.CapturedAudioWindow,
        elapsed: Int
    ) {
        try {
            val startedAt = System.currentTimeMillis()
            val transcript = asrEngine.transcribe(window, languageTag = "es")?.text?.trim().orEmpty()
            val decodeMs = System.currentTimeMillis() - startedAt
            CaptureLog.event(
                gate = CaptureLog.Gate.RECORDING,
                result = if (transcript.isNotBlank()) CaptureLog.Result.OK else CaptureLog.Result.NO_MATCH,
                text = transcript.ifBlank { "empty_offline_transcript" },
                meta = mapOf(
                    "engine" to asrEngine.name,
                    "windowMs" to window.durationMs(),
                    "decodeMs" to decodeMs,
                    "offline" to true
                )
            )
            if (transcript.isBlank()) {
                RecordingState.notifyError("La transcripción local salió vacía")
                return
            }

            fullText = transcript
            currentPartial = ""
            RecordingState.update(false, elapsed.toLong(), transcript, "")

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
            Log.i(TAG, "Offline recording saved (id=$recordingId, ${elapsed}s, decode=${decodeMs}ms)")

            com.trama.app.summary.RecordingProcessorWorker.enqueue(applicationContext, recordingId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to transcribe/persist offline recording", e)
            RecordingState.notifyError("No se pudo transcribir la grabación local")
        } finally {
            finishAfterStop()
        }
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
