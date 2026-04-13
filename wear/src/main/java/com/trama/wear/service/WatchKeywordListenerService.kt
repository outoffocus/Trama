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
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.trama.shared.audio.VoskGateAsr
import com.trama.wear.audio.WatchTriggeredAudioCapture
import com.trama.wear.NotificationConfig
import com.trama.shared.audio.CapturedAudioWindow
import com.trama.wear.R
import com.trama.wear.ui.WatchMainActivity
import com.trama.shared.data.DiaryRepository
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.Source
import com.trama.shared.model.WatchAudioSyncMetadata
import com.trama.shared.speech.EntryValidatorHeuristics
import com.trama.shared.speech.IntentDetector
import com.trama.shared.speech.IntentPattern
import com.trama.shared.sync.MicCoordinator
import com.trama.wear.sync.PhoneToWatchReceiver
import com.trama.wear.sync.WatchToPhoneSyncer
import com.trama.shared.data.DatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Watch keyword listener.
 *
 * Primary path: Vosk gate ASR (AudioRecord loop, same model as phone).
 *   - Records 2-second windows continuously, transcribes with Vosk.
 *   - When keyword detected → WatchTriggeredAudioCapture + send PCM to phone.
 *
 * Fallback path (when Vosk model not installed): Android SpeechRecognizer with
 *   smart backoff (1s→2s→4s→5s) and battery guard.
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
        private const val RESTART_MAX_BACKOFF_MS = 5000L     // 5s max backoff
        private const val ERROR_RETRY_DELAY_MS = 3000L       // 3s on hard errors

        private const val DEDUP_WINDOW_MS = 5000L
    }

    private var recognizer: SpeechRecognizer? = null
    private var repository: DiaryRepository? = null
    private var syncer: WatchToPhoneSyncer? = null
    private var intentDetector: IntentDetector? = null

    @Volatile private var listening = false
    @Volatile private var captureInFlight = false
    @Volatile private var voskAudioRecord: AudioRecord? = null
    private var useVoskLoop = false
    private var consecutiveNoKeyword = 0   // fallback SpeechRecognizer only
    private var consecutiveErrors = 0      // fallback SpeechRecognizer only
    private var lastNotificationText = ""
    private var useSimpleIntent = false
    private var batteryPct = 100

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

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1).coerceAtLeast(1)
            batteryPct = if (level >= 0) (level * 100) / scale else batteryPct

            if (listening && batteryPct in 1 until LOW_BATTERY_THRESHOLD) {
                Log.w(TAG, "Battery dropped to $batteryPct%, stopping watch listener")
                updateNotificationIfChanged("Batería baja · vuelve al teléfono")
                listening = false
                WatchServiceController.sendResumeToPhone(applicationContext)
                stopSelf()
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

            val vosk = VoskGateAsr(applicationContext)
            if (vosk.isAvailable) {
                withContext(Dispatchers.Main) {
                    registerSettingsReceiver()
                    registerBatteryReceiver()
                }
                startVoskLoop(vosk)
            } else {
                withContext(Dispatchers.Main) {
                    registerSettingsReceiver()
                    registerBatteryReceiver()
                    initRecognizerAndStart()
                }
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

    private fun registerBatteryReceiver() {
        try {
            registerReceiver(
                batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                Context.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        listening = false
        try { unregisterReceiver(settingsReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        runCatching { voskAudioRecord?.stop() }
        runCatching { voskAudioRecord?.release() }
        voskAudioRecord = null
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

        updateNotificationIfChanged(
            if (consecutiveNoKeyword == 0) "Escuchando..." else "Esperando..."
        )

        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
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
                        // Keyword match → transferTriggeredAudio() handles resuming the listener
                        consecutiveNoKeyword = 0
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
        lifecycleScope.launch(Dispatchers.IO) {
            MicCoordinator.sendWatchDebug(applicationContext, "trigger detectado", result.capturedText)
        }
        transferTriggeredAudio(
            intentId = intentId,
            label = result.label,
            capturedText = result.capturedText
        )

        lastSavedText = text
        lastSavedTime = now
        return true
    }

    private fun transferTriggeredAudio(intentId: String, label: String, capturedText: String) {
        if (captureInFlight) {
            Log.i(TAG, "Skipping trigger capture because another capture is in flight")
            return
        }

        captureInFlight = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                pauseRecognizerForCapture()
                MicCoordinator.sendWatchDebug(applicationContext, "capturando audio", capturedText)
                val pcm = WatchTriggeredAudioCapture().capture()
                if (pcm.isEmpty()) {
                    Log.w(TAG, "Triggered audio capture returned empty PCM, falling back to text entry")
                    MicCoordinator.sendWatchDebug(applicationContext, "sin audio · guardando texto", capturedText)
                    saveEntry(intentId, label, capturedText, 0.9f)
                    return@launch
                }

                val metadata = WatchAudioSyncMetadata(
                    createdAt = System.currentTimeMillis(),
                    durationSeconds = pcm.size / 16_000,
                    sampleRateHz = 16_000,
                    source = Source.WATCH.name,
                    kind = "CONTEXTUAL_TRIGGER",
                    triggerText = capturedText,
                    intentId = intentId,
                    label = label
                )

                runCatching {
                    syncer?.syncRecordingAudio(shortsToBytes(pcm), metadata)
                }.onSuccess {
                    Log.i(TAG, "Triggered audio transferred to phone")
                    MicCoordinator.sendWatchDebug(applicationContext, "audio enviado al móvil", capturedText)
                }.onFailure { error ->
                    Log.w(TAG, "Triggered audio transfer failed, falling back to text entry", error)
                    MicCoordinator.sendWatchDebug(applicationContext, "fallo · guardando texto", capturedText)
                    saveEntry(intentId, label, capturedText, 0.9f)
                }
            } finally {
                captureInFlight = false
                resumeRecognizerAfterCapture()
            }
        }
    }

    private suspend fun saveEntry(intentId: String, label: String, text: String, confidence: Float) {
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

    // ── Vosk AudioRecord loop ────────────────────────────────────────────────

    private fun startVoskLoop(vosk: VoskGateAsr) {
        useVoskLoop = true
        listening = true
        updateNotificationIfChanged("Escuchando...")

        lifecycleScope.launch(Dispatchers.IO) {
            val sampleRate = 16_000
            val windowSamples = sampleRate * 2 // 2-second windows
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(windowSamples * 2)

            val record = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC, sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuffer
                )
            } catch (e: Exception) {
                Log.e(TAG, "Cannot open AudioRecord for Vosk", e)
                return@launch
            }

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                Log.e(TAG, "AudioRecord not initialized")
                return@launch
            }

            voskAudioRecord = record
            record.startRecording()
            Log.i(TAG, "Vosk loop started")

            try {
                while (listening && isActive) {
                    if (captureInFlight) {
                        delay(200)
                        continue
                    }

                    val pcm = ShortArray(windowSamples)
                    var samplesRead = 0
                    while (samplesRead < windowSamples && listening && !captureInFlight) {
                        val n = record.read(pcm, samplesRead, windowSamples - samplesRead)
                        if (n > 0) samplesRead += n else break
                    }

                    if (samplesRead == 0 || captureInFlight) continue

                    val window = CapturedAudioWindow(
                        preRollPcm = shortArrayOf(),
                        livePcm = pcm.copyOf(samplesRead),
                        sampleRateHz = sampleRate
                    )

                    val text = vosk.transcribe(window, "es") ?: continue
                    if (text.isNotBlank()) {
                        Log.d(TAG, "Vosk: '$text'")
                        processText(text)
                    }
                }
            } finally {
                runCatching { record.stop() }
                record.release()
                voskAudioRecord = null
                Log.i(TAG, "Vosk loop stopped")
            }
        }
    }

    // ── Mic handoff ──────────────────────────────────────────────────────────

    private suspend fun pauseRecognizerForCapture() {
        if (useVoskLoop) {
            // Stop the Vosk AudioRecord so WatchTriggeredAudioCapture can open the mic
            withContext(Dispatchers.IO) {
                runCatching { voskAudioRecord?.stop() }
                runCatching { voskAudioRecord?.release() }
                voskAudioRecord = null
            }
        } else {
            withContext(Dispatchers.Main) {
                try { recognizer?.cancel() } catch (_: Exception) {}
                try { recognizer?.destroy() } catch (_: Exception) {}
                recognizer = null
            }
        }
        // Give Android time to fully release the mic before AudioRecord opens it.
        delay(450)
    }

    private suspend fun resumeRecognizerAfterCapture() {
        if (!listening) return
        delay(RESTART_AFTER_KEYWORD_MS)
        if (useVoskLoop) {
            val vosk = VoskGateAsr(applicationContext)
            if (vosk.isAvailable) {
                startVoskLoop(vosk)
            } else {
                withContext(Dispatchers.Main) {
                    recognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext)
                    startListening()
                }
            }
        } else {
            withContext(Dispatchers.Main) {
                try { recognizer?.destroy() } catch (_: Exception) {}
                recognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext)
                startListening()
            }
        }
    }

    private fun shortsToBytes(samples: ShortArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        var byteIndex = 0
        samples.forEach { sample ->
            bytes[byteIndex] = (sample.toInt() and 0xFF).toByte()
            bytes[byteIndex + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
            byteIndex += 2
        }
        return bytes
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
