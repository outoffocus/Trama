package com.mydiary.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.mydiary.app.MainActivity
import com.mydiary.app.R
import com.mydiary.app.speech.AudioRecorder
import com.mydiary.app.speech.GeminiProcessor
import com.mydiary.app.speech.PersonalDictionary
import com.mydiary.app.speech.SpeechEngine
import com.mydiary.app.speech.VoskModelManager
import com.mydiary.app.ui.DatabaseProvider
import com.mydiary.app.ui.SettingsDataStore
import com.mydiary.shared.data.DiaryRepository
import com.mydiary.shared.model.CategoryInfo
import com.mydiary.shared.model.DiaryEntry
import com.mydiary.shared.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class KeywordListenerService : LifecycleService() {

    companion object {
        private const val TAG = "KeywordListenerService"
        private const val CHANNEL_ID = "mydiary_listener"
        private const val NOTIFICATION_ID = 1
        private const val NEW_ENTRY_CHANNEL_ID = "mydiary_new_entry"
    }

    private var audioRecorder: AudioRecorder? = null
    private var speechEngine: SpeechEngine? = null
    private var audioJob: Job? = null
    private var repository: DiaryRepository? = null
    private lateinit var settings: SettingsDataStore
    private lateinit var dictionary: PersonalDictionary
    private var gemini: GeminiProcessor? = null
    private var geminiEnabled = false

    private var keywords = listOf("recordar", "nota", "destacar", "pendiente")
    private var keywordCategoryMap = mapOf(
        "recordar" to "TODO",
        "nota" to "NOTE",
        "destacar" to "HIGHLIGHT",
        "pendiente" to "REMINDER"
    )
    private var categoryList = CategoryInfo.DEFAULTS
    private var recordingDurationSec = 10

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        initDatabase()
        settings = SettingsDataStore(applicationContext)
        dictionary = PersonalDictionary(applicationContext)
        loadSettings()
        initGemini()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

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
        audioRecorder?.stop()
        audioRecorder = null
        stopAudioLoop()
        speechEngine?.close()
        speechEngine = null
        gemini?.close()
        gemini = null
        ServiceController.notifyStopped()
        super.onDestroy()
    }

    private fun loadSettings() {
        runBlocking {
            recordingDurationSec = settings.recordingDuration.first()
            keywordCategoryMap = settings.keywordMappings.first()
            keywords = keywordCategoryMap.keys.toList()
            categoryList = settings.categories.first()
            geminiEnabled = settings.geminiEnabled.first()
        }
    }

    private fun initGemini() {
        if (!geminiEnabled) return
        lifecycleScope.launch(Dispatchers.IO) {
            val processor = GeminiProcessor()
            if (processor.initialize()) {
                gemini = processor
                Log.i(TAG, "Gemini Nano initialized successfully")
            } else {
                Log.w(TAG, "Gemini Nano not available on this device")
            }
        }
    }

    private fun initDatabase() {
        repository = DatabaseProvider.getRepository(applicationContext)
    }

    private fun loadModelAndStart() {
        if (audioJob != null) {
            Log.w(TAG, "Audio loop already running, skipping start")
            return
        }
        VoskModelManager.load(
            context = this,
            onReady = { model ->
                speechEngine = SpeechEngine(model, keywords).apply {
                    setRecordingDuration(recordingDurationSec)
                }
                startAudioLoop()
            },
            onError = { e ->
                Log.e(TAG, "Failed to load model", e)
                updateNotification("Error: no se pudo cargar el modelo")
                stopSelf()
            }
        )
    }

    private fun startAudioLoop() {
        audioRecorder = AudioRecorder()
        if (!audioRecorder!!.start()) {
            Log.e(TAG, "Failed to start audio recorder")
            updateNotification("Error: no se pudo acceder al micrófono")
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

            repeat(3) {
                if (!isActive) return@launch
                recorder.read(buffer)
            }

            while (isActive) {
                val bytesRead = recorder.read(buffer)
                if (bytesRead <= 0) continue

                if (++frameCount % 3600 == 0) {
                    if (isBatteryLow()) {
                        Log.w(TAG, "Battery low, stopping service")
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
                    vibrate(longArrayOf(0, 100, 50, 100))
                } else if (wasRecording && !engine.isRecording) {
                    updateNotification("Escuchando...")
                    vibrate(longArrayOf(0, 200))
                }

                if (entry != null) {
                    saveEntry(entry.keyword, entry.text, entry.confidence)
                }
            }
        }
    }

    private fun vibrate(pattern: LongArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VibratorManager::class.java)
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createWaveform(pattern, -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Vibrator::class.java)
                v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed", e)
        }
    }

    private fun saveEntry(keyword: String, text: String, confidence: Float) {
        lifecycleScope.launch(Dispatchers.IO) {
            val rawText = if (text.startsWith(keyword, ignoreCase = true)) text else "$keyword $text"

            // Step 1: Apply personal dictionary corrections
            var fullText = dictionary.correct(rawText)

            // Step 2: If Gemini available, also correct with AI
            val gp = gemini
            if (gp != null) {
                fullText = gp.correctText(fullText)
            }

            // Step 3: Categorize — Gemini if available, otherwise keyword mapping
            val categoryId = if (gp != null) {
                gp.categorize(fullText, categoryList) ?: keywordCategoryMap[keyword] ?: "NOTE"
            } else {
                keywordCategoryMap[keyword] ?: "NOTE"
            }

            val entry = DiaryEntry(
                text = fullText,
                keyword = keyword,
                category = categoryId,
                confidence = confidence,
                source = Source.PHONE,
                duration = recordingDurationSec
            )
            repository?.insert(entry)
            Log.i(TAG, "Entry saved: $fullText (category: ${entry.category}, gemini: ${gp != null})")
            showNewEntryNotification(entry)
        }
    }

    private fun stopAudioLoop() {
        audioJob?.cancel()
        audioJob = null
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val listenerChannel = NotificationChannel(
            CHANNEL_ID,
            "Servicio de escucha",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Muestra el estado del servicio de escucha de palabras clave"
        }
        manager.createNotificationChannel(listenerChannel)

        val entryChannel = NotificationChannel(
            NEW_ENTRY_CHANNEL_ID,
            "Nuevas entradas",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notificaciones cuando se captura una nueva entrada"
        }
        manager.createNotificationChannel(entryChannel)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
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
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun showNewEntryNotification(entry: DiaryEntry) {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val categoryLabel = categoryList.find { it.id == entry.category }?.label ?: entry.category

        val notification = NotificationCompat.Builder(this, NEW_ENTRY_CHANNEL_ID)
            .setContentTitle("Nueva entrada: $categoryLabel")
            .setContentText(entry.text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(entry.id.toInt() + 1000, notification)
    }

    private fun isBatteryLow(): Boolean {
        val bm = getSystemService(BatteryManager::class.java)
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return level in 1 until 15
    }
}
