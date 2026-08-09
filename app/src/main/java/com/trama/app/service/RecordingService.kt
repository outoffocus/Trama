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
import com.trama.app.audio.DurablePcmCapture
import com.trama.app.audio.PcmRecordingStorage
import com.trama.app.diagnostics.CaptureLog
import com.trama.app.summary.RecordingTranscriptionWorker
import com.trama.app.summary.RecordingRecoveryWorker
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.Recording
import com.trama.shared.model.RecordingStatus
import com.trama.shared.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service for continuous voice recording + transcription.
 * Runs independently of the UI — the user can navigate freely while recording.
 *
 * Streams local AudioRecord PCM to durable storage and schedules transcription on stop.
 * State is shared via RecordingState singleton.
 */
class RecordingService : LifecycleService() {

    companion object {
        private const val TAG = "RecordingService"
        private const val CHANNEL_ID = NotificationConfig.CHANNEL_RECORDING
        private const val NOTIFICATION_ID = NotificationConfig.ID_RECORDING
        const val ACTION_START = "com.trama.RECORD_START"
        const val ACTION_STOP = "com.trama.RECORD_STOP"
    }

    private var currentPartial = ""
    private var startTimeMs = 0L
    private var maxRecordingDurationMs = 60L * 60L * 1000L  // Default 60 minutes, loaded from settings
    private var capture: DurablePcmCapture? = null
    private var captureJob: Job? = null
    private var timerJob: Job? = null
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
        currentPartial = "Grabando offline"
        startTimeMs = System.currentTimeMillis()

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
                RecordingState.update(true, elapsed, "", currentPartial)
                updateNotification(elapsed)
                delay(1000)
            }
        }

        captureJob = lifecycleScope.launch(Dispatchers.IO) {
            var durableId: Long? = null
            try {
                val repository = DatabaseProvider.getRepository(applicationContext)
                val createdAt = startTimeMs
                val pendingFile = PcmRecordingStorage.createPendingFile(applicationContext, createdAt)
                val id = repository.insertRecording(
                    Recording(
                        transcription = "",
                        durationSeconds = 0,
                        source = Source.PHONE,
                        createdAt = createdAt,
                        processingStatus = RecordingStatus.CAPTURING,
                        audioFilePath = pendingFile.absolutePath
                    )
                )
                if (id <= 0L) {
                    RecordingState.notifyError("No se pudo preparar la grabación")
                    finishAfterStop()
                    return@launch
                }
                durableId = id
                val activeCapture = DurablePcmCapture(applicationContext)
                capture = activeCapture
                if (!isActive) activeCapture.requestStop()
                val result = activeCapture.capture(pendingFile, maxRecordingDurationMs)
                if (isActive) {
                    Log.i(TAG, "Recording reached max duration; stopping")
                    isActive = false
                    timerJob?.cancel()
                }
                if (result == null) {
                    currentPartial = ""
                    RecordingState.notifyError("No se pudo capturar audio")
                    repository.updateRecordingStatus(id, RecordingStatus.FAILED)
                    finishAfterStop()
                    return@launch
                }
                val finalFile = PcmRecordingStorage.finalizePending(pendingFile)
                val elapsed = (result.sampleCount / PcmRecordingStorage.SAMPLE_RATE_HZ)
                    .toInt()
                    .coerceAtLeast(1)
                repository.updateCapturedRecordingAudio(
                    id = id,
                    audioFilePath = finalFile.absolutePath,
                    durationSeconds = elapsed,
                    status = RecordingStatus.TRANSCRIBING
                )
                RecordingState.update(
                    recording = false,
                    elapsed = elapsed.toLong(),
                    text = "",
                    partial = "Transcripción en segundo plano",
                    processing = true
                )
                RecordingState.notifySaved(id)
                RecordingTranscriptionWorker.enqueue(applicationContext, id)
                CaptureLog.event(
                    gate = CaptureLog.Gate.RECORDING,
                    result = CaptureLog.Result.OK,
                    text = "durable_pcm_saved",
                    meta = mapOf(
                        "recordingId" to id,
                        "samples" to result.sampleCount,
                        "durationSeconds" to elapsed,
                        "audioBytes" to finalFile.length()
                    )
                )
                finishAfterStop()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Durable recording capture failed", error)
                isActive = false
                timerJob?.cancel()
                RecordingState.notifyError("La grabación quedó pendiente de recuperación")
                if (durableId != null) RecordingRecoveryWorker.enqueue(applicationContext)
                finishAfterStop()
            }
        }

        Log.i(TAG, "Recording started")
    }

    private fun stopRecording() {
        if (!isActive) return
        isActive = false
        timerJob?.cancel()
        currentPartial = "Transcribiendo offline..."
        RecordingState.update(
            recording = false,
            elapsed = ((System.currentTimeMillis() - startTimeMs) / 1000),
            text = "",
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
        RecordingState.reset()
        super.onDestroy()
    }

    private fun finishAfterStop() {
        RecordingState.reset()

        if (ServiceController.shouldBeRunning(this@RecordingService)) {
            Log.i(TAG, "Resuming KeywordListenerService after recording")
            ServiceController.start(this@RecordingService)
        }

        capture = null
        captureJob = null
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
