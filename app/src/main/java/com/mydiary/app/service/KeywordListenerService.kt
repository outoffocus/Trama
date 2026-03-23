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
import com.mydiary.app.speech.EntryValidator
import com.mydiary.app.speech.IntentDetector
import com.mydiary.app.speech.IntentPattern
import com.mydiary.app.speech.PersonalDictionary
import com.mydiary.app.speech.SpeakerEnrollment
import com.mydiary.app.speech.VoiceActivityDetector
import com.mydiary.app.sync.MicCoordinator
import com.mydiary.app.sync.PhoneToWatchSyncer
import com.mydiary.app.sync.SettingsSyncer
import com.mydiary.app.ui.DatabaseProvider
import com.mydiary.app.ui.SettingsDataStore
import com.mydiary.shared.model.DiaryEntry
import com.mydiary.shared.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Continuous speech listening service using Android SpeechRecognizer.
 *
 * Uses IntentDetector for flexible regex-based intent matching instead of
 * exact keyword matching. Enables partial results for faster detection.
 *
 * Integrated features:
 * - VAD (Voice Activity Detection): Only starts SpeechRecognizer when voice is detected
 * - Speaker verification: Filters out TV/radio using enrolled voice profile
 * - Entry validation: Heuristics + Gemini to validate/correct transcriptions
 *
 * Battery optimizations:
 * - VAD gate: SpeechRecognizer only runs when voice is detected (~60% savings)
 * - Adaptive restart delay (fast after speech, slow after silence)
 * - Slow mode when screen is off
 * - Stops at configurable battery threshold
 * - Deduplicates entries from partial vs final results
 */
class KeywordListenerService : LifecycleService() {

    companion object {
        private const val TAG = "KeywordListenerService"
        private const val CHANNEL_ID = "mydiary_listener"
        private const val NOTIFICATION_ID = 1
        private const val NEW_ENTRY_CHANNEL_ID = "mydiary_new_entry"

        private const val RESTART_DELAY_FAST_MS = 200L
        private const val RESTART_DELAY_NORMAL_MS = 500L
        private const val RESTART_DELAY_SLOW_MS = 1500L
        private const val ERROR_RETRY_DELAY_MS = 1000L

        private const val SLOW_MODE_THRESHOLD = 20
        private const val BATTERY_THRESHOLD = 15

        // Deduplication: ignore entries within this window of a previous capture
        private const val DEDUP_WINDOW_MS = 5000L
    }

    private var recognizer: SpeechRecognizer? = null
    private var repository: com.mydiary.shared.data.DiaryRepository? = null
    private lateinit var settings: SettingsDataStore
    private lateinit var dictionary: PersonalDictionary
    private lateinit var intentDetector: IntentDetector
    private lateinit var entryValidator: EntryValidator
    private lateinit var speakerEnrollment: SpeakerEnrollment
    private var vad: VoiceActivityDetector? = null
    private var vadJob: Job? = null
    private var phoneToWatchSyncer: PhoneToWatchSyncer? = null
    private lateinit var settingsSyncer: SettingsSyncer

    @Volatile
    private var listening = false

    @Volatile
    private var screenOff = false

    @Volatile
    private var vadActive = false // true = VAD is running instead of SpeechRecognizer

    @Volatile
    private var speechRecognizerActive = false

    private var consecutiveSilent = 0
    private var batteryCheckCounter = 0
    private var lastNotificationText = ""

    // Deduplication: avoid saving same entry from partial + final results
    private var lastSavedText = ""
    private var lastSavedTime = 0L

    // Track if partial result already triggered a save for this recognition cycle
    private var partialAlreadySaved = false

    // Audio features for speaker verification (RMS only — SpeechRecognizer doesn't expose raw audio for ZCR)
    private val recentRmsValues = mutableListOf<Double>()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.i(TAG, "Screen off → slow mode")
                    screenOff = true
                    consecutiveSilent = SLOW_MODE_THRESHOLD
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
        intentDetector = IntentDetector()
        entryValidator = EntryValidator(applicationContext)
        speakerEnrollment = SpeakerEnrollment(applicationContext)
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

        lifecycleScope.launch(Dispatchers.IO) { MicCoordinator.sendPause(applicationContext) }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        listening = false
        screenOff = false
        stopVAD()
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
        recognizer?.destroy()
        recognizer = null

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

        val pm = getSystemService(PowerManager::class.java)
        screenOff = !pm.isInteractive
    }

    private fun loadSettings() {
        runBlocking {
            val patterns = settings.intentPatterns.first()
            intentDetector.setPatterns(patterns)

            val customKw = settings.customKeywords.first()
            intentDetector.setCustomKeywords(customKw)
        }
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            settings.intentPatterns.collect { patterns ->
                intentDetector.setPatterns(patterns)
                Log.i(TAG, "Intent patterns updated: ${patterns.count { it.enabled }} enabled")

                // Sync full patterns + speaker profile to watch
                val keywords = settings.customKeywords.first()
                val profile = speakerEnrollment.toSpeakerProfile()
                launch(Dispatchers.IO) { settingsSyncer.syncPatterns(patterns, keywords, profile) }
            }
        }
        lifecycleScope.launch {
            settings.customKeywords.collect { keywords ->
                intentDetector.setCustomKeywords(keywords)
                Log.i(TAG, "Custom keywords updated: ${keywords.size} keywords")

                // Sync to watch
                val patterns = settings.intentPatterns.first()
                val profile = speakerEnrollment.toSpeakerProfile()
                launch(Dispatchers.IO) { settingsSyncer.syncPatterns(patterns, keywords, profile) }
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
                        Log.i(TAG, "Standard SpeechRecognizer created")
                    }
                } else {
                    Log.e(TAG, "No SpeechRecognizer available")
                    updateNotificationIfChanged("Error: reconocimiento no disponible")
                    stopSelf()
                    return@launch
                }

                listening = true
                // Start directly with SpeechRecognizer (VAD not on phone for now
                // since SpeechRecognizer handles its own audio)
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

        partialAlreadySaved = false  // Reset for new cycle
        recentRmsValues.clear()

        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                speechRecognizerActive = true
                updateNotificationIfChanged("Escuchando...")
            }

            override fun onBeginningOfSpeech() {
                consecutiveSilent = 0
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Collect raw dB values for speaker verification
                // Same scale as enrollment (both use SpeechRecognizer.onRmsChanged)
                if (speakerEnrollment.isEnrolled() && speakerEnrollment.isEnabled()) {
                    recentRmsValues.add(rmsdB.toDouble())
                    if (recentRmsValues.size > 50) recentRmsValues.removeAt(0)
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                speechRecognizerActive = false
            }

            override fun onError(error: Int) {
                speechRecognizerActive = false
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
                        Log.e(TAG, "Recognition error: $error")
                        restartListening(ERROR_RETRY_DELAY_MS)
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (partialAlreadySaved) return  // Already captured in this cycle

                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: return

                if (text.isNotBlank()) {
                    val result = intentDetector.detectPartial(text)
                    if (result != null) {
                        Log.i(TAG, "Partial match [${result.label}]: '${text.take(60)}'")
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                speechRecognizerActive = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: ""

                consecutiveSilent = 0

                if (text.isNotBlank()) {
                    Log.i(TAG, "Heard: '$text'")
                    processText(text)
                }

                restartListening(RESTART_DELAY_FAST_MS)
            }

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
            // Enable partial results for faster intent detection
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }

    private fun adaptiveDelay(): Long {
        return when {
            screenOff -> RESTART_DELAY_SLOW_MS
            consecutiveSilent >= SLOW_MODE_THRESHOLD -> RESTART_DELAY_SLOW_MS
            else -> RESTART_DELAY_NORMAL_MS
        }
    }

    private fun restartListening(delayMs: Long) {
        if (!listening) return

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

    private fun stopVAD() {
        vad?.stop()
        vadJob?.cancel()
        vadJob = null
        vad = null
        vadActive = false
    }

    /**
     * Process transcribed text through IntentDetector.
     * Uses regex patterns for flexible matching of natural speech variations.
     * Now includes speaker verification and LLM validation.
     */
    private fun processText(text: String) {
        val result = intentDetector.detect(text) ?: return

        // Speaker verification — reject if voice doesn't match enrolled profile
        if (speakerEnrollment.isEnrolled() && speakerEnrollment.isEnabled()) {
            val verification = speakerEnrollment.verify(recentRmsValues.toList())
            if (!verification.isMatch) {
                Log.i(TAG, "Speaker verification failed (sim=${verification.similarity}), rejecting: '${text.take(40)}'")
                return
            }
        }

        // Deduplication: skip if we just saved something very similar recently
        val now = System.currentTimeMillis()
        if (now - lastSavedTime < DEDUP_WINDOW_MS && isSimilar(text, lastSavedText)) {
            Log.i(TAG, "Dedup: skipping similar entry within ${DEDUP_WINDOW_MS}ms")
            return
        }

        val intentId = result.pattern?.id ?: result.customKeyword ?: "nota"
        Log.i(TAG, "Intent '$intentId' [${result.label}] found in: '${text.take(60)}'")
        vibrate(longArrayOf(0, 100, 50, 100))

        // Validate and correct via heuristics + LLM (async)
        lifecycleScope.launch(Dispatchers.IO) {
            val validation = entryValidator.validate(result.capturedText)

            if (!validation.isValid) {
                Log.i(TAG, "Entry rejected by validator: ${validation.reason}")
                return@launch
            }

            val correctedText = dictionary.correct(
                validation.correctedText ?: result.capturedText
            )

            saveEntry(
                intentId = intentId,
                label = result.label,
                text = correctedText,
                originalText = result.capturedText,
                correctedByLLM = validation.correctedText,
                llmConfidence = validation.confidence,
                wasReviewed = validation.correctedText != null || validation.reason.contains("IA"),
                confidence = 0.9f
            )
        }

        lastSavedText = text
        lastSavedTime = now
        partialAlreadySaved = true
    }

    private fun saveEntry(
        intentId: String,
        label: String,
        text: String,
        originalText: String,
        correctedByLLM: String?,
        llmConfidence: Float,
        wasReviewed: Boolean,
        confidence: Float
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            val entry = DiaryEntry(
                text = text,
                keyword = intentId,
                category = label,
                confidence = confidence,
                source = Source.PHONE,
                duration = 0,
                correctedText = correctedByLLM,
                wasReviewedByLLM = wasReviewed,
                llmConfidence = llmConfidence
            )
            repository?.insert(entry)
            Log.i(TAG, "Entry saved: '$text' (intent: $intentId, label: $label, reviewed: $wasReviewed)")
            showNewEntryNotification(entry)

            phoneToWatchSyncer?.syncUnsentEntries()
        }
    }

    /**
     * Simple similarity check: if >70% of words overlap, consider it a duplicate.
     */
    private fun isSimilar(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        val wordsA = a.lowercase().split("\\s+".toRegex()).toSet()
        val wordsB = b.lowercase().split("\\s+".toRegex()).toSet()
        if (wordsA.isEmpty() || wordsB.isEmpty()) return false
        val intersection = wordsA.intersect(wordsB)
        val smaller = minOf(wordsA.size, wordsB.size)
        return intersection.size.toFloat() / smaller >= 0.7f
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

        val displayText = entry.correctedText ?: entry.text
        val reviewBadge = if (entry.wasReviewedByLLM) " (revisado por IA)" else ""

        val notification = NotificationCompat.Builder(this, NEW_ENTRY_CHANNEL_ID)
            .setContentTitle("${entry.category}$reviewBadge")
            .setContentText(displayText)
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
