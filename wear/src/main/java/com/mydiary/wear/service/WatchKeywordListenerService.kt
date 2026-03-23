package com.mydiary.wear.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.mydiary.wear.R
import com.mydiary.wear.ui.WatchMainActivity
import com.mydiary.shared.data.DiaryRepository
import com.mydiary.shared.model.DiaryEntry
import com.mydiary.shared.model.Source
import com.mydiary.shared.speech.EntryValidatorHeuristics
import com.mydiary.shared.speech.IntentDetector
import com.mydiary.shared.speech.IntentPattern
import com.mydiary.shared.speech.SimpleVAD
import com.mydiary.shared.speech.SpeakerProfile
import com.mydiary.wear.sync.MicCoordinator
import com.mydiary.wear.sync.PhoneToWatchReceiver
import com.mydiary.wear.sync.WatchToPhoneSyncer
import com.mydiary.wear.ui.DatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Watch keyword listener using Android SpeechRecognizer in continuous loop.
 * Same architecture as the phone app — uses IntentDetector for flexible matching.
 *
 * Battery optimization: uses SimpleVAD (dB threshold) to gate SpeechRecognizer.
 * Only activates SpeechRecognizer when voice is actually detected, saving ~60% battery.
 *
 * Loads intent patterns from SharedPreferences (synced from phone).
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

        private const val DEDUP_WINDOW_MS = 5000L

        // VAD AudioRecord settings
        private const val SAMPLE_RATE = 16000
        private const val VAD_FRAME_SIZE = 512  // ~32ms at 16kHz
    }

    private var recognizer: SpeechRecognizer? = null
    private var repository: DiaryRepository? = null
    private var syncer: WatchToPhoneSyncer? = null
    private var intentDetector: IntentDetector? = null

    // VAD for battery optimization
    private var vadAudioRecord: AudioRecord? = null
    private var vadJob: Job? = null
    private val simpleVAD = SimpleVAD()
    @Volatile
    private var vadMode = false  // true = VAD is listening instead of SpeechRecognizer
    @Volatile
    private var speechRecognizerActive = false

    @Volatile
    private var listening = false
    private var consecutiveSilent = 0
    private var batteryCheckCounter = 0
    private var lastNotificationText = ""
    private var useSimpleIntent = false

    // Speaker verification (profile synced from phone)
    private var speakerProfile: SpeakerProfile? = null
    private val recentRmsValues = mutableListOf<Double>()

    // Deduplication
    private var lastSavedText = ""
    private var lastSavedTime = 0L

    /** Broadcast receiver for settings updates from PhoneToWatchReceiver */
    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.mydiary.wear.SETTINGS_UPDATED") {
                lifecycleScope.launch(Dispatchers.IO) { loadSettingsFromPrefs() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Only lightweight work here — no database, no JSON parsing
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // MUST call startForeground() FIRST — Android requires it immediately
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Inicializando..."),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Inicializando..."))
        }

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

        // Heavy init on background thread, then start listening on main
        lifecycleScope.launch(Dispatchers.IO) {
            initDatabase()
            if (intentDetector == null) {
                intentDetector = IntentDetector()
            }
            loadSettingsFromPrefs()

            launch(Dispatchers.Main) {
                registerSettingsReceiver()
                initRecognizerAndStart()
            }

            MicCoordinator.sendPause(applicationContext)
        }

        return START_STICKY
    }

    private fun registerSettingsReceiver() {
        try {
            registerReceiver(settingsReceiver, IntentFilter("com.mydiary.wear.SETTINGS_UPDATED"),
                Context.RECEIVER_NOT_EXPORTED)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        listening = false
        stopVAD()
        try {
            unregisterReceiver(settingsReceiver)
        } catch (_: Exception) {}
        recognizer?.destroy()
        recognizer = null

        lifecycleScope.launch(Dispatchers.IO) { MicCoordinator.sendResume(applicationContext) }

        WatchServiceController.notifyStopped()
        super.onDestroy()
    }

    /**
     * Load intent patterns and custom keywords from SharedPreferences (synced from phone).
     */
    private fun loadSettingsFromPrefs() {
        val prefs = getSharedPreferences(PhoneToWatchReceiver.PREFS, Context.MODE_PRIVATE)

        val patternsJson = prefs.getString("intent_patterns_json", null)
        if (patternsJson != null) {
            val patterns = IntentPattern.deserialize(patternsJson)
            intentDetector?.setPatterns(patterns)
            Log.i(TAG, "Intent patterns loaded: ${patterns.count { it.enabled }} enabled")
        }

        val keywordsStr = prefs.getString("keyword_mappings", null)
        if (keywordsStr != null) {
            val keywords = keywordsStr.split(",").mapNotNull { entry ->
                val trimmed = entry.trim()
                if (trimmed.isBlank()) return@mapNotNull null
                if (trimmed.contains(":")) trimmed.substringBefore(":").trim().lowercase()
                else trimmed.lowercase()
            }.filter { it.isNotBlank() }

            intentDetector?.setCustomKeywords(keywords)
            Log.i(TAG, "Custom keywords loaded: ${keywords.size}")
        }

        // Load speaker profile (synced from phone)
        val profileJson = prefs.getString("speaker_profile_json", null)
        if (profileJson != null) {
            speakerProfile = SpeakerProfile.deserialize(profileJson)
            Log.i(TAG, "Speaker profile loaded: avgRMS=${"%.1f".format(speakerProfile?.avgRMS)}dB")
        }
    }

    private fun initDatabase() {
        repository = DatabaseProvider.getRepository(applicationContext)
        syncer = repository?.let { WatchToPhoneSyncer(applicationContext, it) }
    }

    private fun initRecognizerAndStart() {
        lifecycleScope.launch(Dispatchers.Main) {
            try {
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
                // Start in VAD mode to save battery
                startVADMode()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create SpeechRecognizer", e)
                updateNotificationIfChanged("Error")
                stopSelf()
            }
        }
    }

    /**
     * Start VAD mode: only AudioRecord + dB threshold, no SpeechRecognizer.
     * When voice is detected, transitions to SpeechRecognizer mode.
     */
    private fun startVADMode() {
        if (!listening) return

        // Can't use VAD if no mic permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            // Fall back to direct SpeechRecognizer
            startListening()
            return
        }

        vadMode = true
        updateNotificationIfChanged("Esperando voz...")

        simpleVAD.reset()
        simpleVAD.onVoiceStart = {
            Log.i(TAG, "VAD: voice detected, starting SpeechRecognizer")
            lifecycleScope.launch(Dispatchers.Main) {
                stopVAD()
                startListening()
            }
        }

        vadJob = lifecycleScope.launch(Dispatchers.IO) {
            val bufferSize = maxOf(
                AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT),
                VAD_FRAME_SIZE * 2
            )

            try {
                @Suppress("MissingPermission")
                vadAudioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create VAD AudioRecord", e)
                launch(Dispatchers.Main) { startListening() } // fallback
                return@launch
            }

            val record = vadAudioRecord ?: return@launch
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                vadAudioRecord = null
                launch(Dispatchers.Main) { startListening() }
                return@launch
            }

            record.startRecording()
            val buffer = ShortArray(VAD_FRAME_SIZE)

            while (listening && vadMode && isActive) {
                val read = record.read(buffer, 0, VAD_FRAME_SIZE)
                if (read > 0) {
                    simpleVAD.processFrame(buffer, read)
                }

                // Periodic battery check
                if (++batteryCheckCounter % 500 == 0) {
                    if (isBatteryLow()) {
                        Log.w(TAG, "Battery low during VAD, stopping")
                        launch(Dispatchers.Main) {
                            updateNotificationIfChanged("Batería baja")
                            listening = false
                            stopSelf()
                        }
                        break
                    }
                }
            }

            try {
                record.stop()
                record.release()
            } catch (_: IllegalStateException) {}
            vadAudioRecord = null
        }
    }

    private fun stopVAD() {
        vadMode = false
        simpleVAD.reset()
        vadJob?.cancel()
        vadJob = null
        try {
            vadAudioRecord?.stop()
            vadAudioRecord?.release()
        } catch (_: Exception) {}
        vadAudioRecord = null
    }

    private fun startListening() {
        if (!listening) return
        val rec = recognizer ?: return

        vadMode = false
        speechRecognizerActive = true
        recentRmsValues.clear()

        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                updateNotificationIfChanged("Escuchando...")
            }

            override fun onBeginningOfSpeech() {
                consecutiveSilent = 0
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Collect RMS for speaker verification (same scale as phone enrollment)
                val profile = speakerProfile
                if (profile != null) {
                    recentRmsValues.add(rmsdB.toDouble())
                    if (recentRmsValues.size > 50) recentRmsValues.removeAt(0)
                }
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                speechRecognizerActive = false
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        consecutiveSilent++
                        // After enough silence, go back to VAD mode to save battery
                        if (consecutiveSilent >= SLOW_MODE_THRESHOLD) {
                            Log.i(TAG, "Extended silence, switching to VAD mode")
                            startVADMode()
                        } else {
                            restartListening(adaptiveDelay())
                        }
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

    /**
     * Process transcribed text through IntentDetector.
     * Applies speaker verification + heuristic validation before saving.
     */
    private fun processText(text: String) {
        val result = intentDetector?.detect(text) ?: return

        // Speaker verification — reject if voice doesn't match enrolled profile
        val profile = speakerProfile
        if (profile != null) {
            val verification = SpeakerProfile.verify(recentRmsValues.toList(), profile)
            if (!verification.isMatch) {
                Log.i(TAG, "Speaker verification failed (sim=${"%.2f".format(verification.similarity)}), " +
                    "rejecting: '${text.take(40)}'")
                return
            }
            Log.d(TAG, "Speaker verified (sim=${"%.2f".format(verification.similarity)})")
        }

        // Heuristic validation — reject obvious noise (radio, TV, fragments)
        val heuristic = EntryValidatorHeuristics.check(result.capturedText)
        if (heuristic != null && !heuristic.isValid) {
            Log.i(TAG, "Heuristic rejected: ${heuristic.reason} — '${text.take(40)}'")
            return
        }

        // Deduplication
        val now = System.currentTimeMillis()
        if (now - lastSavedTime < DEDUP_WINDOW_MS && isSimilar(text, lastSavedText)) {
            Log.i(TAG, "Dedup: skipping similar entry")
            return
        }

        val intentId = result.pattern?.id ?: result.customKeyword ?: "nota"
        Log.i(TAG, "Intent '$intentId' [${result.label}] found in: '${text.take(60)}'")
        saveEntry(intentId, result.label, result.capturedText, 0.9f)

        lastSavedText = text
        lastSavedTime = now
    }

    private fun saveEntry(intentId: String, label: String, text: String, confidence: Float) {
        lifecycleScope.launch(Dispatchers.IO) {
            val entry = DiaryEntry(
                text = text,
                keyword = intentId,
                category = label,
                confidence = confidence,
                source = Source.WATCH,
                duration = 0
            )
            repository?.insert(entry)
            Log.i(TAG, "Entry saved: '$text' (intent: $intentId)")

            syncer?.syncUnsentEntries()
        }
    }

    private fun isSimilar(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        val wordsA = a.lowercase().split("\\s+".toRegex()).toSet()
        val wordsB = b.lowercase().split("\\s+".toRegex()).toSet()
        if (wordsA.isEmpty() || wordsB.isEmpty()) return false
        val intersection = wordsA.intersect(wordsB)
        val smaller = minOf(wordsA.size, wordsB.size)
        return intersection.size.toFloat() / smaller >= 0.7f
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
