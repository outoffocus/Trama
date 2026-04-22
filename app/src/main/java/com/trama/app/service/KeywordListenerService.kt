package com.trama.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.trama.app.MainActivity
import com.trama.app.NotificationConfig
import com.trama.app.R
import com.trama.app.audio.ContextualAudioCaptureEngine
import com.trama.app.audio.SherpaWhisperAsrEngine
import com.trama.shared.audio.VoskGateAsr
import com.trama.shared.audio.ContextualCaptureConfig
import com.trama.shared.audio.LightweightGateAsr
import com.trama.shared.audio.NoOpAsrEngine
import com.trama.shared.audio.NoOpLightweightGateAsr
import com.trama.shared.audio.OnDeviceAsrEngine
import com.trama.app.speech.EntryValidator
import com.trama.app.speech.IntentDetector
import com.trama.app.speech.IntentPattern
import com.trama.app.speech.PersonalDictionary
import com.trama.app.speech.speaker.SherpaSpeakerVerificationManager
import com.trama.app.summary.ActionItemProcessor
import com.trama.shared.sync.MicCoordinator
import com.trama.app.sync.PhoneToWatchSyncer
import com.trama.app.sync.SettingsSyncer
import com.trama.shared.data.DatabaseProvider
import com.trama.app.ui.SettingsDataStore
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.Source
import com.trama.shared.speech.IntentDetector.DetectionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.Normalizer

/**
 * Continuous speech listening service using Android SpeechRecognizer.
 *
 * Uses IntentDetector for flexible regex-based intent matching instead of
 * exact keyword matching. Enables partial results for faster detection.
 *
 * Integrated features:
 * - VAD (Voice Activity Detection): Only starts SpeechRecognizer when voice is detected
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
        private const val CHANNEL_ID = NotificationConfig.CHANNEL_LISTENER
        private const val NOTIFICATION_ID = NotificationConfig.ID_LISTENER
        private const val NEW_ENTRY_CHANNEL_ID = NotificationConfig.CHANNEL_NEW_ENTRY

        private const val RESTART_DELAY_NORMAL_MS = 500L
        private const val RESTART_DELAY_SLOW_MS = 1500L
        private const val RESTART_DELAY_DEEP_SLEEP_MS = 3000L
        private const val RESTART_DELAY_HIBERNATE_MS = 5000L
        private const val ERROR_RETRY_DELAY_MS = 1000L
        private const val CONTEXTUAL_RESTART_DELAY_MS = 500L
        private const val SPEAKER_VERIFY_WINDOW_MS = 3_000L

        private const val SLOW_MODE_THRESHOLD = 20
        private const val BATTERY_THRESHOLD = 15
        private const val DEEP_SLEEP_THRESHOLD = 15
        private const val HIBERNATE_THRESHOLD = 30

        // Deduplication: ignore entries within this window of a previous capture
        private const val DEDUP_WINDOW_MS = 5000L
        private const val PERSISTED_DEDUP_WINDOW_MS = 15000L
        private val DEDUP_STOPWORDS = setOf(
            "recordar",
            "recorda",
            "acordarme",
            "acordarnos",
            "de",
            "me",
            "olvide",
            "se",
            "fue",
            "la",
            "olla"
        )
    }

    private var recognizer: SpeechRecognizer? = null
    private var repository: com.trama.shared.data.DiaryRepository? = null
    private lateinit var settings: SettingsDataStore
    private lateinit var dictionary: PersonalDictionary
    private lateinit var intentDetector: IntentDetector
    private lateinit var entryValidator: EntryValidator
    private lateinit var speakerVerificationManager: SherpaSpeakerVerificationManager
    private var phoneToWatchSyncer: PhoneToWatchSyncer? = null
    private lateinit var settingsSyncer: SettingsSyncer
    private lateinit var asrEngine: OnDeviceAsrEngine
    private lateinit var gateAsr: LightweightGateAsr
    private var contextualCaptureEngine: ContextualAudioCaptureEngine? = null
    private var contextualCaptureJob: Job? = null
    private var startupJob: Job? = null
    private var contextPreRollSeconds: Int = SettingsDataStore.DEFAULT_CONTEXT_PRE_ROLL
    private var contextPostRollSeconds: Int = SettingsDataStore.DEFAULT_CONTEXT_POST_ROLL
    private var asrDebugEnabled: Boolean = false
    @Volatile private var asrDebugEnabledVolatile = false
    @Volatile
    private var listening = false

    @Volatile
    private var screenOff = false

    @Volatile
    private var speechRecognizerActive = false

    @Volatile
    private var dedicatedAsrFailedOver = false

    @Volatile private var consecutiveSilent = 0
    @Volatile private var lastNotificationText = ""
    @Volatile
    private var batteryPct: Int = 100
    @Volatile
    private var batteryLowNoticeShown = false

    // Deduplication: avoid saving same entry from partial + final results.
    // Guarded by saveMutex during check-then-update.
    @Volatile private var lastSavedText = ""
    @Volatile private var lastSavedDedupKey = ""
    @Volatile private var lastSavedTime = 0L

    /** Serializes the check-then-insert sequence in [saveEntry] so concurrent detections
     *  (partial + final from different threads) cannot both pass the in-memory dedup gate
     *  and double-insert. Room's `withTransaction` already serializes DB writes, but the
     *  in-memory flags (`lastSaved*`) need this mutex for atomic update. */
    private val saveMutex = Mutex()

    /** Guards the synchronous check-then-update of in-memory dedup state in
     *  [processDetectedResult]. Separate from [saveMutex] because that callsite is
     *  non-suspend. */
    private val dedupLock = Any()

    // Track if partial result already triggered a save for this recognition cycle
    @Volatile private var partialAlreadySaved = false
    @Volatile private var partialAlreadyVibrated = false
    @Volatile private var confirmedAlreadyVibrated = false
    @Volatile private var pendingPartialDetection: DetectionResult? = null
    @Volatile private var pendingGateDetection: DetectionResult? = null

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

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_LOW,
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1).coerceAtLeast(1)
                    if (level >= 0) {
                        batteryPct = (level * 100) / scale
                    }

                    if (batteryPct >= BATTERY_THRESHOLD) {
                        batteryLowNoticeShown = false
                    }

                    if (batteryPct in 1 until BATTERY_THRESHOLD && listening) {
                        stopForLowBattery("receiver")
                    }
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
        speakerVerificationManager = SherpaSpeakerVerificationManager(applicationContext)
        settingsSyncer = SettingsSyncer(applicationContext)
        asrEngine = createAsrEngine()
        gateAsr = createGateAsr()
        observeSettings()
        registerScreenReceiver()
        registerBatteryReceiver()
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

        if (isBatteryLow()) {
            stopForLowBattery("start")
            return START_NOT_STICKY
        }

        startupJob?.cancel()
        startupJob = lifecycleScope.launch(Dispatchers.IO) {
            loadInitialSettings()
            prewarmAsr()
            initRecognizerAndStart()
            MicCoordinator.sendPause(applicationContext)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        listening = false
        screenOff = false
        stopContextualCapture()
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
        startupJob?.cancel()
        startupJob = null
        recognizer?.destroy()
        recognizer = null

        // Note: MicCoordinator.sendResume is handled by ServiceController.stop()
        // not here, because onDestroy also fires when watch pauses us (and we shouldn't
        // send RESUME back in that case).

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

    private fun registerBatteryReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_BATTERY_LOW)
        }
        registerReceiver(batteryReceiver, filter)

        val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, -1)?.coerceAtLeast(1) ?: 1
        if (level >= 0) {
            batteryPct = (level * 100) / scale
        }
    }

    private suspend fun loadInitialSettings() {
        val patterns = settings.intentPatterns.first()
        val customKw = settings.customKeywords.first()
        val preRollSeconds = settings.contextPreRollSeconds.first()
        val postRollSeconds = settings.contextPostRollSeconds.first()
        val debugEnabled = settings.asrDebugEnabled.first()

        intentDetector.setPatterns(patterns)
        intentDetector.setCustomKeywords(customKw)
        contextPreRollSeconds = preRollSeconds
        contextPostRollSeconds = postRollSeconds
        asrDebugEnabled = debugEnabled
        asrDebugEnabledVolatile = debugEnabled
        updateWhisperHotwords(customKw, patterns)

        Log.i(
            TAG,
            "Initial settings loaded: ${patterns.count { it.enabled }} patterns, ${customKw.size} keywords, " +
                "preRoll=${contextPreRollSeconds}s, postRoll=${contextPostRollSeconds}s"
        )
    }

    private suspend fun prewarmAsr() {
        val warmupWindow = com.trama.shared.audio.CapturedAudioWindow(
            preRollPcm = shortArrayOf(),
            livePcm = ShortArray(16_000),
            sampleRateHz = 16_000
        )

        if (gateAsr.isAvailable) {
            runCatching {
                gateAsr.transcribe(warmupWindow, languageTag = "es")
            }.onSuccess {
                Log.i(TAG, "Gate ASR prewarmed")
            }.onFailure { error ->
                Log.w(TAG, "Gate ASR prewarm failed", error)
            }
        }
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            settings.intentPatterns
                .combine(settings.customKeywords) { patterns, keywords ->
                    patterns to keywords
                }
                .distinctUntilChanged()
                .collect { (patterns, keywords) ->
                    intentDetector.setPatterns(patterns)
                    intentDetector.setCustomKeywords(keywords)
                    Log.i(TAG, "Intent patterns updated: ${patterns.count { it.enabled }} enabled")
                    Log.i(TAG, "Custom keywords updated: ${keywords.size} keywords")

                    updateWhisperHotwords(keywords, patterns)
                    launch(Dispatchers.IO) { settingsSyncer.syncPatterns(patterns, keywords) }
                }
        }
        lifecycleScope.launch {
            settings.contextPreRollSeconds.collect { seconds ->
                contextPreRollSeconds = seconds
                contextualCaptureEngine?.updateConfig(currentContextualConfig())
            }
        }
        lifecycleScope.launch {
            settings.contextPostRollSeconds.collect { seconds ->
                contextPostRollSeconds = seconds
                contextualCaptureEngine?.updateConfig(currentContextualConfig())
            }
        }
        lifecycleScope.launch {
            settings.asrDebugEnabled.collect { enabled ->
                asrDebugEnabled = enabled
                asrDebugEnabledVolatile = enabled
            }
        }
    }

    /**
     * Feed custom keywords + pattern trigger words to Whisper as hotwords.
     * This biases the decoder toward proper nouns and acronyms the user cares about,
     * reducing substitution errors like "CTAG" → "aceptar".
     */
    private fun updateWhisperHotwords(
        customKeywords: List<String>,
        patterns: List<IntentPattern>
    ) {
        val whisper = asrEngine as? SherpaWhisperAsrEngine ?: return
        // Collect pattern label words (e.g. "reunión", place names from triggers)
        val patternWords = patterns
            .filter { it.enabled }
            .flatMap { p -> p.label.split(" ") }
            .filter { it.length >= 3 }
        val all = (customKeywords + patternWords).distinct()
        whisper.setHotwords(all)
    }

    private fun initDatabase() {
        repository = DatabaseProvider.getRepository(applicationContext)
        phoneToWatchSyncer = repository?.let { PhoneToWatchSyncer(applicationContext, it) }
    }

    private fun createAsrEngine(): OnDeviceAsrEngine {
        return try {
            SherpaWhisperAsrEngine(applicationContext).takeIf { it.isAvailable } ?: NoOpAsrEngine()
        } catch (e: Throwable) {
            Log.w(TAG, "Dedicated ASR unavailable, falling back to SpeechRecognizer", e)
            NoOpAsrEngine()
        }
    }

    private fun createGateAsr(): LightweightGateAsr {
        return try {
            VoskGateAsr(applicationContext).takeIf { it.isAvailable } ?: NoOpLightweightGateAsr
        } catch (e: Throwable) {
            Log.w(TAG, "Vosk gate unavailable, will use Whisper directly", e)
            NoOpLightweightGateAsr
        }
    }

    private fun initRecognizerAndStart() {
        if (asrEngine.isAvailable) {
            val status = if (gateAsr.isAvailable) {
                "vosk + whisper"
            } else {
                "asr dedicado"
            }
            publishAsrDebug(engine = "${gateAsr.name} -> ${asrEngine.name}", status = status)
            initContextualCaptureAndStart()
        } else {
            publishAsrDebug(engine = "speechrecognizer", status = "fallback android")
            initSpeechRecognizerAndStart()
        }
    }

    private fun initContextualCaptureAndStart() {
        listening = true
        val captureEngine = ContextualAudioCaptureEngine(
            context = applicationContext,
            initialConfig = currentContextualConfig(),
            gateAsr = gateAsr,
            triggerDetector = { text -> intentDetector.detect(text) != null }
        ).also { engine ->
            engine.onStatusChanged = { state ->
                if (state == "gating" || state == "capturing") {
                    partialAlreadyVibrated = false
                    confirmedAlreadyVibrated = false
                    pendingPartialDetection = null
                    pendingGateDetection = null
                }
                publishAsrDebug(engine = "${gateAsr.name} -> ${asrEngine.name}", status = humanReadableAsrState(state))
                when (state) {
                    "capturing" -> updateNotificationIfChanged("Capturando contexto...")
                    "gating" -> updateNotificationIfChanged("Escuchando (gate ligero)")
                    "trigger_detected" -> updateNotificationIfChanged("Trigger detectado, procesando contexto...")
                    "rearmed" -> updateNotificationIfChanged("Listo para siguiente frase")
                    else -> updateNotificationIfChanged("Escuchando (ASR dedicado)")
                }
            }
            engine.onGateMatch = {
                if (!partialAlreadyVibrated) {
                    vibrate(longArrayOf(0, 35))
                    partialAlreadyVibrated = true
                }
                val detection = intentDetector.detect(it)
                pendingGateDetection = detection
                val reason = detection?.label?.let { label -> "gate -> $label" } ?: "gate -> trigger"
                publishAsrDebug(status = "trigger detectado", gateText = it, triggerReason = reason)
            }
            engine.onGateEvaluated = { transcript, matched, debugSummary ->
                val reason = if (matched) {
                    intentDetector.detect(transcript)?.label?.let { label -> "gate -> $label" } ?: "gate -> trigger"
                } else {
                    "gate descartado"
                }
                publishAsrDebug(
                    status = if (matched) "trigger detectado" else "esperando trigger",
                    gateText = debugSummary.ifBlank { transcript.ifBlank { "sin transcripcion en gate" } },
                    triggerReason = reason
                )
            }
            engine.onWindowCaptured = { window ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val startedAt = System.currentTimeMillis()
                    publishAsrDebug(status = "procesando audio")
                    val transcript = try {
                        asrEngine.transcribe(window, languageTag = "es")
                    } catch (e: Throwable) {
                        Log.e(TAG, "Dedicated ASR failed", e)
                        fallbackToSpeechRecognizer(e)
                        null
                    }

                    val text = transcript?.text?.trim().orEmpty()
                    if (text.isNotBlank()) {
                        val elapsedMs = System.currentTimeMillis() - startedAt
                        val speakerWindow = window
                            .copy(preRollPcm = shortArrayOf())
                            .tailWindow(SPEAKER_VERIFY_WINDOW_MS)
                        val speakerVerification = speakerVerificationManager.verify(speakerWindow)
                        if (!speakerVerification.accepted) {
                            val speakerThreshold = speakerVerificationManager.threshold
                            publishAsrDebug(
                                engine = asrEngine.name,
                                status = "rechazado por voz",
                                lastText = text,
                                triggerReason = "speaker ${"%.2f".format(speakerVerification.similarity)}/${"%.2f".format(speakerThreshold)}"
                            )
                            Log.i(
                                TAG,
                                "Speaker verification rejected capture (sim=${speakerVerification.similarity}, threshold=$speakerThreshold): '$text'"
                            )
                            return@launch
                        }
                        publishAsrDebug(
                            engine = asrEngine.name,
                            status = "ultima captura",
                            lastText = text,
                            triggerReason = "whisper final",
                            lastWindowMs = window.durationMs().toInt(),
                            lastDecodeMs = elapsedMs.toInt()
                        )
                        Log.i(
                            TAG,
                            "ASR[${asrEngine.name}] heard (${window.durationMs()}ms window, " +
                                "${elapsedMs}ms decode): '$text'"
                        )
                        val saved = processText(text)
                        if (!saved) {
                            processPendingGateResult(text)
                        }
                    }
                }
            }
        }

        contextualCaptureEngine = captureEngine
        contextualCaptureJob?.cancel()
        contextualCaptureJob = lifecycleScope.launch(Dispatchers.IO) {
            while (listening && isActive && asrEngine.isAvailable && !dedicatedAsrFailedOver) {
                try {
                    captureEngine.start()
                    if (listening && asrEngine.isAvailable && !dedicatedAsrFailedOver) {
                        Log.w(TAG, "Contextual capture stopped unexpectedly, restarting")
                        publishAsrDebug(status = "rearmando captura")
                        delay(CONTEXTUAL_RESTART_DELAY_MS)
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Contextual capture crashed", t)
                    fallbackToSpeechRecognizer(t)
                    break
                }
            }
        }
    }

    private fun initSpeechRecognizerAndStart() {
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                recognizer?.destroy()
                val onDeviceAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(applicationContext)
                val recognizerAvailable = SpeechRecognizer.isRecognitionAvailable(applicationContext)

                recognizer = if (onDeviceAvailable) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(applicationContext).also {
                        Log.i(TAG, "On-device SpeechRecognizer created")
                    }
                } else if (recognizerAvailable) {
                    if (!hasActiveNetwork()) {
                        Log.w(TAG, "Cloud SpeechRecognizer fallback unavailable without network, waiting")
                        updateNotificationIfChanged("Esperando conexión para fallback")
                        publishAsrDebug(engine = "speechrecognizer", status = "fallback sin red")
                        scheduleFallbackRecognizerInit(adaptiveDelay())
                        return@launch
                    }
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
                publishAsrDebug(engine = "speechrecognizer", status = "escuchando")
                startListening()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create SpeechRecognizer", e)
                updateNotificationIfChanged("Error: no se pudo iniciar")
                stopSelf()
            }
        }
    }

    private fun fallbackToSpeechRecognizer(error: Throwable) {
        if (dedicatedAsrFailedOver || !listening) return
        dedicatedAsrFailedOver = true
        Log.w(TAG, "Falling back to SpeechRecognizer after dedicated ASR failure", error)
        asrEngine = NoOpAsrEngine()
        stopContextualCapture()
        updateNotificationIfChanged("Escuchando (fallback Android)")
        publishAsrDebug(engine = "speechrecognizer", status = "fallback tras error")
        initSpeechRecognizerAndStart()
    }

    private fun startListening() {
        if (!listening) return
        val rec = recognizer ?: return

        partialAlreadySaved = false  // Reset for new cycle
        partialAlreadyVibrated = false
        confirmedAlreadyVibrated = false
        pendingPartialDetection = null
        pendingGateDetection = null
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                speechRecognizerActive = true
                updateNotificationIfChanged("Escuchando...")
            }

            override fun onBeginningOfSpeech() {
                consecutiveSilent = 0
            }

            override fun onRmsChanged(rmsdB: Float) {}

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
                        pendingPartialDetection = result
                        if (!partialAlreadyVibrated) {
                            vibrate(longArrayOf(0, 35))
                            partialAlreadyVibrated = true
                        }
                        publishAsrDebug(
                            status = "trigger parcial",
                            gateText = text,
                            triggerReason = "speech partial -> ${result.label}"
                        )
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
                    val saved = processText(text)
                    if (!saved) {
                        processPendingPartialResult(text)
                    }
                }

                restartListening(RESTART_DELAY_NORMAL_MS)
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
            screenOff && consecutiveSilent >= HIBERNATE_THRESHOLD -> RESTART_DELAY_HIBERNATE_MS
            consecutiveSilent >= HIBERNATE_THRESHOLD -> RESTART_DELAY_DEEP_SLEEP_MS
            consecutiveSilent >= DEEP_SLEEP_THRESHOLD -> RESTART_DELAY_SLOW_MS
            screenOff -> RESTART_DELAY_SLOW_MS
            else -> RESTART_DELAY_NORMAL_MS
        }
    }

    private fun restartListening(delayMs: Long) {
        if (!listening) return

        if (isBatteryLow()) {
            stopForLowBattery("restart")
            return
        }

        lifecycleScope.launch(Dispatchers.Main) {
            delay(delayMs)
            if (listening) {
                startListening()
            }
        }
    }

    private fun scheduleFallbackRecognizerInit(delayMs: Long) {
        if (!listening) return
        lifecycleScope.launch(Dispatchers.Main) {
            delay(delayMs)
            if (listening && dedicatedAsrFailedOver && recognizer == null) {
                initSpeechRecognizerAndStart()
            }
        }
    }

    private fun stopContextualCapture() {
        contextualCaptureEngine?.stop()
        contextualCaptureJob?.cancel()
        contextualCaptureJob = null
        contextualCaptureEngine = null
    }

    private fun currentContextualConfig(): ContextualCaptureConfig {
        return ContextualCaptureConfig(
            preRollSeconds = contextPreRollSeconds,
            postRollSeconds = contextPostRollSeconds,
            silenceStopMs = 1800L,
            gateEvalWindowsMs = listOf(5_000L, 3_000L)
        )
    }

    private fun humanReadableAsrState(state: String): String {
        return when (state) {
            "gating" -> "esperando trigger"
            "capturing" -> "capturando voz"
            "trigger_detected" -> "trigger detectado"
            "rearmed" -> "listo para siguiente frase"
            "stalled" -> "rearmando captura"
            "listening" -> if (gateAsr.isAvailable) "esperando trigger" else "escuchando"
            else -> state
        }
    }

    private fun hasActiveNetwork(): Boolean {
        val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Process transcribed text through IntentDetector.
     * Uses regex patterns for flexible matching of natural speech variations.
     * Now includes speaker verification and LLM validation.
     */
    private fun processText(text: String): Boolean {
        val result = intentDetector.detect(text) ?: return false
        publishAsrDebug(
            gateText = text,
            triggerReason = "detector final -> ${result.label}"
        )
        return processDetectedResult(
            result = result,
            text = text
        )
    }

    private fun processPendingPartialResult(
        finalText: String
    ): Boolean {
        val result = pendingPartialDetection ?: return false
        Log.i(TAG, "Using pending partial detection to preserve recognized reminder")
        publishAsrDebug(
            gateText = finalText,
            triggerReason = "partial latched -> ${result.label}"
        )
        return processDetectedResult(
            result = result.copy(capturedText = finalText, label = result.label),
            text = finalText
        )
    }

    private fun processPendingGateResult(
        finalText: String
    ): Boolean {
        val result = pendingGateDetection ?: return false
        Log.i(TAG, "Using pending gate detection to preserve recognized reminder")
        publishAsrDebug(
            gateText = finalText,
            triggerReason = "gate latched -> ${result.label}"
        )
        return processDetectedResult(
            result = result.copy(capturedText = finalText, label = result.label),
            text = finalText
        )
    }

    private fun processDetectedResult(
        result: DetectionResult,
        text: String
    ): Boolean {
        val now = System.currentTimeMillis()
        val dedupKey = buildDedupKey(result, text)

        // Atomic check-then-reserve: if no duplicate, claim the dedup slot synchronously
        // so any concurrent processDetectedResult sees the claim and bails out.
        synchronized(dedupLock) {
            if (now - lastSavedTime < DEDUP_WINDOW_MS && dedupKey.isNotBlank() && dedupKey == lastSavedDedupKey) {
                publishAsrDebug(status = "duplicado ignorado")
                Log.i(TAG, "Dedup: skipping similar entry within ${DEDUP_WINDOW_MS}ms")
                return false
            }
            lastSavedText = text
            lastSavedDedupKey = dedupKey
            lastSavedTime = now
            partialAlreadySaved = true
        }

        pendingPartialDetection = null
        pendingGateDetection = null

        if (!confirmedAlreadyVibrated) {
            vibrate(longArrayOf(0, 120, 40, 120))
            confirmedAlreadyVibrated = true
        }
        publishAsrDebug(status = "procesando entrada")

        val intentId = result.pattern?.id ?: result.customKeyword ?: "nota"
        Log.i(TAG, "Intent '$intentId' [${result.label}] found in: '${text.take(60)}'")
        Log.i(
            TAG,
            "Detector result [$intentId]: raw='${text.take(160)}' | captured='${result.capturedText.take(160)}'"
        )

        // Correct text via LLM but don't reject — the pattern trigger already confirms intent.
        // Validation (heuristics + LLM) was rejecting short but valid entries like "comprar ajos"
        // because MIN_WORDS=3, even though the user clearly triggered a capture pattern.
        lifecycleScope.launch(Dispatchers.IO) {
            val validation = try {
                entryValidator.validate(result.capturedText)
            } catch (e: Exception) {
                Log.w(TAG, "Validation failed, proceeding without correction", e)
                null
            }

            val correctedText = dictionary.correct(
                validation?.correctedText ?: result.capturedText
            )

            saveEntry(
                intentId = intentId,
                label = result.label,
                text = correctedText,
                originalText = result.capturedText,
                correctedByLLM = validation?.correctedText,
                llmConfidence = validation?.confidence ?: 0.9f,
                wasReviewed = validation?.correctedText != null || validation?.reason?.contains("IA") == true,
                confidence = 0.9f
            )
        }

        return true
    }

    private fun publishAsrDebug(
        engine: String? = null,
        status: String? = null,
        lastText: String? = null,
        gateText: String? = null,
        triggerReason: String? = null,
        lastWindowMs: Int? = null,
        lastDecodeMs: Int? = null
    ) {
        if (!asrDebugEnabledVolatile) return
        lifecycleScope.launch(Dispatchers.IO) {
            settings.updateAsrDebugSnapshot(
                engine = engine,
                status = status,
                lastText = lastText,
                gateText = gateText,
                triggerReason = triggerReason,
                lastWindowMs = lastWindowMs,
                lastDecodeMs = lastDecodeMs
            )
        }
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
            val repo = repository ?: return@launch
            val normalizedCaptured = normalizeTextForDedup(originalText.ifBlank { text })
            val normalizedSaved = normalizeTextForDedup(text)
            val entry = DiaryEntry(
                text = originalText.ifBlank { text },
                keyword = intentId,
                category = label,
                confidence = confidence,
                source = Source.PHONE,
                duration = 0,
                correctedText = text,
                wasReviewedByLLM = wasReviewed,
                llmConfidence = llmConfidence,
                cleanText = null
            )
            val entryId = saveMutex.withLock { repo.withTransaction {
                val latestPending = getLatestPendingOnce()
                if (
                    latestPending != null &&
                    System.currentTimeMillis() - latestPending.createdAt <= PERSISTED_DEDUP_WINDOW_MS
                ) {
                    val latestNormalizedText = normalizeTextForDedup(latestPending.text)
                    val latestNormalizedClean = normalizeTextForDedup(latestPending.cleanText ?: "")
                    val isSameRecentEntry =
                        normalizedCaptured.isNotBlank() && (
                            normalizedCaptured == latestNormalizedText ||
                                normalizedCaptured == latestNormalizedClean
                            ) ||
                            normalizedSaved.isNotBlank() && (
                            normalizedSaved == latestNormalizedText ||
                                normalizedSaved == latestNormalizedClean
                            ) ||
                            isSimilar(normalizedSaved, latestNormalizedText) ||
                            isSimilar(normalizedCaptured, latestNormalizedText)

                    if (isSameRecentEntry) {
                        return@withTransaction null
                    }
                }

                insert(entry)
            } }

            if (entryId == null) {
                Log.i(TAG, "Persisted dedup: skipping recently saved duplicate '$text'")
                publishAsrDebug(status = "duplicado reciente ignorado")
                return@launch
            }
            Log.i(
                TAG,
                "Entry saved: raw='${originalText.ifBlank { text }}' corrected='${text}' " +
                    "(intent: $intentId, label: $label, reviewed: $wasReviewed)"
            )
            publishAsrDebug(status = "entrada guardada")
            showNewEntryNotification(entry)

            phoneToWatchSyncer?.syncUnsentEntries()

            // Fire-and-forget: process entry through AI to extract action metadata
            EntryProcessingState.markProcessing(entryId)
            try {
                val processor = ActionItemProcessor(this@KeywordListenerService)
                processor.process(entryId, text, repo)
            } catch (e: Exception) {
                Log.w(TAG, "ActionItemProcessor failed for entry $entryId", e)
            } finally {
                EntryProcessingState.markFinished(entryId)
            }
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

    private fun buildDedupKey(result: DetectionResult, text: String): String {
        val normalized = normalizeTextForDedup(text)
        if (normalized.isBlank()) return ""

        val stripped = result.pattern
            ?.normalizedTriggers
            ?.mapNotNull { trigger ->
                normalized
                    .removePrefix("$trigger ")
                    .takeIf { it != normalized }
                    ?.trim()
            }
            ?.firstOrNull()
            ?: result.customKeyword
                ?.let { keyword ->
                    val normalizedKeyword = normalizeTextForDedup(keyword)
                    normalized.removePrefix("$normalizedKeyword ").trim()
                }
            ?: normalized

        val meaningful = stripped
            .split(" ")
            .filter { token ->
                token.isNotBlank() && token !in DEDUP_STOPWORDS
            }
            .joinToString(" ")

        return meaningful.ifBlank { normalized }
    }

    private fun normalizeTextForDedup(text: String): String {
        return Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .replace("[^\\p{L}\\p{N}\\s]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
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
            .setContentTitle("Trama")
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

    private fun stopForLowBattery(reason: String) {
        Log.w(TAG, "Battery low ($batteryPct%), stopping listener [$reason]")
        updateNotificationIfChanged("Pausado: batería baja")
        if (!batteryLowNoticeShown) {
            batteryLowNoticeShown = true
            lifecycleScope.launch(Dispatchers.Main) {
                Toast.makeText(
                    applicationContext,
                    "Escucha continua pausada: bateria por debajo del 15%",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        listening = false
        stopSelf()
    }

    private fun showNewEntryNotification(entry: DiaryEntry) {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

        val displayText = entry.displayText
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
