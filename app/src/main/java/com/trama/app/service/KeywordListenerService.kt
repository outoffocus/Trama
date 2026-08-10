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
import com.trama.app.audio.CaptureProcessingSequencer
import com.trama.app.audio.SherpaWhisperAsrEngine
import com.trama.app.audio.SileroVadFilter
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
import com.trama.app.summary.AsrHallucinationDetector
import com.trama.shared.sync.MicCoordinator
import com.trama.app.sync.PhoneToWatchSyncer
import com.trama.app.sync.SettingsSyncer
import com.trama.shared.data.DatabaseProvider
import com.trama.app.ui.SettingsDataStore
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.Source
import com.trama.shared.speech.IntentDetector.DetectionResult
import com.trama.shared.speech.CaptureIntentRules
import com.trama.shared.speech.CaptureProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
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
 * - Entry validation: local heuristics + optional on-device model
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
        // Exponential backoff for crash restarts. The current run elapsed
        // time is used to reset: if the engine ran cleanly for longer than
        // CONTEXTUAL_CRASH_BACKOFF_RESET_MS before failing, the next delay
        // resets to the first step instead of climbing further.
        private val CONTEXTUAL_CRASH_RESTART_BACKOFF_MS = longArrayOf(
            1_000L,
            5_000L,
            30_000L,
            5L * 60L * 1000L
        )
        private const val CONTEXTUAL_CRASH_BACKOFF_RESET_MS = 60_000L
        private const val SPEAKER_VERIFY_WINDOW_MS = 3_000L
        private const val UNCERTAIN_GATE_FALLBACK_COOLDOWN_MS = 5L * 60L * 1000L
        private const val UNCERTAIN_GATE_MIN_WINDOW_MS = 2_500L
        private const val UNCERTAIN_GATE_MAX_WINDOW_MS = 15_000L
        private const val MAX_ASR_WINDOW_MS = 20_000L
        private const val BLOCKED_FALLBACK_LOG_INTERVAL_MS = 60_000L
        private const val MEDIA_PLAYBACK_POLL_MS = 2_000L
        private const val SHADOW_DEDUP_MS = 60_000L

        private const val BATTERY_THRESHOLD = 15
        // Soft threshold: above the hard stop (15%) but still constrained.
        // Triggers periodic-eval backoff while keeping the service alive.
        private const val BATTERY_SOFT_THRESHOLD = 30
        private const val SERVICE_HEARTBEAT_MS = 15L * 60L * 1000L
        private val WEAK_OWNERSHIP_PREFIXES = listOf(
            "hay que",
            "tenemos que",
            "tienes que",
            "necesitamos"
        )

    }

    private var repository: com.trama.shared.data.DiaryRepository? = null
    private lateinit var settings: SettingsDataStore
    private lateinit var dictionary: PersonalDictionary
    private lateinit var intentDetector: IntentDetector
    private lateinit var shadowIntentDetector: IntentDetector
    @Volatile private var activeCaptureProfile: CaptureProfile = CaptureProfile.STRICT
    @Volatile private var lastShadowText: String = ""
    @Volatile private var lastShadowAt: Long = 0L
    private lateinit var entryValidator: EntryValidator
    private lateinit var speakerVerificationManager: SherpaSpeakerVerificationManager
    private var phoneToWatchSyncer: PhoneToWatchSyncer? = null
    private lateinit var settingsSyncer: SettingsSyncer
    private lateinit var asrEngine: OnDeviceAsrEngine
    private lateinit var gateAsr: LightweightGateAsr
    // Optional Silero VAD pre-filter — null when the asset is missing.
    private var sileroVad: SileroVadFilter? = null
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
    private var latestThermalStatus: Int? = null
    @Volatile
    private var mediaPlaybackActive = false
    @Volatile
    private var lastBlockedUncertainGateFallbackLogAt = 0L
    @Volatile
    private var batteryLowNoticeShown = false
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null

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

    private val captureProcessingSequencer = CaptureProcessingSequencer()

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
        ServiceController.notifyStarting()
        logServiceEvent("onCreate")
        notifier.createChannels()
        initDatabase()
        settings = SettingsDataStore(applicationContext)
        dictionary = PersonalDictionary(applicationContext)
        intentDetector = IntentDetector()
        shadowIntentDetector = IntentDetector()
        entryValidator = EntryValidator(applicationContext)
        speakerVerificationManager = SherpaSpeakerVerificationManager(applicationContext)
        settingsSyncer = SettingsSyncer(applicationContext)
        asrEngine = createAsrEngine()
        gateAsr = createGateAsr()
        sileroVad = SileroVadFilter.tryCreate(applicationContext)
        logSileroVadStatus()
        observeSettings()
        registerScreenReceiver()
        registerBatteryReceiver()
        registerThermalListener()
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
        unregisterThermalListener()
        startupJob?.cancel()
        startupJob = null
        mediaPlaybackMonitorJob?.cancel()
        mediaPlaybackMonitorJob = null
        serviceHeartbeatJob?.cancel()
        serviceHeartbeatJob = null
        // Note: MicCoordinator.sendResume is handled by ServiceController.stop()
        // not here, because onDestroy also fires when watch pauses us (and we shouldn't
        // send RESUME back in that case).

        sileroVad?.release()
        sileroVad = null
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

    private fun registerThermalListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val powerManager = getSystemService(PowerManager::class.java)
        latestThermalStatus = runCatching { powerManager.currentThermalStatus }.getOrNull()
        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            latestThermalStatus = status
            logServiceEvent(
                "thermal_status_changed",
                meta = mapOf(
                    "thermalStatus" to status,
                    "thermalStatusLabel" to thermalStatusLabel(status),
                    "outcome" to if (status >= PowerManager.THERMAL_STATUS_MODERATE) {
                        CaptureLog.CaptureOutcome.CAPTURE_THROTTLED
                    } else {
                        CaptureLog.CaptureOutcome.SERVICE_AVAILABLE
                    }
                )
            )
        }
        runCatching {
            powerManager.addThermalStatusListener(mainExecutor, listener)
            thermalListener = listener
            logServiceEvent(
                "thermal_listener_registered",
                meta = mapOf(
                    "thermalStatus" to latestThermalStatus,
                    "thermalStatusLabel" to thermalStatusLabel(latestThermalStatus)
                )
            )
        }.onFailure { error ->
            logServiceEvent(
                "thermal_listener_failed",
                result = CaptureLog.Result.REJECT,
                meta = mapOf(
                    "error" to error.javaClass.simpleName,
                    "message" to error.message
                )
            )
        }
    }

    private fun unregisterThermalListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val listener = thermalListener ?: return
        runCatching {
            getSystemService(PowerManager::class.java).removeThermalStatusListener(listener)
        }
        thermalListener = null
    }

    private fun logSileroVadStatus() {
        logServiceEvent(
            if (sileroVad != null) "silero_vad_active" else "silero_vad_unavailable",
            result = if (sileroVad != null) CaptureLog.Result.OK else CaptureLog.Result.REJECT,
            meta = mapOf(
                "reason" to if (sileroVad != null) "model_loaded" else "missing_or_init_failed",
                "outcome" to if (sileroVad != null) {
                    CaptureLog.CaptureOutcome.SERVICE_AVAILABLE
                } else {
                    CaptureLog.CaptureOutcome.UNKNOWN
                }
            )
        )
    }

    private suspend fun loadInitialSettings() {
        val patterns = settings.intentPatterns.first()
        val customKw = settings.customKeywords.first()
        val captureProfile = settings.captureProfile.first()
        val preRollSeconds = settings.contextPreRollSeconds.first()
        val postRollSeconds = settings.contextPostRollSeconds.first()
        val debugEnabled = settings.asrDebugEnabled.first()

        intentDetector.setPatterns(patterns)
        intentDetector.setCustomKeywords(customKw)
        intentDetector.setCaptureProfile(captureProfile)
        configureShadowDetector(patterns, customKw, captureProfile)
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
        logServiceEvent(
            state = "capture_config_loaded",
            meta = mapOf(
                "profile" to captureProfile.name,
                "enabledCategories" to patterns.count { it.enabled },
                "explicitPhrases" to patterns.filter { it.enabled }.sumOf { it.normalizedTriggers.size },
                "customKeywords" to customKw.size,
                "presetVersion" to IntentPattern.CURRENT_PRESET_VERSION,
                "preRollSeconds" to contextPreRollSeconds,
                "postRollSeconds" to contextPostRollSeconds
            )
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
                .combine(settings.customKeywords) { patterns, keywords -> patterns to keywords }
                .combine(settings.captureProfile) { (patterns, keywords), profile ->
                    Triple(patterns, keywords, profile)
                }
                .distinctUntilChanged()
                .collect { (patterns, keywords, profile) ->
                    intentDetector.setPatterns(patterns)
                    intentDetector.setCustomKeywords(keywords)
                    intentDetector.setCaptureProfile(profile)
                    configureShadowDetector(patterns, keywords, profile)
                    Log.i(TAG, "Intent patterns updated: ${patterns.count { it.enabled }} enabled")
                    Log.i(TAG, "Custom keywords updated: ${keywords.size} keywords")

                    updateWhisperHotwords(keywords, patterns)
                    launch(Dispatchers.IO) { settingsSyncer.syncPatterns(patterns, keywords, profile) }
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

    private fun configureShadowDetector(
        patterns: List<IntentPattern>,
        customKeywords: List<String>,
        activeProfile: CaptureProfile
    ) {
        activeCaptureProfile = activeProfile
        val shadowProfile = when (activeProfile) {
            CaptureProfile.STRICT -> CaptureProfile.BALANCED
            CaptureProfile.BALANCED -> CaptureProfile.SENSITIVE
            CaptureProfile.SENSITIVE -> CaptureProfile.SENSITIVE
        }
        shadowIntentDetector.setPatterns(patterns)
        shadowIntentDetector.setCustomKeywords(customKeywords)
        shadowIntentDetector.setCaptureProfile(shadowProfile)
    }

    /** Logs what the next more permissive profile would capture without changing behavior. */
    private fun detectGateTrigger(text: String): Boolean {
        if (intentDetector.detect(text) != null) return true
        if (activeCaptureProfile == CaptureProfile.SENSITIVE) return false
        val shadow = shadowIntentDetector.detect(text) ?: return false
        val now = System.currentTimeMillis()
        val normalized = text.trim().lowercase(Locale.getDefault())
        if (normalized != lastShadowText || now - lastShadowAt >= SHADOW_DEDUP_MS) {
            lastShadowText = normalized
            lastShadowAt = now
            CaptureLog.event(
                gate = CaptureLog.Gate.INTENT,
                result = CaptureLog.Result.NO_MATCH,
                text = text,
                meta = mapOf(
                    "shadow" to true,
                    "activeProfile" to activeCaptureProfile.name,
                    "shadowProfile" to when (activeCaptureProfile) {
                        CaptureProfile.STRICT -> CaptureProfile.BALANCED.name
                        CaptureProfile.BALANCED -> CaptureProfile.SENSITIVE.name
                        CaptureProfile.SENSITIVE -> CaptureProfile.SENSITIVE.name
                    },
                    "candidate" to (shadow.pattern?.id ?: shadow.customKeyword ?: "custom"),
                    "trigger" to shadow.matchedTrigger,
                    "confidence" to shadow.confidence,
                    "reasons" to shadow.scoreReasons.joinToString(",")
                )
            )
        }
        return false
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
            ServiceController.notifyFailed()
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
        ServiceController.notifyPaused()
        Log.i(TAG, "Media playback on this device active; pausing listener")
        CaptureLog.event(
            gate = CaptureLog.Gate.SERVICE,
            result = CaptureLog.Result.REJECT,
            text = "media_playback_pause",
            meta = mapOf(
                "reason" to reason,
                "outcome" to CaptureLog.CaptureOutcome.MEDIA_PLAYBACK
            )
        )
        publishAsrDebug(status = "pausado por audio de este dispositivo", triggerReason = "media_playback")
        notifier.updateForegroundIfChanged("Pausado por audio de este dispositivo")
        stopContextualCapture()
    }

    private fun handleMediaPlaybackInactive() {
        mediaPlaybackActive = false
        Log.i(TAG, "Media playback on this device inactive; resuming listener")
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
        ServiceController.notifyListening()
        val captureEngine = ContextualAudioCaptureEngine(
            context = applicationContext,
            initialConfig = currentContextualConfig(),
            gateAsr = gateAsr,
            triggerDetector = ::detectGateTrigger,
            isThrottled = { shouldThrottleCapture() }
        ).also { engine ->
            engine.onStatusChanged = { state ->
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
            engine.onGateMatch = { _, transcript ->
                val detection = intentDetector.detect(transcript)
                val reason = detection?.label?.let { label -> "gate -> $label" } ?: "gate -> trigger"
                publishAsrDebug(status = "trigger detectado", gateText = transcript, triggerReason = reason)
            }
            engine.onGateEvaluated = { captureId, transcript, matched, debugSummary ->
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
                        "captureId" to captureId,
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
            engine.onSegmentFinalized = { captureId, reason, windowMs, droppedSamples, triggerMatched ->
                CaptureLog.event(
                    gate = CaptureLog.Gate.ASR_GATE,
                    result = CaptureLog.Result.OK,
                    text = "segment_finalized",
                    meta = mapOf(
                        "captureId" to captureId,
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
                        "thresholdMs" to thresholdMs,
                        "throttleReason" to throttleReason(),
                        "outcome" to when (reason) {
                            "capture_throttled" -> CaptureLog.CaptureOutcome.CAPTURE_THROTTLED
                            "ambient_backoff", "insufficient_speech" -> CaptureLog.CaptureOutcome.NO_SPEECH
                            else -> CaptureLog.CaptureOutcome.UNKNOWN
                        }
                    ) + powerSnapshot()
                )
            }
            engine.onWindowCaptured = { envelope ->
                lifecycleScope.launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                    captureProcessingSequencer.process capture@{
                    val window = envelope.window
                    val source = envelope.source
                    if (mediaPlaybackActive || isMediaPlaybackActiveNow()) {
                        CaptureLog.event(
                            gate = CaptureLog.Gate.ASR_FINAL,
                            result = CaptureLog.Result.REJECT,
                            text = "media_playback_blocked_window",
                            meta = mapOf(
                                "captureId" to envelope.captureId,
                                "source" to source,
                                "windowMs" to window.durationMs(),
                                "outcome" to CaptureLog.CaptureOutcome.MEDIA_PLAYBACK
                            ) + powerSnapshot()
                        )
                        publishAsrDebug(status = "audio externo ignorado", triggerReason = "media_playback")
                        return@capture
                    }
                    val startedAt = System.currentTimeMillis()
                    publishAsrDebug(status = "procesando audio")
                    // Hard cap on Whisper input: p95 windows of 45s drove 19s decodes.
                    // Action items live near the end of the segment after the trigger,
                    // so we keep the 20s tail (covers post-roll + final utterance).
                    val asrWindow = if (window.durationMs() > MAX_ASR_WINDOW_MS) {
                        window.tailWindow(MAX_ASR_WINDOW_MS)
                    } else {
                        window
                    }
                    // Pre-Whisper Silero VAD gate: skip the decode entirely on
                    // music/silence/non-speech. Diagnostics showed ~43% of
                    // Whisper finals were bracket-only outputs ("[Música]",
                    // "(Puerto)") — pure waste of CPU. The filter degrades to
                    // a no-op when the model asset is missing.
                    val sileroFilter = sileroVad
                    val sileroDecision = sileroFilter?.evaluate(asrWindow)
                    if (sileroDecision != null) {
                        CaptureLog.event(
                            gate = CaptureLog.Gate.ACOUSTIC_SPEECH,
                            result = if (sileroDecision.containsSpeech) {
                                CaptureLog.Result.OK
                            } else {
                                CaptureLog.Result.REJECT
                            },
                            text = sileroDecision.reason,
                            meta = mapOf(
                                "captureId" to envelope.captureId,
                                "source" to source,
                                "windowMs" to asrWindow.durationMs(),
                                "vadMs" to sileroDecision.elapsedMs,
                                "outcome" to if (sileroDecision.containsSpeech) {
                                    CaptureLog.CaptureOutcome.INTENT_CANDIDATE
                                } else {
                                    CaptureLog.CaptureOutcome.NO_SPEECH
                                }
                            ) + powerSnapshot()
                        )
                    }
                    if (sileroDecision != null && !sileroDecision.containsSpeech) {
                        publishAsrDebug(status = "sin habla detectada", triggerReason = "silero_vad")
                        return@capture
                    }
                    val transcript = try {
                        asrEngine.transcribe(asrWindow, languageTag = "es")
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
                                "captureId" to envelope.captureId,
                                "engine" to asrEngine.name,
                                "windowMs" to window.durationMs(),
                                "decodeMs" to elapsedMs,
                                "source" to source
                            ) + powerSnapshot()
                        )
                        val asrNoiseReason = AsrHallucinationDetector.detect(
                            text,
                            singleWordIsHallucination = false
                        )
                        if (asrNoiseReason != null) {
                            publishAsrDebug(
                                status = "audio ambiental filtrado",
                                lastText = text,
                                triggerReason = asrNoiseReason
                            )
                            CaptureLog.event(
                                gate = CaptureLog.Gate.INTENT,
                                result = CaptureLog.Result.NO_MATCH,
                                text = text.take(160),
                                meta = mapOf(
                                    "captureId" to envelope.captureId,
                                    "rejectStage" to "AsrSanity",
                                    "intentReason" to asrNoiseReason,
                                    "outcome" to CaptureLog.CaptureOutcome.NO_INTENT
                                )
                            )
                            return@capture
                        }
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
                                triggerReason = "speaker ${metric(speakerVerification.similarity)}/${metric(speakerThreshold)}"
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
                                    "captureId" to envelope.captureId,
                                    "sim" to metric(speakerVerification.similarity),
                                    "threshold" to metric(speakerThreshold),
                                    "speakerReason" to speakerVerification.reason,
                                    "rejectStage" to "SpeakerOwnership",
                                    "outcome" to CaptureLog.CaptureOutcome.NOT_OWNER
                                )
                            )
                            return@capture
                        }
                        CaptureLog.event(
                            gate = CaptureLog.Gate.SPEAKER,
                            result = CaptureLog.Result.OK,
                            meta = mapOf(
                                "captureId" to envelope.captureId,
                                "sim" to metric(speakerVerification.similarity),
                                "speakerReason" to speakerVerification.reason,
                                "speakerConfigured" to speakerVerificationManager.isConfigured,
                                "speakerEnabled" to speakerVerificationManager.isEnabled
                            )
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
                        // An optional voice profile supplies extra evidence; its
                        // absence is neutral. Only an inconclusive check from an
                        // enabled/configured profile should demote the capture.
                        // A real mismatch was already rejected above.
                        val speakerEvidenceRequiresReview =
                            speakerVerificationManager.isConfigured &&
                                speakerVerificationManager.isEnabled &&
                                speakerVerification.reason in setOf(
                                    "backend_unavailable",
                                    "too_short",
                                    "embedding_failed"
                                )
                        val saved = processText(
                            text = text,
                            preferSuggested = speakerEvidenceRequiresReview,
                            captureId = envelope.captureId
                        )
                        val rescued = if (!saved) {
                            processGateResult(
                                gateText = envelope.gateTranscript,
                                finalText = text,
                                preferSuggested = speakerEvidenceRequiresReview,
                                captureId = envelope.captureId
                            )
                        } else {
                            false
                        }
                        if (!saved && !rescued) {
                            CaptureLog.event(
                                gate = CaptureLog.Gate.INTENT,
                                result = CaptureLog.Result.NO_MATCH,
                                text = text,
                                meta = mapOf(
                                    "captureId" to envelope.captureId,
                                    "rejectStage" to "IntentCandidate",
                                    "intentReason" to "no_final_intent",
                                    "outcome" to CaptureLog.CaptureOutcome.NO_INTENT
                                )
                            )
                        }
                    }
                    }
                }
            }
        }

        contextualCaptureEngine = captureEngine
        contextualCaptureJob?.cancel()
        contextualCaptureJob = lifecycleScope.launch(Dispatchers.IO) {
            var crashStreak = 0
            while (listening && isActive && asrEngine.isAvailable && !dedicatedAsrFailedOver) {
                val runStartedAt = System.currentTimeMillis()
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
                    val ranForMs = System.currentTimeMillis() - runStartedAt
                    if (ranForMs >= CONTEXTUAL_CRASH_BACKOFF_RESET_MS) {
                        crashStreak = 0
                    }
                    val delayMs = CONTEXTUAL_CRASH_RESTART_BACKOFF_MS[
                        crashStreak.coerceAtMost(CONTEXTUAL_CRASH_RESTART_BACKOFF_MS.size - 1)
                    ]
                    Log.e(TAG, "Contextual capture crashed (streak=${crashStreak + 1}, ranForMs=$ranForMs)", t)
                    logOfflineAsrRecoverableFailure(
                        state = "contextual_capture_crashed",
                        error = t,
                        meta = mapOf(
                            "restartDelayMs" to delayMs,
                            "crashStreak" to (crashStreak + 1),
                            "ranForMs" to ranForMs
                        )
                    )
                    crashStreak += 1
                    publishAsrDebug(engine = asrEngine.name, status = "rearmando captura")
                    delay(delayMs)
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
        // Truncate stacktrace to keep diagnostic events bounded; full trace is
        // still in logcat. 2 KB is enough for ~25 frames of Kotlin/JVM stack.
        val stack = Log.getStackTraceString(error).take(2_000)
        val cause = error.cause?.let { "${it.javaClass.simpleName}: ${it.message}" }
        logServiceEvent(
            state,
            result = CaptureLog.Result.REJECT,
            meta = mapOf(
                "error" to error.javaClass.simpleName,
                "message" to error.message,
                "cause" to cause,
                "stack" to stack
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
        val powerSaveReason = throttleReason().takeIf { it != "none" }
        val decision = UncertainGateFallbackPolicy.decide(
            windowMs = windowMs,
            gateTranscript = gateTranscript,
            nowMs = now,
            lastAllowedAtMs = lastUncertainGateFallbackAt,
            batteryPct = batteryPct,
            charging = charging,
            powerSaveReason = powerSaveReason,
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
        } else if (decision.blockedReason == "battery_low" ||
            decision.blockedReason == "cooldown" ||
            decision.blockedReason?.startsWith("thermal:") == true ||
            decision.blockedReason == "battery_soft_low"
        ) {
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
    private fun processText(text: String, preferSuggested: Boolean, captureId: Long): Boolean {
        val result = intentDetector.detect(text) ?: return false
        publishAsrDebug(
            gateText = text,
            triggerReason = "detector final -> ${result.label}"
        )
        return processDetectedResult(
            result = result,
            text = text,
            preferSuggested = preferSuggested,
            captureId = captureId
        )
    }

    private fun processGateResult(
        gateText: String?,
        finalText: String,
        preferSuggested: Boolean,
        captureId: Long
    ): Boolean {
        val result = gateText?.let(intentDetector::detect) ?: return false
        Log.i(TAG, "Using gate detection correlated with the final capture")
        publishAsrDebug(
            gateText = finalText,
            triggerReason = "gate latched -> ${result.label}"
        )
        return processDetectedResult(
            result = result.copy(capturedText = finalText, label = result.label),
            text = finalText,
            preferSuggested = preferSuggested,
            captureId = captureId
        )
    }

    private fun processDetectedResult(
        result: DetectionResult,
        text: String,
        preferSuggested: Boolean = false,
        captureId: Long
    ): Boolean {
        val acceptance = IntentAcceptancePolicy.evaluate(
            detectorConfidence = result.confidence,
            ownerVerified = !preferSuggested,
            weakGrammaticalOwnership = result.shouldRouteToSuggested()
        )
        val routeToSuggested = acceptance.routeToSuggested
        CaptureLog.event(
            gate = CaptureLog.Gate.INTENT,
            result = CaptureLog.Result.OK,
            text = text,
            meta = mapOf(
                "captureId" to captureId,
                "label" to result.label,
                "pattern" to (result.pattern?.id ?: "custom"),
                "trigger" to result.matchedTrigger,
                "detectorConfidence" to metric(result.confidence),
                "scoreReasons" to result.scoreReasons.joinToString(","),
                "acceptanceReasons" to acceptance.reasons.joinToString(","),
                "preferSuggested" to routeToSuggested,
                "outcome" to CaptureLog.CaptureOutcome.INTENT_CANDIDATE
            )
        )
        when (dedup.tryReserve(result, text)) {
            DeduplicationManager.Reservation.Duplicate -> {
                publishAsrDebug(status = "duplicado ignorado")
                Log.i(TAG, "Dedup: skipping similar entry within ${DeduplicationManager.IN_MEMORY_WINDOW_MS}ms")
                CaptureLog.event(
                    gate = CaptureLog.Gate.DEDUP_MEM,
                    result = CaptureLog.Result.DUP,
                    text = text,
                    meta = mapOf(
                        "captureId" to captureId,
                        "outcome" to CaptureLog.CaptureOutcome.DUPLICATE
                    )
                )
                return false
            }
            DeduplicationManager.Reservation.Reserved -> Unit
        }

        publishAsrDebug(status = "procesando entrada")

        val fallbackClassification = CaptureIntentRules.Classification(
            id = result.pattern?.id ?: result.customKeyword ?: "nota",
            label = result.label
        )
        val classification = if (result.customKeyword != null) {
            fallbackClassification
        } else {
            CaptureIntentRules.classify(result.capturedText, fallbackClassification)
        }
        val intentId = classification.id
        val classifiedLabel = classification.label
        Log.i(TAG, "Intent '$intentId' [$classifiedLabel] found in: '${text.take(60)}'")
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
                label = classifiedLabel,
                text = correctedText,
                originalText = result.capturedText,
                llmConfidence = validation?.confidence ?: 0.9f,
                wasReviewed = validation?.correctedText != null || validation?.reason?.contains("IA") == true,
                confidence = result.confidence,
                preferSuggested = routeToSuggested,
                suggestedReasons = acceptance.reasons
            )
        }

        return true
    }

    private fun DetectionResult.shouldRouteToSuggested(): Boolean {
        val normalized = capturedText.lowercase(Locale.getDefault())
        if (scoreReasons.contains("weak_ownership")) return true
        if (pattern?.id == "tareas" && WEAK_OWNERSHIP_PREFIXES.any { normalized.contains(it) }) {
            return true
        }
        return false
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
                "dedicatedAsrFailedOver" to dedicatedAsrFailedOver,
                "throttleReason" to throttleReason()
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
        latestThermalStatus?.let { return it }
        return runCatching {
            getSystemService(PowerManager::class.java).currentThermalStatus
        }.getOrNull()?.also { latestThermalStatus = it }
    }

    /**
     * True when the device is under thermal pressure that warrants suspending
     * periodic gate ASR evals. MODERATE is the first state where Android asks
     * apps to back off; SEVERE/CRITICAL/EMERGENCY also throttle.
     */
    private fun isThermallyThrottled(): Boolean {
        val status = currentThermalStatus() ?: return false
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            status >= PowerManager.THERMAL_STATUS_MODERATE
    }

    /**
     * True when battery is below the soft threshold and the device is not
     * charging. Triggers the same backoff as thermal throttling.
     */
    private fun isBatteryConstrained(): Boolean {
        if (charging) return false
        val pct = batteryPct
        return pct in 1..BATTERY_SOFT_THRESHOLD
    }

    /** Combined signal used by the capture engine to skip periodic gate evals. */
    private fun shouldThrottleCapture(): Boolean =
        isThermallyThrottled() || isBatteryConstrained()

    private fun throttleReason(): String {
        val thermalStatus = currentThermalStatus()
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                thermalStatus != null &&
                thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE ->
                "thermal:${thermalStatusLabel(thermalStatus)}"
            isBatteryConstrained() -> "battery_soft_low"
            else -> "none"
        }
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

    private fun metric(value: Number): String =
        String.format(Locale.US, "%.2f", value.toDouble())

}
