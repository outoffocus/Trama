package com.trama.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.trama.app.audio.ContextualAudioCaptureEngine
import com.trama.app.audio.SherpaWhisperAsrEngine
import com.trama.app.diagnostics.CaptureLog
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.util.Locale

/**
 * Continuous local speech listening service.
 *
 * Uses IntentDetector for flexible regex-based intent matching instead of
 * exact keyword matching.
 *
 * Integrated features:
 * - Lightweight local gate ASR: checks short speech windows before running Whisper
 * - Entry validation: Heuristics + Gemini to validate/correct transcriptions
 *
 * Battery optimizations:
 * - VAD/gate segmentation keeps Whisper off unless speech looks relevant
 * - Slow mode when screen is off
 * - Stops at configurable battery threshold
 * - Deduplicates entries from repeated segment captures
 */
class KeywordListenerService : LifecycleService() {

    companion object {
        private const val TAG = "KeywordListenerService"

        private const val CONTEXTUAL_RESTART_DELAY_MS = 500L
        private const val CONTEXTUAL_CRASH_RESTART_DELAY_MS = 2_000L
        private const val SPEAKER_VERIFY_WINDOW_MS = 3_000L
        private const val UNCERTAIN_GATE_FALLBACK_COOLDOWN_MS = 5L * 60L * 1000L
        private const val UNCERTAIN_GATE_MIN_WINDOW_MS = 2_500L
        private const val UNCERTAIN_GATE_MAX_WINDOW_MS = 15_000L
        private const val BLOCKED_FALLBACK_LOG_INTERVAL_MS = 60_000L
        private const val MEDIA_PLAYBACK_POLL_MS = 2_000L

        private const val BATTERY_THRESHOLD = 15
        private const val SERVICE_HEARTBEAT_MS = 15L * 60L * 1000L

    }

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
    private var mediaPlaybackMonitorJob: Job? = null
    private var serviceHeartbeatJob: Job? = null
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
    private var dedicatedAsrFailedOver = false

    @Volatile
    private var consecutiveOfflineAsrErrors = 0

    @Volatile private var lastUncertainGateFallbackAt = 0L
    @Volatile
    private var batteryPct: Int = 100
    @Volatile
    private var charging = false
    @Volatile
    private var batteryTempC: Float? = null
    @Volatile
    private var batteryVoltageMv: Int? = null
    @Volatile
    private var mediaPlaybackActive = false
    @Volatile
    private var lastBlockedUncertainGateFallbackLogAt = 0L
    @Volatile
    private var batteryLowNoticeShown = false

    private val dedup = DeduplicationManager()
    private val notifier by lazy { ServiceNotifier(this) }
    private val captureSaver by lazy {
        CaptureSaver(
            context = this,
            dedup = dedup,
            notifier = notifier,
            scope = lifecycleScope,
            repoProvider = { repository },
            onStatus = { status -> publishAsrDebug(status = status) },
            onEntrySaved = { phoneToWatchSyncer?.syncUnsentEntries() }
        )
    }

    private val detectionState = DetectionState()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.i(TAG, "Screen off → slow mode")
                    screenOff = true
                    notifier.updateForegroundIfChanged("Escuchando (segundo plano)")
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.i(TAG, "Screen on → fast mode")
                    screenOff = false
                    notifier.updateForegroundIfChanged("Escuchando...")
                }
            }
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_LOW,
                Intent.ACTION_BATTERY_CHANGED -> {
                    updateBatterySnapshot(intent)

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
        logServiceEvent("onCreate")
        notifier.createChannels()
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
        logServiceEvent("onStartCommand", meta = mapOf("startId" to startId, "flags" to flags))
        ServiceWatchdogScheduler.schedule(this, reason = "onStartCommand")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                notifier.foregroundId,
                notifier.buildForeground("Inicializando..."),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(notifier.foregroundId, notifier.buildForeground("Inicializando..."))
        }

        if (!ServiceController.shouldBeRunning(this)) {
            Log.i(TAG, "Service started but user toggled off — stopping")
            logServiceEvent("stop_user_toggle_off", result = CaptureLog.Result.REJECT)
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
            startMediaPlaybackMonitor()
            initRecognizerAndStart()
            MicCoordinator.sendPause(applicationContext)
            startServiceHeartbeat()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        logServiceEvent("onDestroy", result = CaptureLog.Result.REJECT)
        if (ServiceController.shouldBeRunning(this) &&
            ServiceController.suspendReason(this) == ServiceController.SuspendReason.NONE &&
            !isBatteryLow()
        ) {
            ServiceWatchdogScheduler.schedule(this, delayMs = 30_000L, reason = "unexpected_destroy")
        }
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
        mediaPlaybackMonitorJob?.cancel()
        mediaPlaybackMonitorJob = null
        serviceHeartbeatJob?.cancel()
        serviceHeartbeatJob = null
        // Note: MicCoordinator.sendResume is handled by ServiceController.stop()
        // not here, because onDestroy also fires when watch pauses us (and we shouldn't
        // send RESUME back in that case).

        ServiceController.notifyStopped()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        logServiceEvent("onTaskRemoved", result = CaptureLog.Result.REJECT)
        if (ServiceController.shouldBeRunning(this) &&
            ServiceController.suspendReason(this) == ServiceController.SuspendReason.NONE
        ) {
            ServiceWatchdogScheduler.schedule(this, delayMs = 30_000L, reason = "task_removed")
        }
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
        updateBatterySnapshot(sticky)
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
            Log.w(TAG, "Dedicated offline ASR unavailable", e)
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
        if (isMediaPlaybackActiveNow()) {
            listening = true
            handleMediaPlaybackActive(reason = "init")
            return
        }
        if (asrEngine.isAvailable) {
            val status = if (gateAsr.isAvailable) {
                "vosk + whisper"
            } else {
                "asr dedicado"
            }
            publishAsrDebug(engine = "${gateAsr.name} -> ${asrEngine.name}", status = status)
            initContextualCaptureAndStart()
        } else {
            listening = false
            dedicatedAsrFailedOver = true
            notifier.updateForegroundIfChanged("ASR local no disponible")
            publishAsrDebug(engine = "offline", status = "asr local no disponible")
            logServiceEvent(
                "offline_asr_unavailable",
                result = CaptureLog.Result.REJECT
            )
        }
    }

    private fun startMediaPlaybackMonitor() {
        if (mediaPlaybackMonitorJob?.isActive == true) return
        mediaPlaybackMonitorJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                val active = isMediaPlaybackActiveNow()
                if (active != mediaPlaybackActive) {
                    if (active) {
                        handleMediaPlaybackActive(reason = "poll")
                    } else {
                        handleMediaPlaybackInactive()
                    }
                }
                delay(MEDIA_PLAYBACK_POLL_MS)
            }
        }
    }

    private fun isMediaPlaybackActiveNow(): Boolean =
        runCatching {
            getSystemService(AudioManager::class.java)?.isMusicActive == true
        }.getOrDefault(false)

    private fun handleMediaPlaybackActive(reason: String) {
        if (mediaPlaybackActive && reason != "init") return
        mediaPlaybackActive = true
        Log.i(TAG, "External media playback active; pausing listener")
        CaptureLog.event(
            gate = CaptureLog.Gate.SERVICE,
            result = CaptureLog.Result.REJECT,
            text = "media_playback_pause",
            meta = mapOf("reason" to reason)
        )
        publishAsrDebug(status = "pausado por audio de otra app", triggerReason = "media_playback")
        notifier.updateForegroundIfChanged("Pausado por audio externo")
        stopContextualCapture()
    }

    private fun handleMediaPlaybackInactive() {
        mediaPlaybackActive = false
        Log.i(TAG, "External media playback inactive; resuming listener")
        CaptureLog.event(
            gate = CaptureLog.Gate.SERVICE,
            result = CaptureLog.Result.OK,
            text = "media_playback_resume"
        )
        publishAsrDebug(status = "reanudando escucha", triggerReason = "media_playback_clear")
        if (listening && !isBatteryLow()) {
            initRecognizerAndStart()
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
                    detectionState.resetForRearm()
                }
                if (state == "stalled") {
                    logServiceEvent("audio_record_stalled", result = CaptureLog.Result.REJECT)
                }
                publishAsrDebug(engine = "${gateAsr.name} -> ${asrEngine.name}", status = humanReadableAsrState(state))
                when (state) {
                    "capturing" -> notifier.updateForegroundIfChanged("Capturando contexto...")
                    "gating" -> notifier.updateForegroundIfChanged("Escuchando (gate ligero)")
                    "trigger_detected" -> notifier.updateForegroundIfChanged("Trigger detectado, procesando contexto...")
                    "trigger_uncertain" -> notifier.updateForegroundIfChanged("Verificando frase...")
                    "rearmed" -> notifier.updateForegroundIfChanged("Listo para siguiente frase")
                    else -> notifier.updateForegroundIfChanged("Escuchando (ASR dedicado)")
                }
            }
            engine.onGateMatch = {
                val detection = intentDetector.detect(it)
                detectionState.pendingGateDetection = detection
                val reason = detection?.label?.let { label -> "gate -> $label" } ?: "gate -> trigger"
                publishAsrDebug(status = "trigger detectado", gateText = it, triggerReason = reason)
            }
            engine.onGateEvaluated = { transcript, matched, debugSummary ->
                val reason = if (matched) {
                    intentDetector.detect(transcript)?.label?.let { label -> "gate -> $label" } ?: "gate -> trigger"
                } else {
                    "gate descartado"
                }
                CaptureLog.event(
                    gate = CaptureLog.Gate.ASR_GATE,
                    result = if (matched) CaptureLog.Result.OK else CaptureLog.Result.NO_MATCH,
                    text = transcript.ifBlank { null },
                    meta = mapOf(
                        "summary" to debugSummary,
                        "reason" to reason
                    ) + powerSnapshot()
                )
                publishAsrDebug(
                    status = if (matched) "trigger detectado" else "esperando trigger",
                    gateText = debugSummary.ifBlank { transcript.ifBlank { "sin transcripcion en gate" } },
                    triggerReason = reason
                )
            }
            engine.shouldCaptureUnmatchedFinalWindow = { _, _, _ -> false }
            engine.shouldCaptureUnmatchedGateWindow = { windowMs, transcript, debugSummary, isFinal ->
                shouldEscalateUncertainGate(windowMs, transcript, debugSummary, isFinal)
            }
            engine.onSegmentFinalized = { reason, windowMs, droppedSamples, triggerMatched ->
                CaptureLog.event(
                    gate = CaptureLog.Gate.ASR_GATE,
                    result = CaptureLog.Result.OK,
                    text = "segment_finalized",
                    meta = mapOf(
                        "reason" to reason,
                        "windowMs" to windowMs,
                        "droppedSamples" to droppedSamples,
                        "triggerMatched" to triggerMatched
                    )
                )
            }
            engine.onGateEvalSkipped = { reason, speechMs, thresholdMs ->
                CaptureLog.event(
                    gate = CaptureLog.Gate.ASR_GATE,
                    result = CaptureLog.Result.NO_MATCH,
                    text = "gate_eval_skipped",
                    meta = mapOf(
                        "reason" to reason,
                        "speechMs" to speechMs,
                        "thresholdMs" to thresholdMs
                    )
                )
            }
            engine.onWindowCaptured = { window, source ->
                lifecycleScope.launch(Dispatchers.IO) {
                    if (mediaPlaybackActive || isMediaPlaybackActiveNow()) {
                        CaptureLog.event(
                            gate = CaptureLog.Gate.ASR_FINAL,
                            result = CaptureLog.Result.REJECT,
                            text = "media_playback_blocked_window",
                            meta = mapOf(
                                "source" to source,
                                "windowMs" to window.durationMs()
                            ) + powerSnapshot()
                        )
                        publishAsrDebug(status = "audio externo ignorado", triggerReason = "media_playback")
                        return@launch
                    }
                    val startedAt = System.currentTimeMillis()
                    publishAsrDebug(status = "procesando audio")
                    val transcript = try {
                        asrEngine.transcribe(window, languageTag = "es")
                    } catch (e: Throwable) {
                        Log.e(TAG, "Dedicated ASR failed", e)
                        handleOfflineAsrWindowFailure(e, window.durationMs(), source)
                        null
                    }

                    val text = transcript?.text?.trim().orEmpty()
                    if (text.isNotBlank()) {
                        consecutiveOfflineAsrErrors = 0
                        val elapsedMs = System.currentTimeMillis() - startedAt
                        CaptureLog.event(
                            gate = CaptureLog.Gate.ASR_FINAL,
                            result = CaptureLog.Result.OK,
                            text = text,
                            meta = mapOf(
                                "engine" to asrEngine.name,
                                "windowMs" to window.durationMs(),
                                "decodeMs" to elapsedMs,
                                "source" to source
                            ) + powerSnapshot()
                        )
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
                            CaptureLog.event(
                                gate = CaptureLog.Gate.SPEAKER,
                                result = CaptureLog.Result.REJECT,
                                text = text,
                                meta = mapOf(
                                    "sim" to "%.2f".format(speakerVerification.similarity),
                                    "threshold" to "%.2f".format(speakerThreshold)
                                )
                            )
                            return@launch
                        }
                        CaptureLog.event(
                            gate = CaptureLog.Gate.SPEAKER,
                            result = CaptureLog.Result.OK,
                            meta = mapOf("sim" to "%.2f".format(speakerVerification.similarity))
                        )
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
                        val rescued = if (!saved) {
                            processPendingGateResult(text)
                        } else {
                            false
                        }
                        if (!saved && !rescued) {
                            CaptureLog.event(
                                gate = CaptureLog.Gate.INTENT,
                                result = CaptureLog.Result.NO_MATCH,
                                text = text
                            )
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
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Log.e(TAG, "Contextual capture crashed", t)
                    logOfflineAsrRecoverableFailure(
                        state = "contextual_capture_crashed",
                        error = t,
                        meta = mapOf("restartDelayMs" to CONTEXTUAL_CRASH_RESTART_DELAY_MS)
                    )
                    publishAsrDebug(engine = asrEngine.name, status = "rearmando captura")
                    delay(CONTEXTUAL_CRASH_RESTART_DELAY_MS)
                }
            }
        }
    }

    private fun handleOfflineAsrWindowFailure(
        error: Throwable,
        windowMs: Long,
        source: String
    ) {
        consecutiveOfflineAsrErrors += 1
        logOfflineAsrRecoverableFailure(
            state = "offline_asr_window_failed",
            error = error,
            meta = mapOf(
                "windowMs" to windowMs,
                "source" to source,
                "consecutiveErrors" to consecutiveOfflineAsrErrors
            )
        )
        notifier.updateForegroundIfChanged("Rearmando ASR local")
        publishAsrDebug(engine = asrEngine.name, status = "rearmando asr local")
    }

    private fun logOfflineAsrRecoverableFailure(
        state: String,
        error: Throwable,
        meta: Map<String, Any?> = emptyMap()
    ) {
        Log.w(TAG, "Offline ASR recoverable failure: $state", error)
        logServiceEvent(
            state,
            result = CaptureLog.Result.REJECT,
            meta = mapOf(
                "error" to error.javaClass.simpleName,
                "message" to error.message
            ) + meta
        )
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
            // Final eval keeps a short tail (catch trigger word) plus the engine
            // implicitly adds the full window. Intermediate sizes were redundant
            // and cost N extra ASR transcriptions per finalize.
            gateEvalWindowsMs = listOf(3_000L)
        )
    }

    private fun shouldEscalateUncertainGate(
        windowMs: Long,
        gateTranscript: String,
        debugSummary: String,
        isFinal: Boolean
    ): Boolean {
        if (mediaPlaybackActive || isMediaPlaybackActiveNow()) {
            CaptureLog.event(
                gate = CaptureLog.Gate.ASR_GATE,
                result = CaptureLog.Result.NO_MATCH,
                text = "media_playback_gate_blocked",
            meta = mapOf(
                "windowMs" to windowMs,
                "isFinal" to isFinal,
                "summary" to debugSummary
            ) + powerSnapshot()
        )
            return false
        }
        val now = System.currentTimeMillis()
        val decision = UncertainGateFallbackPolicy.decide(
            windowMs = windowMs,
            gateTranscript = gateTranscript,
            nowMs = now,
            lastAllowedAtMs = lastUncertainGateFallbackAt,
            batteryPct = batteryPct,
            charging = charging,
            normalCooldownMs = UNCERTAIN_GATE_FALLBACK_COOLDOWN_MS,
            minWindowMs = UNCERTAIN_GATE_MIN_WINDOW_MS,
            maxWindowMs = UNCERTAIN_GATE_MAX_WINDOW_MS
        )

        if (decision.allowed) {
            lastUncertainGateFallbackAt = now
            CaptureLog.event(
                gate = CaptureLog.Gate.ASR_GATE,
                result = CaptureLog.Result.OK,
                text = gateTranscript.ifBlank { null },
                meta = mapOf(
                    "reason" to "uncertain_gate_fallback",
                    "isFinal" to isFinal,
                    "windowMs" to windowMs,
                    "batteryPct" to batteryPct,
                    "charging" to charging,
                    "cooldownMs" to decision.cooldownMs,
                    "summary" to debugSummary
                ) + powerSnapshot()
            )
            publishAsrDebug(
                status = "verificando frase",
                gateText = debugSummary.ifBlank { "gate incierto" },
                triggerReason = "gate incierto -> whisper"
            )
        } else if (decision.blockedReason == "battery_low" || decision.blockedReason == "cooldown") {
            logBlockedUncertainGateFallback(
                reason = decision.blockedReason,
                windowMs = windowMs,
                isFinal = isFinal,
                cooldownMs = decision.cooldownMs,
                debugSummary = debugSummary
            )
        }

        return decision.allowed
    }

    private fun logBlockedUncertainGateFallback(
        reason: String,
        windowMs: Long,
        isFinal: Boolean,
        cooldownMs: Long,
        debugSummary: String
    ) {
        val now = System.currentTimeMillis()
        if (now - lastBlockedUncertainGateFallbackLogAt < BLOCKED_FALLBACK_LOG_INTERVAL_MS) return
        lastBlockedUncertainGateFallbackLogAt = now
        CaptureLog.event(
            gate = CaptureLog.Gate.ASR_GATE,
            result = CaptureLog.Result.NO_MATCH,
            text = "uncertain_gate_fallback_blocked",
            meta = mapOf(
                "reason" to reason,
                "isFinal" to isFinal,
                "windowMs" to windowMs,
                "batteryPct" to batteryPct,
                "charging" to charging,
                "cooldownMs" to cooldownMs,
                "summary" to debugSummary
            ) + powerSnapshot()
        )
    }

    private fun humanReadableAsrState(state: String): String {
        return when (state) {
            "gating" -> "esperando trigger"
            "capturing" -> "capturando voz"
            "trigger_detected" -> "trigger detectado"
            "trigger_uncertain" -> "verificando frase"
            "rearmed" -> "listo para siguiente frase"
            "stalled" -> "rearmando captura"
            "listening" -> if (gateAsr.isAvailable) "esperando trigger" else "escuchando"
            else -> state
        }
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

    private fun processPendingGateResult(finalText: String): Boolean {
        val result = detectionState.pendingGateDetection ?: return false
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
        CaptureLog.event(
            gate = CaptureLog.Gate.INTENT,
            result = CaptureLog.Result.OK,
            text = text,
            meta = mapOf(
                "label" to result.label,
                "pattern" to (result.pattern?.id ?: "custom")
            )
        )
        when (dedup.tryReserve(result, text)) {
            DeduplicationManager.Reservation.Duplicate -> {
                publishAsrDebug(status = "duplicado ignorado")
                Log.i(TAG, "Dedup: skipping similar entry within ${DeduplicationManager.IN_MEMORY_WINDOW_MS}ms")
                CaptureLog.event(
                    gate = CaptureLog.Gate.DEDUP_MEM,
                    result = CaptureLog.Result.DUP,
                    text = text
                )
                return false
            }
            DeduplicationManager.Reservation.Reserved -> {
                detectionState.partialAlreadySaved = true
            }
        }

        detectionState.clearPending()
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

            captureSaver.save(
                intentId = intentId,
                label = result.label,
                text = correctedText,
                originalText = result.capturedText,
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
        if (!asrDebugEnabledVolatile && status == null) return
        lifecycleScope.launch(Dispatchers.IO) {
            settings.updateAsrDebugSnapshot(
                engine = engine.takeIf { asrDebugEnabledVolatile },
                status = status,
                lastText = lastText.takeIf { asrDebugEnabledVolatile },
                gateText = gateText.takeIf { asrDebugEnabledVolatile },
                triggerReason = triggerReason.takeIf { asrDebugEnabledVolatile },
                lastWindowMs = lastWindowMs.takeIf { asrDebugEnabledVolatile },
                lastDecodeMs = lastDecodeMs.takeIf { asrDebugEnabledVolatile }
            )
        }
    }

    private fun stopForLowBattery(reason: String) {
        Log.w(TAG, "Battery low ($batteryPct%), stopping listener [$reason]")
        logServiceEvent(
            "stop_low_battery",
            result = CaptureLog.Result.REJECT,
            meta = mapOf("reason" to reason)
        )
        notifier.updateForegroundIfChanged("Pausado: batería baja")
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

    private fun startServiceHeartbeat() {
        if (serviceHeartbeatJob?.isActive == true) return
        serviceHeartbeatJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                logServiceEvent("heartbeat")
                ServiceWatchdogScheduler.schedule(
                    this@KeywordListenerService,
                    reason = "heartbeat"
                )
                delay(SERVICE_HEARTBEAT_MS)
            }
        }
    }

    private fun logServiceEvent(
        state: String,
        result: CaptureLog.Result = CaptureLog.Result.OK,
        meta: Map<String, Any?> = emptyMap()
    ) {
        CaptureLog.event(
            gate = CaptureLog.Gate.SERVICE,
            result = result,
            text = state,
            meta = mapOf(
                "listening" to listening,
                "screenOff" to screenOff,
                "contextualJobActive" to (contextualCaptureJob?.isActive == true),
                "batteryPct" to batteryPct,
                "charging" to charging,
                "dedicatedAsrFailedOver" to dedicatedAsrFailedOver
            ) + powerSnapshot() + meta
        )
    }

    private fun updateBatterySnapshot(intent: Intent?) {
        if (intent == null) return
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1).coerceAtLeast(1)
        if (level >= 0) {
            batteryPct = (level * 100) / scale
        }
        charging = intent.isCharging()
        val tempTenthsC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        batteryTempC = tempTenthsC
            .takeIf { it != Int.MIN_VALUE }
            ?.let { it / 10f }
        batteryVoltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE && it > 0 }
    }

    private fun powerSnapshot(): Map<String, Any?> {
        val thermalStatus = currentThermalStatus()
        return mapOf(
            "batteryPct" to batteryPct,
            "charging" to charging,
            "batteryTempC" to batteryTempC?.let { String.format(Locale.US, "%.1f", it) },
            "batteryVoltageMv" to batteryVoltageMv,
            "thermalStatus" to thermalStatus,
            "thermalStatusLabel" to thermalStatusLabel(thermalStatus)
        )
    }

    private fun currentThermalStatus(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            getSystemService(PowerManager::class.java).currentThermalStatus
        }.getOrNull()
    }

    private fun thermalStatusLabel(status: Int?): String? {
        if (status == null) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return when (status) {
            PowerManager.THERMAL_STATUS_NONE -> "none"
            PowerManager.THERMAL_STATUS_LIGHT -> "light"
            PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
            else -> "unknown"
        }
    }

    private fun isBatteryLow(): Boolean {
        val bm = getSystemService(BatteryManager::class.java)
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return level in 1 until BATTERY_THRESHOLD
    }

    private fun Intent.isCharging(): Boolean {
        val status = getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

}
