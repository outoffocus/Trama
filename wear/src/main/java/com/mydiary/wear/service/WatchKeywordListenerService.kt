package com.mydiary.wear.service

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
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.mydiary.wear.R
import com.mydiary.wear.ui.WatchMainActivity
import com.mydiary.shared.data.DiaryRepository
import com.mydiary.shared.model.DiaryEntry
import com.mydiary.shared.model.Source
import com.mydiary.wear.sync.MicCoordinator
import com.mydiary.wear.sync.WatchToPhoneSyncer
import com.mydiary.wear.ui.DatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Watch keyword listener using Android SpeechRecognizer in continuous loop.
 * Same architecture as the phone app — single engine, multilingual, no Vosk.
 *
 * Loads keyword mappings from SharedPreferences (synced from phone).
 * Syncs captured entries to the phone after each save.
 */
class WatchKeywordListenerService : LifecycleService() {

    companion object {
        private const val TAG = "WatchListenerService"
        private const val CHANNEL_ID = "mydiary_watch_listener"
        private const val NOTIFICATION_ID = 1
        private const val LOW_BATTERY_THRESHOLD = 20

        private const val RESTART_DELAY_FAST_MS = 200L
        private const val RESTART_DELAY_NORMAL_MS = 500L
        private const val RESTART_DELAY_SLOW_MS = 1500L
        private const val ERROR_RETRY_DELAY_MS = 1000L
        private const val SLOW_MODE_THRESHOLD = 15

        private const val PREFS = "watch_sync_prefs"
    }

    private var recognizer: SpeechRecognizer? = null
    private var repository: DiaryRepository? = null
    private var syncer: WatchToPhoneSyncer? = null

    @Volatile
    private var listening = false
    private var consecutiveSilent = 0
    private var batteryCheckCounter = 0
    private var lastNotificationText = ""
    private var useSimpleIntent = false  // Fallback if language not supported

    private var keywords = listOf("recordar", "nota", "destacar", "pendiente")
    private var keywordCategoryMap = mapOf(
        "recordar" to "TODO",
        "nota" to "NOTE",
        "destacar" to "HIGHLIGHT",
        "pendiente" to "REMINDER"
    )

    /** Broadcast receiver for settings updates from PhoneToWatchReceiver */
    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.mydiary.wear.SETTINGS_UPDATED") {
                loadKeywordsFromPrefs()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initDatabase()
        loadKeywordsFromPrefs()
        registerReceiver(settingsReceiver, IntentFilter("com.mydiary.wear.SETTINGS_UPDATED"),
            Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // Don't start if phone is actively listening
        if (WatchServiceController.isPhoneActive(applicationContext)) {
            Log.i(TAG, "Phone is active, not starting watch listener")
            stopSelf()
            return START_NOT_STICKY
        }

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

        initRecognizerAndStart()

        // Tell phone to pause — watch is listening
        lifecycleScope.launch(Dispatchers.IO) { MicCoordinator.sendPause(applicationContext) }

        return START_STICKY
    }

    override fun onDestroy() {
        listening = false
        try {
            unregisterReceiver(settingsReceiver)
        } catch (_: Exception) {}
        recognizer?.destroy()
        recognizer = null

        // Tell phone it can resume listening
        lifecycleScope.launch(Dispatchers.IO) { MicCoordinator.sendResume(applicationContext) }

        WatchServiceController.notifyStopped()
        super.onDestroy()
    }

    /**
     * Load keyword→category mappings from SharedPreferences (synced from phone).
     * Falls back to defaults if nothing synced yet.
     */
    private fun loadKeywordsFromPrefs() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val mappingsStr = prefs.getString("keyword_mappings", null)
        if (mappingsStr != null) {
            val parsed = mappingsStr.split(",").mapNotNull { pair ->
                val parts = pair.trim().split(":")
                if (parts.size == 2) parts[0].trim().lowercase() to parts[1].trim()
                else null
            }.toMap()
            if (parsed.isNotEmpty()) {
                keywordCategoryMap = parsed
                keywords = parsed.keys.toList()
                Log.i(TAG, "Keywords loaded from sync: $keywords")
            }
        }
    }

    private fun initDatabase() {
        repository = DatabaseProvider.getRepository(applicationContext)
        syncer = repository?.let { WatchToPhoneSyncer(applicationContext, it) }
    }

    private fun initRecognizerAndStart() {
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                // On Wear OS, prefer standard recognizer — it routes through the phone
                // via Bluetooth and has better language support than on-device.
                recognizer = if (SpeechRecognizer.isRecognitionAvailable(applicationContext)) {
                    SpeechRecognizer.createSpeechRecognizer(applicationContext).also {
                        Log.i(TAG, "Standard SpeechRecognizer created (via phone proxy)")
                    }
                } else {
                    Log.e(TAG, "No SpeechRecognizer available")
                    updateNotificationIfChanged("Error: sin reconocimiento")
                    stopSelf()
                    return@launch
                }

                listening = true
                startListening()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create SpeechRecognizer", e)
                updateNotificationIfChanged("Error")
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
                consecutiveSilent = 0
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
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    SpeechRecognizer.ERROR_CLIENT -> {
                        restartListening(ERROR_RETRY_DELAY_MS)
                    }
                    12 /* ERROR_LANGUAGE_NOT_SUPPORTED */ -> {
                        if (!useSimpleIntent) {
                            Log.w(TAG, "Language not supported, falling back to simple intent")
                            useSimpleIntent = true
                            restartListening(RESTART_DELAY_FAST_MS)
                        } else {
                            Log.e(TAG, "Language still not supported with simple intent")
                            restartListening(ERROR_RETRY_DELAY_MS)
                        }
                    }
                    else -> {
                        Log.e(TAG, "Recognition error: $error")
                        restartListening(ERROR_RETRY_DELAY_MS)
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: ""

                consecutiveSilent = 0

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
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

            if (useSimpleIntent) {
                // Fallback: device default language, no extras
                Log.d(TAG, "Using simple recognizer intent (device default language)")
            } else {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")

                if (Build.VERSION.SDK_INT >= 34) {
                    putExtra("android.speech.extra.ENABLE_LANGUAGE_SWITCH", true)
                    putExtra(
                        "android.speech.extra.LANGUAGE_SWITCH_ALLOWED_LANGUAGES",
                        arrayListOf("es-ES", "en-US")
                    )
                }
                // No PREFER_OFFLINE on watch — routes through phone for better support
            }
        }
    }

    private fun adaptiveDelay(): Long {
        return if (consecutiveSilent >= SLOW_MODE_THRESHOLD) RESTART_DELAY_SLOW_MS
        else RESTART_DELAY_NORMAL_MS
    }

    private fun restartListening(delayMs: Long) {
        if (!listening) return

        if (++batteryCheckCounter % 50 == 0) {
            if (isBatteryLow()) {
                Log.w(TAG, "Battery low, stopping")
                updateNotificationIfChanged("Batería baja")
                listening = false
                stopSelf()
                return
            }
        }

        lifecycleScope.launch(Dispatchers.Main) {
            delay(delayMs)
            if (listening) startListening()
        }
    }

    private fun processText(text: String) {
        val lowerText = text.lowercase()
        val keyword = keywords.firstOrNull { lowerText.contains(it) } ?: return

        Log.i(TAG, "Keyword '$keyword' found in: '$text'")
        saveEntry(keyword, text, 0.9f)
    }

    private fun saveEntry(keyword: String, text: String, confidence: Float) {
        lifecycleScope.launch(Dispatchers.IO) {
            val categoryId = keywordCategoryMap[keyword.lowercase()] ?: "NOTE"

            val entry = DiaryEntry(
                text = text,
                keyword = keyword,
                category = categoryId,
                confidence = confidence,
                source = Source.WATCH,
                duration = 0
            )
            repository?.insert(entry)
            Log.i(TAG, "Entry saved: '$text'")

            // Sync entry to phone
            syncer?.syncUnsentEntries()
        }
    }

    private fun isBatteryLow(): Boolean {
        val bm = getSystemService(BatteryManager::class.java)
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return level in 1 until LOW_BATTERY_THRESHOLD
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Servicio de escucha", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, WatchMainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MyDiary")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotificationIfChanged(text: String) {
        if (text == lastNotificationText) return
        lastNotificationText = text
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }
}
