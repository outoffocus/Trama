package com.mydiary.wear.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.mydiary.wear.R
import com.mydiary.wear.speech.AudioRecorder
import com.mydiary.wear.speech.SpeechEngine
import com.mydiary.wear.speech.VoskModelManager
import com.mydiary.wear.ui.WatchMainActivity
import com.mydiary.shared.data.DiaryDatabase
import com.mydiary.shared.data.DiaryRepository
import com.mydiary.shared.model.Category
import com.mydiary.shared.model.DiaryEntry
import com.mydiary.shared.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WatchKeywordListenerService : LifecycleService() {

    companion object {
        private const val TAG = "WatchListenerService"
        private const val CHANNEL_ID = "mydiary_watch_listener"
        private const val NOTIFICATION_ID = 1
        private const val LOW_BATTERY_THRESHOLD = 20
        private const val DEFAULT_RECORDING_DURATION_SEC = 10
    }

    private var audioRecorder: AudioRecorder? = null
    private var speechEngine: SpeechEngine? = null
    private var audioJob: Job? = null
    private var repository: DiaryRepository? = null

    private val defaultKeywords = listOf("recordar", "nota", "destacar", "pendiente")
    private var recordingDurationSec = DEFAULT_RECORDING_DURATION_SEC

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initDatabase()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (isBatteryLow()) {
            Log.w(TAG, "Battery low, not starting service")
            stopSelf()
            return START_NOT_STICKY
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Inicializando..."),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Inicializando..."))
        }

        loadModelAndStart()

        return START_STICKY
    }

    override fun onDestroy() {
        // Order matters: stop recorder first to unblock read(), then cancel job, then close engine
        audioRecorder?.stop()
        audioRecorder = null
        stopAudioLoop()
        speechEngine?.close()
        speechEngine = null
        super.onDestroy()
    }

    private fun initDatabase() {
        val db = Room.databaseBuilder(
            applicationContext,
            DiaryDatabase::class.java,
            "mydiary-database"
        ).build()
        repository = DiaryRepository(db.diaryDao())
    }

    private fun loadModelAndStart() {
        // Guard against double-start
        if (audioJob != null) {
            Log.w(TAG, "Audio loop already running, skipping start")
            return
        }
        VoskModelManager.load(
            context = this,
            onReady = { model ->
                speechEngine = SpeechEngine(model, defaultKeywords).apply {
                    setRecordingDuration(recordingDurationSec)
                }
                startAudioLoop()
            },
            onError = { e ->
                Log.e(TAG, "Failed to load model", e)
                updateNotification("Error: modelo")
                stopSelf()
            }
        )
    }

    private fun startAudioLoop() {
        audioRecorder = AudioRecorder()
        if (!audioRecorder!!.start()) {
            Log.e(TAG, "Failed to start audio recorder")
            stopSelf()
            return
        }

        updateNotification("Escuchando...")

        audioJob = lifecycleScope.launch(Dispatchers.Default) {
            val readSize = 4096
            val buffer = ByteArray(readSize)
            val engine = speechEngine ?: return@launch
            val recorder = audioRecorder ?: return@launch
            var frameCount = 0

            // Discard first 3 reads to let AudioRecord stabilize
            repeat(3) {
                if (!isActive) return@launch
                recorder.read(buffer)
            }

            while (isActive) {
                val bytesRead = recorder.read(buffer)
                if (bytesRead <= 0) continue

                // Check battery every ~30 seconds
                if (++frameCount % 3600 == 0) {
                    if (isBatteryLow()) {
                        Log.w(TAG, "Battery low, pausing service")
                        updateNotification("Pausado: batería baja")
                        stopSelf()
                        return@launch
                    }
                }

                val wasRecording = engine.isRecording
                val entry = try {
                    engine.acceptWaveForm(buffer, bytesRead)
                } catch (e: Exception) {
                    Log.e(TAG, "Vosk processing error", e)
                    null
                }

                if (!wasRecording && engine.isRecording) {
                    updateNotification("Grabando...")
                } else if (wasRecording && !engine.isRecording) {
                    updateNotification("Escuchando...")
                }

                if (entry != null) {
                    saveEntry(entry.keyword, entry.text, entry.confidence)
                }
            }
        }
    }

    private fun saveEntry(keyword: String, text: String, confidence: Float) {
        lifecycleScope.launch(Dispatchers.IO) {
            val fullText = if (text.startsWith(keyword, ignoreCase = true)) text else "$keyword $text"
            val entry = DiaryEntry(
                text = fullText,
                keyword = keyword,
                category = Category.fromKeyword(keyword),
                confidence = confidence,
                source = Source.WATCH,
                duration = recordingDurationSec
            )
            repository?.insert(entry)
            Log.i(TAG, "Entry saved: $text")
        }
    }

    private fun stopAudioLoop() {
        audioJob?.cancel()
        audioJob = null
    }

    private fun isBatteryLow(): Boolean {
        val bm = getSystemService(BatteryManager::class.java)
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return level in 1 until LOW_BATTERY_THRESHOLD
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Servicio de escucha",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, WatchMainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MyDiary")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }
}
