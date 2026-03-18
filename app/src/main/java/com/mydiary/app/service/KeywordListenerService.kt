package com.mydiary.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.mydiary.app.MainActivity
import com.mydiary.app.R
import com.mydiary.app.speech.PersonalDictionary
import com.mydiary.app.sync.MicCoordinator
import com.mydiary.app.sync.PhoneToWatchSyncer
import com.mydiary.app.sync.SettingsSyncer
import com.mydiary.app.ui.DatabaseProvider
import com.mydiary.app.ui.SettingsDataStore
import com.mydiary.shared.model.CategoryInfo
import com.mydiary.shared.model.DiaryEntry
import com.mydiary.shared.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Single-engine speech service using Android SpeechRecognizer in continuous loop.
 *
 * Battery optimizations:
 * - Pauses when screen is off (user can't speak keywords with phone in pocket)
 * - Adaptive restart delay: slows down after consecutive silent cycles
 * - Stops at configurable battery threshold
 * - Reduces notification updates to avoid unnecessary wake-ups
 */
class KeywordListenerService : LifecycleService() {

    companion object {
        private const val TAG = "KeywordListenerService"
        private const val CHANNEL_ID = "mydiary_listener"
        private const val NOTIFICATION_ID = 1
        private const val NEW_ENTRY_CHANNEL_ID = "mydiary_new_entry"

        // Adaptive delays: start fast, slow down if no speech detected
        private const val RESTART_DELAY_FAST_MS = 200L     // After speech detected
        private const val RESTART_DELAY_NORMAL_MS = 500L   // Default
        private const val RESTART_DELAY_SLOW_MS = 1500L    // After many silent cycles
        private const val ERROR_RETRY_DELAY_MS = 1000L

        // After this many consecutive silent cycles, switch to slow mode
        private const val SLOW_MODE_THRESHOLD = 20

        private const val BATTERY_THRESHOLD = 15
    }

    private var recognizer: SpeechRecognizer? = null
    private var repository: com.mydiary.shared.data.DiaryRepository? = null
    private lateinit var settings: SettingsDataStore
    private lateinit var dictionary: PersonalDictionary
    private var phoneToWatchSyncer: PhoneToWatchSyncer? = null
    private lateinit var settingsSyncer: SettingsSyncer

    @Volatile
    private var listening = false

    @Volatile
    private var screenOff = false  // Slow mode when screen off

    private var consecutiveSilent = 0
    private var batteryCheckCounter = 0
    private var lastNotificationText = ""

    private var keywords = listOf("recordar", "nota", "destacar", "pendiente")
    private var keywordCategoryMap = mapOf(
        "recordar" to "TODO",
        "nota" to "NOTE",
        "destacar" to "HIGHLIGHT",
        "pendiente" to "REMINDER"
    )
    private var categoryList = CategoryInfo.DEFAULTS

    // Screen on/off receiver — switch to slow mode when screen off
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.i(TAG, "Screen off → slow mode")
                    screenOff = true
                    consecutiveSilent = SLOW_MODE_THRESHOLD // Force slow mode immediately
                    updateNotificationIfChanged("Escuchando (segundo plano)")
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.i(TAG, "Screen on → fast mode")
                    screenOff = false
                    consecutiveSilent = 0
                    updateNotificationIfChanged("Escuchando...")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        initDatabase()
        settings = SettingsDataStore(applicationContext)
        dictionary = PersonalDictionary(applicationContext)
        settingsSyncer = SettingsSyncer(applicationContext)
        loadSettings()
        observeSettings()
        registerScreenReceiver()
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

        if (!ServiceController.shouldBeRunning(this)) {
            Log.i(TAG, "Service started but user toggled off — stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        initRecognizerAndStart()

        // Tell watch to pause — phone has mic priority
        lifecycleScope.launch(Dispatchers.IO) { MicCoordinator.sendPause(applicationContext) }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        listening = false
        screenOff = false
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
        recognizer?.destroy()
        recognizer = null

        // Tell watch it can resume listening
        lifecycleScope.launch(Dispatchers.IO) { MicCoordinator.sendResume(applicationContext) }

        ServiceController.notifyStopped()
        super.onDestroy()
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)

        // Check current screen state
        val pm = getSystemService(PowerManager::class.java)
        screenOff = !pm.isInteractive
    }

    private fun loadSettings() {
        runBlocking {
            keywordCategoryMap = settings.keywordMappings.first()
            keywords = keywordCategoryMap.keys.toList()
            categoryList = settings.categories.first()
        }
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            settings.keywordMappings.collect { mappings ->
                keywordCategoryMap = mappings
                keywords = mappings.keys.toList()
                Log.i(TAG, "Keywords updated: $keywords")

                // Sync keyword changes to watch
                launch(Dispatchers.IO) { settingsSyncer.syncSettings(mappings) }
            }
        }
        lifecycleScope.launch {
            settings.categories.collect { cats ->
                categoryList = cats
            }
        }
    }

    private fun initDatabase() {
        repository = DatabaseProvider.getRepository(applicationContext)
        phoneToWatchSyncer = repository?.let { PhoneToWatchSyncer(applicationContext, it) }
    }

    private fun initRecognizerAndStart() {
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                recognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(applicationContext)
                ) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(applicationContext).also {
                        Log.i(TAG, "On-device SpeechRecognizer created")
                    }
                } else if (SpeechRecognizer.isRecognitionAvailable(applicationContext)) {
                    SpeechRecognizer.createSpeechRecognizer(applicationContext).also {
                        Log.i(TAG, "Standard SpeechRecognizer created (offline preference)")
                    }
                } else {
                    Log.e(TAG, "No SpeechRecognizer available")
                    updateNotificationIfChanged("Error: reconocimiento no disponible")
                    stopSelf()
                    return@launch
                }

                listening = true
                startListening()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create SpeechRecognizer", e)
                updateNotificationIfChanged("Error: no se pudo iniciar")
                stopSelf()
            }
        }
    }

    private fun startListening() {
        if (!listening) return
        val rec = recognizer ?: return

        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                updateNotificationIfChanged("Escuchando...")
            }

            override fun onBeginningOfSpeech() {
                consecutiveSilent = 0  // Reset: user is speaking
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        consecutiveSilent++
                        restartListening(adaptiveDelay())
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        restartListening(ERROR_RETRY_DELAY_MS)
                    }
                    SpeechRecognizer.ERROR_CLIENT -> {
                        restartListening(ERROR_RETRY_DELAY_MS)
                    }
                    else -> {
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio"
                            SpeechRecognizer.ERROR_NETWORK -> "Network"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissions"
                            else -> "Code $error"
                        }
                        Log.e(TAG, "Recognition error: $msg")
                        restartListening(ERROR_RETRY_DELAY_MS)
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: ""

                consecutiveSilent = 0  // Got a result → reset

                if (text.isNotBlank()) {
                    Log.i(TAG, "Heard: '$text'")
                    processText(text)
                }

                restartListening(RESTART_DELAY_FAST_MS)
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            rec.startListening(buildRecognizerIntent())
        } catch (e: Exception) {
            Log.e(TAG, "startListening failed", e)
            restartListening(ERROR_RETRY_DELAY_MS)
        }
    }

    private fun buildRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")

            if (Build.VERSION.SDK_INT >= 34) {
                putExtra("android.speech.extra.ENABLE_LANGUAGE_SWITCH", true)
                putExtra(
                    "android.speech.extra.LANGUAGE_SWITCH_ALLOWED_LANGUAGES",
                    arrayListOf("es-ES", "en-US")
                )
            }

            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }

    /**
     * Adaptive delay: fast after speech, slower after many silent cycles.
     * Reduces CPU wake-ups when nobody is speaking.
     */
    private fun adaptiveDelay(): Long {
        return when {
            screenOff -> RESTART_DELAY_SLOW_MS  // Screen off → always slow to save battery
            consecutiveSilent >= SLOW_MODE_THRESHOLD -> RESTART_DELAY_SLOW_MS
            else -> RESTART_DELAY_NORMAL_MS
        }
    }

    private fun restartListening(delayMs: Long) {
        if (!listening) return

        // Battery check every ~50 cycles
        if (++batteryCheckCounter % 50 == 0) {
            if (isBatteryLow()) {
                Log.w(TAG, "Battery low, stopping")
                updateNotificationIfChanged("Pausado: batería baja")
                listening = false
                stopSelf()
                return
            }
        }

        lifecycleScope.launch(Dispatchers.Main) {
            delay(delayMs)
            if (listening) {
                startListening()
            }
        }
    }

    private fun processText(text: String) {
        val lowerText = text.lowercase()
        val keyword = keywords.firstOrNull { lowerText.contains(it) } ?: return

        Log.i(TAG, "Keyword '$keyword' found in: '$text'")
        vibrate(longArrayOf(0, 100, 50, 100))
        saveEntry(keyword, text, 0.9f)
    }

    private fun saveEntry(keyword: String, text: String, confidence: Float) {
        lifecycleScope.launch(Dispatchers.IO) {
            val correctedText = dictionary.correct(text)
            val categoryId = keywordCategoryMap[keyword] ?: "NOTE"

            val entry = DiaryEntry(
                text = correctedText,
                keyword = keyword,
                category = categoryId,
                confidence = confidence,
                source = Source.PHONE,
                duration = 0
            )
            repository?.insert(entry)
            Log.i(TAG, "Entry saved: '$correctedText' (category: ${entry.category})")
            showNewEntryNotification(entry)

            // Sync entry to watch
            phoneToWatchSyncer?.syncUnsentEntries()
        }
    }

    private fun vibrate(pattern: LongArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VibratorManager::class.java)
                vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Vibrator::class.java)
                v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed", e)
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Servicio de escucha", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Muestra el estado del servicio de escucha de palabras clave"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(NEW_ENTRY_CHANNEL_ID, "Nuevas entradas", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notificaciones cuando se captura una nueva entrada"
            }
        )
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MyDiary")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    /** Only update notification if text changed — avoids unnecessary system wake-ups */
    private fun updateNotificationIfChanged(text: String) {
        if (text == lastNotificationText) return
        lastNotificationText = text
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun showNewEntryNotification(entry: DiaryEntry) {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
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
        return level in 1 until BATTERY_THRESHOLD
    }
}
