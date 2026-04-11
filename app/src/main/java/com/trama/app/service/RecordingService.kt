package com.trama.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.trama.app.NotificationConfig
import com.trama.app.R
import com.trama.shared.data.DatabaseProvider
import com.trama.app.MainActivity
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
 * Uses SpeechRecognizer in continuous mode (restart on each session end).
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

    private var recognizer: SpeechRecognizer? = null
    private var fullText = ""
    private var currentPartial = ""
    private var startTimeMs = 0L
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
        currentPartial = ""
        startTimeMs = System.currentTimeMillis()

        RecordingState.update(true, 0, "", "")

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

        // Start speech recognition
        val rec = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = rec
        startSession(rec)

        Log.i(TAG, "Recording started")
    }

    private fun stopRecording() {
        if (!isActive || stopJob != null) return
        isActive = false
        timerJob?.cancel()

        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null

        val textToSave = buildString {
            append(fullText)
            if (currentPartial.isNotBlank()) {
                if (isNotBlank()) append(". ")
                append(currentPartial)
            }
        }.trim()

        val elapsed = ((System.currentTimeMillis() - startTimeMs) / 1000).toInt()

        stopJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (textToSave.isNotBlank()) {
                    val repository = DatabaseProvider.getRepository(applicationContext)
                    val recordingId = repository.insertRecording(
                        Recording(
                            transcription = textToSave,
                            durationSeconds = elapsed,
                            source = Source.PHONE
                        )
                    )

                    RecordingState.notifySaved(recordingId)
                    Log.i(TAG, "Recording saved (id=$recordingId, ${elapsed}s)")

                    com.trama.app.summary.RecordingProcessorWorker.enqueue(applicationContext, recordingId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist recording before stop", e)
                RecordingState.notifyError("No se pudo guardar la grabación")
            } finally {
                RecordingState.reset()

                if (ServiceController.shouldBeRunning(this@RecordingService)) {
                    Log.i(TAG, "Resuming KeywordListenerService after recording")
                    ServiceController.start(this@RecordingService)
                }

                stopJob = null
                stopSelf()
                Log.i(TAG, "Recording stopped")
            }
        }
    }

    override fun onDestroy() {
        isActive = false
        timerJob?.cancel()
        stopJob?.cancel()
        recognizer?.destroy()
        RecordingState.reset()
        super.onDestroy()
    }

    // ── SpeechRecognizer ──

    private fun buildRecognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
        }

    private fun startSession(rec: SpeechRecognizer) {
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim() ?: ""
                if (text.isNotBlank()) {
                    fullText = if (fullText.isBlank()) text else "$fullText. $text"
                }
                currentPartial = ""
                if (isActive) {
                    try { rec.startListening(buildRecognizerIntent()) }
                    catch (e: Exception) { Log.w(TAG, "Restart failed", e) }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim() ?: ""
                if (text.isNotBlank()) currentPartial = text
            }

            override fun onError(error: Int) {
                Log.w(TAG, "Recognition error: $error")
                currentPartial = ""
                if (isActive && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        delay(500)
                        if (isActive) {
                            try { rec.startListening(buildRecognizerIntent()) }
                            catch (e: Exception) { Log.w(TAG, "Restart failed", e) }
                        }
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        rec.startListening(buildRecognizerIntent())
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
