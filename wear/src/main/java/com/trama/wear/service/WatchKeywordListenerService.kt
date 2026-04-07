package com.trama.wear.service

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
import com.trama.wear.NotificationConfig
import com.trama.wear.R
import com.trama.wear.ui.WatchMainActivity
import com.trama.shared.data.DiaryRepository
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.Source
import com.trama.shared.speech.EntryValidatorHeuristics
import com.trama.shared.speech.IntentDetector
import com.trama.shared.speech.IntentPattern
import com.trama.shared.speech.SpeakerProfile
import com.trama.wear.speech.WatchSpeakerEnrollment
import com.trama.shared.sync.MicCoordinator
import com.trama.wear.sync.PhoneToWatchReceiver
import com.trama.wear.sync.WatchToPhoneSyncer
import com.trama.shared.data.DatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Watch keyword listener — SpeechRecognizer with smart backoff.
 *
 * No VAD (avoids mic contention that cuts phrases). Battery strategy:
 * 1. Smart backoff: speech without keyword → delay grows (1s→2s→4s→8s)
 *    Only keyword match resets backoff to fast mode (300ms)
 * 2. Silence backoff: ERROR_NO_MATCH/TIMEOUT → same progressive delay
 * 3. Speaker verification using locally enrolled profile (watch mic)
 * 4. Sync per entry without setUrgent()
 */
class WatchKeywordListenerService : LifecycleService() {

    companion object {
        private const val TAG = "WatchListenerService"
        private const val CHANNEL_ID = NotificationConfig.CHANNEL_WATCH_LISTENER
        private const val NOTIFICATION_ID = NotificationConfig.ID_WATCH_LISTENER
        private const val LOW_BATTERY_THRESHOLD = 20

        // Restart delays — progressive backoff
        private const val RESTART_AFTER_KEYWORD_MS = 300L    // fast: user actively dictating
        private const val RESTART_MIN_BACKOFF_MS = 1000L     // 1s initial backoff
        private const val RESTART_MAX_BACKOFF_MS = 8000L     // 8s max backoff
        private const val ERROR_RETRY_DELAY_MS = 3000L       // 3s on hard errors

        private const val DEDUP_WINDOW_MS = 5000L

        // Battery check: every N restarts
        private const val BATTERY_CHECK_INTERVAL = 15
    }

    private var recognizer: SpeechRecognizer? = null
    private var repository: DiaryRepository? = null
    private var syncer: WatchToPhoneSyncer? = null
    private var intentDetector: IntentDetector? = null

    @Volatile private var listening = false
    private var consecutiveNoKeyword = 0   // counts silence + non-keyword results
    private var restartCount = 0
    private var consecutiveErrors = 0      // hard errors (not silence/no-match)
    private var lastNotificationText = ""
    private var useSimpleIntent = false

    // Speaker verification (enrolled locally on watch)
    private var speakerProfile: SpeakerProfile? = null
    private var speakerThreshold: Float = 0.45f
    private val recentRmsValues = mutableListOf<Double>()

    // Deduplication
    private var lastSavedText = ""
    private var lastSavedTime = 0L

    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.trama.wear.SETTINGS_UPDATED") {
                lifecycleScope.launch(Dispatchers.IO) { loadSettingsFromPrefs() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // MUST call startForeground immediately in onCreate — before system timeout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, buildNotification("Inicializando..."),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Inicializando..."))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (WatchServiceController.isPhoneActive(applicationContext)) {
            Log.i(TAG, "Phone active, not starting")
            stopSelf(); return START_NOT_STICKY
        }
        if (isBatteryLow()) {
            Log.w(TAG, "Battery low, not starting")
            stopSelf(); return START_NOT_STICKY
        }

        lifecycleScope.launch(Dispatchers.IO) {
            initDatabase()
            if (intentDetector == null) intentDetector = IntentDetector()
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
            registerReceiver(settingsReceiver, IntentFilter("com.trama.wear.SETTINGS_UPDATED"),
                Context.RECEIVER_NOT_EXPORTED)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        listening = false
        try { unregisterReceiver(settingsReceiver) } catch (_: Exception) {}
        recognizer?.destroy()
        recognizer = null

        // Final sync before dying (best-effort, may be cancelled)
        lifecycleScope.launch(Dispatchers.IO) {
            try { syncer?.syncUnsentEntries() } catch (_: Exception) {}
        }
        // Note: MicCoordinator.sendResume is handled by WatchServiceController.stopByUser()
        // not here, because onDestroy also fires when phone pauses us (and we shouldn't
        // send RESUME back in that case).

        WatchServiceController.notifyStopped()
        super.onDestroy()
    }

    // ── Settings ─────────────────────────────────────────────────────────────

    private fun loadSettingsFromPrefs() {
        val prefs = getSharedPreferences(PhoneToWatchReceiver.PREFS, Context.MODE_PRIVATE)

        prefs.getString("intent_patterns_json", null)?.let { json ->
            val patterns = IntentPattern.deserialize(json)
            intentDetector?.setPatterns(patterns)
            Log.i(TAG, "Patterns loaded: ${patterns.count { it.enabled }} enabled")
        }

        prefs.getString("keyword_mappings", null)?.let { str ->
            val keywords = str.split(",").mapNotNull { entry ->
                val trimmed = entry.trim()
                if (trimmed.isBlank()) return@mapNotNull null
                if (trimmed.contains(":")) trimmed.substringBefore(":").trim().lowercase()
                else trimmed.lowercase()
            }.filter { it.isNotBlank() }
            intentDetector?.setCustomKeywords(keywords)
        }

        // Speaker verification — user can enable/disable from watch settings
        val watchPrefs = getSharedPreferences("watch_settings", Context.MODE_PRIVATE)
        val speakerVerificationEnabled = watchPrefs.getBoolean("speaker_verification_enabled", false)
        speakerThreshold = watchPrefs.getFloat("speaker_threshold", 0.45f)

        if (speakerVerificationEnabled) {
            prefs.getString("speaker_profile_json", null)?.let { json ->
                speakerProfile = SpeakerProfile.deserialize(json)
                Log.i(TAG, "Speaker verification enabled (threshold=${"%.0f".format(speakerThreshold * 100)}%)")
            } ?: run {
                speakerProfile = null
                Log.i(TAG, "Speaker verification enabled but no profile found")
            }
        } else {
            speakerProfile = null
            Log.d(TAG, "Speaker verification disabled")
        }
    }

    private fun initDatabase() {
        repository = DatabaseProvider.getRepository(applicationContext)
        syncer = repository?.let { WatchToPhoneSyncer(applicationContext, it) }
    }

    // ── Recognition ──────────────────────────────────────────────────────────

    private fun initRecognizerAndStart() {
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                recognizer = if (SpeechRecognizer.isRecognitionAvailable(applicationContext)) {
                    SpeechRecognizer.createSpeechRecognizer(applicationContext)
                } else {
                    Log.e(TAG, "No SpeechRecognizer available")
                    stopSelf(); return@launch
                }
                listening = true
                startListening()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create SpeechRecognizer", e)
                stopSelf()
            }
        }
    }

    private fun startListening() {
        if (!listening) return
        val rec = recognizer ?: return

        recentRmsValues.clear()
        updateNotificationIfChanged(
            if (consecutiveNoKeyword == 0) "Escuchando..." else "Esperando..."
        )

        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                if (speakerProfile != null) {
                    recentRmsValues.add(rmsdB.toDouble())
                    if (recentRmsValues.size > 30) recentRmsValues.removeAt(0)
                }
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        // Normal silence — backoff but reset hard error counter
                        consecutiveErrors = 0
                        consecutiveNoKeyword++
                        restartListening(calculateBackoff())
                    }
                    else -> {
                        consecutiveErrors++
                        Log.w(TAG, "Recognition error: $error (consecutive=$consecutiveErrors)")

                        when {
                            // After 3 hard errors, try simplified intent (no language extras)
                            consecutiveErrors == 3 && !useSimpleIntent -> {
                                Log.i(TAG, "Switching to simple intent after $consecutiveErrors errors")
                                useSimpleIntent = true
                                recreateRecognizer()
                            }
                            // After 5 hard errors, recreate recognizer (might be stale)
                            consecutiveErrors % 5 == 0 -> {
                                Log.i(TAG, "Recreating recognizer after $consecutiveErrors errors")
                                recreateRecognizer()
                            }
                            else -> {
                                restartListening(ERROR_RETRY_DELAY_MS)
                            }
                        }
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                consecutiveErrors = 0  // got results → connection works
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim() ?: ""

                if (text.isNotBlank()) {
                    Log.i(TAG, "Heard: '$text'")
                    val matched = processText(text)
                    if (matched) {
                        // Keyword match → reset backoff, stay responsive
                        consecutiveNoKeyword = 0
                        restartListening(RESTART_AFTER_KEYWORD_MS)
                    } else {
                        // Speech but no keyword → background noise / others talking
                        Log.d(TAG, "No keyword match for: '$text'")
                        consecutiveNoKeyword++
                        restartListening(calculateBackoff())
                    }
                } else {
                    consecutiveNoKeyword++
                    restartListening(calculateBackoff())
                }
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

    /**
     * Destroy and recreate the SpeechRecognizer instance.
     * Fixes stale connections (ERROR_SERVER_DISCONNECTED etc.)
     */
    private fun recreateRecognizer() {
        if (!listening) return
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                recognizer?.destroy()
            } catch (_: Exception) {}
            recognizer = null

            delay(ERROR_RETRY_DELAY_MS)

            try {
                recognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext)
                Log.i(TAG, "Recognizer recreated")
                startListening()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to recreate recognizer", e)
                stopSelf()
            }
        }
    }

    /**
     * Progressive backoff: 1s → 2s → 4s → 8s (max).
     * Saves battery when nobody is speaking keywords.
     */
    private fun calculateBackoff(): Long {
        val shift = minOf(consecutiveNoKeyword, 3)
        val backoff = RESTART_MIN_BACKOFF_MS * (1L shl shift)
        return minOf(backoff, RESTART_MAX_BACKOFF_MS)
    }

    private fun buildRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            if (useSimpleIntent) return@apply

            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            if (Build.VERSION.SDK_INT >= 34) {
                putExtra("android.speech.extra.ENABLE_LANGUAGE_SWITCH", true)
                putExtra("android.speech.extra.LANGUAGE_SWITCH_ALLOWED_LANGUAGES",
                    arrayListOf("es-ES", "en-US"))
            }
        }
    }

    private fun restartListening(delayMs: Long) {
        if (!listening) return

        restartCount++
        if (restartCount % BATTERY_CHECK_INTERVAL == 0 && isBatteryLow()) {
            Log.w(TAG, "Battery low, stopping")
            updateNotificationIfChanged("Bateria baja")
            listening = false
            // Resume phone mic before dying — use controller scope (not lifecycleScope)
            WatchServiceController.sendResumeToPhone(applicationContext)
            stopSelf()
            return
        }

        lifecycleScope.launch(Dispatchers.Main) {
            delay(delayMs)
            if (listening) startListening()
        }
    }

    // ── Text Processing ──────────────────────────────────────────────────────

    /**
     * @return true if keyword matched (entry saved or rejected by heuristic),
     *         false if no keyword detected (irrelevant speech)
     */
    private fun processText(text: String): Boolean {
        val result = intentDetector?.detect(text) ?: return false

        // Speaker verification (locally enrolled on watch)
        val profile = speakerProfile
        if (profile != null) {
            val verification = SpeakerProfile.verify(recentRmsValues.toList(), profile, speakerThreshold)
            if (!verification.isMatch) {
                Log.i(TAG, "Speaker rejected (sim=${"%.2f".format(verification.similarity)})")
                return true  // keyword was there, wrong speaker — don't reset backoff
            }
        }

        // Heuristic validation
        val heuristic = EntryValidatorHeuristics.check(result.capturedText)
        if (heuristic != null && !heuristic.isValid) {
            Log.i(TAG, "Heuristic rejected: ${heuristic.reason}")
            return true  // keyword was there, just invalid content
        }

        // Deduplication
        val now = System.currentTimeMillis()
        if (now - lastSavedTime < DEDUP_WINDOW_MS && isSimilar(text, lastSavedText)) return true

        val intentId = result.pattern?.id ?: result.customKeyword ?: "nota"
        Log.i(TAG, "Intent '$intentId' [${result.label}]: '${text.take(60)}'")
        saveEntry(intentId, result.label, result.capturedText, 0.9f)

        lastSavedText = text
        lastSavedTime = now
        return true
    }

    private fun saveEntry(intentId: String, label: String, text: String, confidence: Float) {
        lifecycleScope.launch(Dispatchers.IO) {
            val entry = DiaryEntry(
                text = text, keyword = intentId, category = label,
                confidence = confidence, source = Source.WATCH, duration = 0
            )
            repository?.insert(entry)
            Log.i(TAG, "Entry saved: '$text'")

            // Sync per entry (no setUrgent — DataClient batches naturally)
            try {
                syncer?.syncUnsentEntries()
            } catch (e: Exception) {
                Log.w(TAG, "Sync failed, will retry later", e)
            }
        }
    }

    // ── Utilities ────────────────────────────────────────────────────────────

    private fun isSimilar(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        val wordsA = a.lowercase().split("\\s+".toRegex()).toSet()
        val wordsB = b.lowercase().split("\\s+".toRegex()).toSet()
        if (wordsA.isEmpty() || wordsB.isEmpty()) return false
        return wordsA.intersect(wordsB).size.toFloat() / minOf(wordsA.size, wordsB.size) >= 0.7f
    }

    private fun isBatteryLow(): Boolean {
        val bm = getSystemService(BatteryManager::class.java)
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) in 1 until LOW_BATTERY_THRESHOLD
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Servicio de escucha", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, WatchMainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Trama")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pi)
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
