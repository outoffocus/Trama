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
import com.trama.app.MainActivity
import com.trama.app.NotificationConfig
import com.trama.app.R
import com.trama.app.audio.ContextualAudioCaptureEngine
import com.trama.app.audio.ContextualCaptureConfig
import com.trama.app.audio.NoOpAsrEngine
import com.trama.app.audio.OnDeviceAsrEngine
import com.trama.app.audio.SherpaWhisperAsrEngine
import com.trama.app.speech.EntryValidator
import com.trama.app.speech.IntentDetector
import com.trama.app.speech.IntentPattern
import com.trama.app.speech.PersonalDictionary
import com.trama.app.speech.SpeakerEnrollment
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.sqrt

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
        private const val CHANNEL_ID = NotificationConfig.CHANNEL_LISTENER
        private const val NOTIFICATION_ID = NotificationConfig.ID_LISTENER
        private const val NEW_ENTRY_CHANNEL_ID = NotificationConfig.CHANNEL_NEW_ENTRY

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
    private var repository: com.trama.shared.data.DiaryRepository? = null
    private lateinit var settings: SettingsDataStore
    private lateinit var dictionary: PersonalDictionary
    private lateinit var intentDetector: IntentDetector
    private lateinit var entryValidator: EntryValidator
    private lateinit var speakerEnrollment: SpeakerEnrollment
    private var phoneToWatchSyncer: PhoneToWatchSyncer? = null
    private lateinit var settingsSyncer: SettingsSyncer
    private lateinit var asrEngine: OnDeviceAsrEngine
    private var contextualCaptureEngine: ContextualAudioCaptureEngine? = null
    private var contextualCaptureJob: Job? = null
    private var contextPreRollSeconds: Int = SettingsDataStore.DEFAULT_CONTEXT_PRE_ROLL
    private var contextPostRollSeconds: Int = SettingsDataStore.DEFAULT_CONTEXT_POST_ROLL
    private var asrDebugEnabled: Boolean = false

    @Volatile
    private var listening = false

    @Volatile
    private var screenOff = false

    @Volatile
    private var speechRecognizerActive = false

    @Volatile
    private var dedicatedAsrFailedOver = false

    private var consecutiveSilent = 0
    private var batteryCheckCounter = 0
    private var lastNotificationText = ""

    // Deduplication: avoid saving same entry from partial + final results
    private var lastSavedText = ""
    private var lastSavedTime = 0L

    // Track if partial result already triggered a save for this recognition cycle
    private var partialAlreadySaved = false
    private var partialAlreadyVibrated = false
    private var confirmedAlreadyVibrated = false
    private var pendingPartialDetection: DetectionResult? = null

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
        asrEngine = createAsrEngine()
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
        stopContextualCapture()
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
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

    private fun loadSettings() {
        lifecycleScope.launch(Dispatchers.IO) {
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
            }
        }
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

    private fun initRecognizerAndStart() {
        if (asrEngine.isAvailable) {
            publishAsrDebug(engine = asrEngine.name, status = "asr dedicado")
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
            initialConfig = currentContextualConfig()
        ).also { engine ->
            engine.onStatusChanged = { state ->
                publishAsrDebug(engine = asrEngine.name, status = state)
                when (state) {
                    "capturing" -> updateNotificationIfChanged("Capturando contexto...")
                    else -> updateNotificationIfChanged("Escuchando (ASR dedicado)")
                }
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
                        val rmsValues = extractRmsSeries(window)
                        publishAsrDebug(
                            engine = asrEngine.name,
                            status = "ultima captura",
                            lastText = text,
                            lastWindowMs = window.durationMs().toInt(),
                            lastDecodeMs = elapsedMs.toInt()
                        )
                        Log.i(
                            TAG,
                            "ASR[${asrEngine.name}] heard (${window.durationMs()}ms window, " +
                                "${elapsedMs}ms decode): '$text'"
                        )
                        processText(text, rmsValues)
                    }
                }
            }
        }

        contextualCaptureEngine = captureEngine
        contextualCaptureJob?.cancel()
        contextualCaptureJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                captureEngine.start()
            } catch (t: Throwable) {
                Log.e(TAG, "Contextual capture crashed", t)
                fallbackToSpeechRecognizer(t)
            }
        }
    }

    private fun initSpeechRecognizerAndStart() {
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                recognizer?.destroy()
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
                        pendingPartialDetection = result
                        if (!partialAlreadyVibrated) {
                            vibrate(longArrayOf(0, 35))
                            partialAlreadyVibrated = true
                        }
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

    private fun stopContextualCapture() {
        contextualCaptureEngine?.stop()
        contextualCaptureJob?.cancel()
        contextualCaptureJob = null
        contextualCaptureEngine = null
    }

    private fun currentContextualConfig(): ContextualCaptureConfig {
        return ContextualCaptureConfig(
            preRollSeconds = contextPreRollSeconds,
            postRollSeconds = contextPostRollSeconds
        )
    }

    /**
     * Process transcribed text through IntentDetector.
     * Uses regex patterns for flexible matching of natural speech variations.
     * Now includes speaker verification and LLM validation.
     */
    private fun processText(text: String, rmsValuesOverride: List<Double>? = null): Boolean {
        val result = intentDetector.detect(text) ?: return false
        return processDetectedResult(
            result = result,
            text = text,
            rmsValuesOverride = rmsValuesOverride
        )
    }

    private fun processPendingPartialResult(
        finalText: String,
        rmsValuesOverride: List<Double>? = null
    ): Boolean {
        val result = pendingPartialDetection ?: return false
        Log.i(TAG, "Using pending partial detection to preserve recognized reminder")
        return processDetectedResult(
            result = result.copy(capturedText = finalText, label = result.label),
            text = finalText,
            rmsValuesOverride = rmsValuesOverride
        )
    }

    private fun processDetectedResult(
        result: DetectionResult,
        text: String,
        rmsValuesOverride: List<Double>? = null
    ): Boolean {
        pendingPartialDetection = null

        if (!confirmedAlreadyVibrated) {
            vibrate(longArrayOf(0, 120, 40, 120))
            confirmedAlreadyVibrated = true
        }
        publishAsrDebug(status = "procesando entrada")

        // Speaker verification — reject if voice doesn't match enrolled profile
        if (speakerEnrollment.isEnrolled() && speakerEnrollment.isEnabled()) {
            val verification = speakerEnrollment.verify(
                rmsValuesOverride ?: recentRmsValues.toList()
            )
            if (!verification.isMatch) {
                publishAsrDebug(status = "rechazado por voz")
                Log.i(TAG, "Speaker verification failed (sim=${verification.similarity}), rejecting: '${text.take(40)}'")
                return false
            }
        }

        // Deduplication: skip if we just saved something very similar recently
        val now = System.currentTimeMillis()
        if (now - lastSavedTime < DEDUP_WINDOW_MS && isSimilar(text, lastSavedText)) {
            publishAsrDebug(status = "duplicado ignorado")
            Log.i(TAG, "Dedup: skipping similar entry within ${DEDUP_WINDOW_MS}ms")
            return false
        }

        val intentId = result.pattern?.id ?: result.customKeyword ?: "nota"
        Log.i(TAG, "Intent '$intentId' [${result.label}] found in: '${text.take(60)}'")

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

        lastSavedText = text
        lastSavedTime = now
        partialAlreadySaved = true
        return true
    }

    private fun extractRmsSeries(window: com.trama.app.audio.CapturedAudioWindow): List<Double> {
        val pcm = window.mergedPcm()
        if (pcm.isEmpty()) return emptyList()

        val frameSize = 512
        val rmsValues = ArrayList<Double>((pcm.size / frameSize) + 1)
        var index = 0
        while (index < pcm.size) {
            val end = minOf(index + frameSize, pcm.size)
            val rms = calculateRms(pcm, index, end)
            if (rms > 0.0) {
                rmsValues.add(rms)
            }
            index = end
        }
        return rmsValues
    }

    private fun calculateRms(samples: ShortArray, start: Int, end: Int): Double {
        val length = end - start
        if (length <= 0) return 0.0

        var sum = 0.0
        for (index in start until end) {
            val sample = samples[index].toDouble()
            sum += sample * sample
        }
        return sqrt(sum / length)
    }

    private fun publishAsrDebug(
        engine: String? = null,
        status: String? = null,
        lastText: String? = null,
        lastWindowMs: Int? = null,
        lastDecodeMs: Int? = null
    ) {
        if (!asrDebugEnabled && lastText == null) return
        lifecycleScope.launch(Dispatchers.IO) {
            settings.updateAsrDebugSnapshot(
                engine = engine,
                status = status,
                lastText = lastText,
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
            val entryId = repository?.insert(entry) ?: return@launch
            Log.i(TAG, "Entry saved: '$text' (intent: $intentId, label: $label, reviewed: $wasReviewed)")
            publishAsrDebug(status = "entrada guardada")
            showNewEntryNotification(entry)

            phoneToWatchSyncer?.syncUnsentEntries()

            // Fire-and-forget: process entry through AI to extract action metadata
            repository?.let { repo ->
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
