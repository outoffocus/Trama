package com.trama.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.trama.app.diagnostics.CaptureLog
import com.trama.app.sync.SettingsSyncer
import com.trama.app.ui.SettingsDataStore
import com.trama.shared.sync.MicCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

object ServiceController {

    enum class CaptureMode {
        IDLE,
        STARTING,
        LISTENING,
        TRIGGER_RECOGNIZED,
        PAUSED,
        RECORDING,
        PROCESSING,
        WATCH,
        TRANSFERRING,
        ERROR
    }

    data class CaptureUiState(
        val mode: CaptureMode = CaptureMode.IDLE,
        val listeningActive: Boolean = false,
        val recording: Boolean = false,
        val processing: Boolean = false,
        val watchActive: Boolean = false,
        val transferring: Boolean = false,
        val triggerRecognized: Boolean = false,
        val elapsedSeconds: Long = 0L
    )

    private const val PREFS = "service_prefs"
    private const val KEY_SHOULD_RUN = "should_run"
    private const val KEY_SUSPEND_REASON = "suspend_reason"

    enum class SuspendReason {
        NONE,
        RECORDING,
        WATCH
    }

    /**
     * Runtime state reported by [KeywordListenerService].
     *
     * `shouldBeRunning()` remains the persisted user preference. This state is
     * deliberately runtime-only: after process recreation the service itself
     * must report that it has started again instead of the UI assuming it did.
     */
    enum class ListenerState {
        STOPPED,
        STARTING,
        LISTENING,
        PAUSED,
        FAILED
    }

    private enum class ServiceMode {
        IDLE,
        LISTENING,
        RECORDING,
        WATCH
    }

    private const val START_DEBOUNCE_MS = 1_500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val transitionLock = Any()
    private val modeRef = AtomicReference(ServiceMode.IDLE)

    @Volatile
    private var lastStartRequestMs: Long = 0L
    @Volatile
    private var lastStartReason: String = ""

    private fun shouldSuppressStart(reason: String): Boolean {
        val now = System.currentTimeMillis()
        val elapsed = now - lastStartRequestMs
        if (elapsed in 0 until START_DEBOUNCE_MS) {
            CaptureLog.event(
                gate = CaptureLog.Gate.SERVICE,
                result = CaptureLog.Result.REJECT,
                text = "service_start_debounced",
                meta = mapOf(
                    "reason" to reason,
                    "previousReason" to lastStartReason,
                    "elapsedMs" to elapsed
                )
            )
            return true
        }
        lastStartRequestMs = now
        lastStartReason = reason
        return false
    }

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _listenerState = MutableStateFlow(ListenerState.STOPPED)
    val listenerState: StateFlow<ListenerState> = _listenerState.asStateFlow()

    private val _isTriggerRecognized = MutableStateFlow(false)
    val isTriggerRecognized: StateFlow<Boolean> = _isTriggerRecognized.asStateFlow()

    private val _continuousListeningEnabled = MutableStateFlow(false)
    val continuousListeningEnabled: StateFlow<Boolean> =
        _continuousListeningEnabled.asStateFlow()

    private val _isWatchActive = MutableStateFlow(false)
    val isWatchActive: StateFlow<Boolean> = _isWatchActive.asStateFlow()

    private val _isTransferring = MutableStateFlow(false)
    val isTransferring: StateFlow<Boolean> = _isTransferring.asStateFlow()

    private data class ListenerRuntime(
        val state: ListenerState,
        val trigger: Boolean,
        val watch: Boolean,
        val enabled: Boolean
    )

    private data class RecordingRuntime(
        val recording: Boolean,
        val processing: Boolean,
        val elapsedSeconds: Long,
        val error: String?
    )

    private val listenerRuntime = combine(
        _listenerState,
        _isTriggerRecognized,
        _isWatchActive,
        _continuousListeningEnabled
    ) { state, trigger, watch, enabled -> ListenerRuntime(state, trigger, watch, enabled) }

    private val recordingRuntime = combine(
        RecordingState.isRecording,
        RecordingState.isProcessing,
        RecordingState.elapsedSeconds,
        RecordingState.lastError
    ) { recording, processing, elapsed, error ->
        RecordingRuntime(recording, processing, elapsed, error)
    }

    val captureState: StateFlow<CaptureUiState> = combine(
        listenerRuntime,
        recordingRuntime,
        _isTransferring
    ) { listener, recording, transferring ->
        resolveCaptureUiState(
            listenerState = listener.state,
            listeningEnabled = listener.enabled,
            triggerRecognized = listener.trigger,
            recording = recording.recording,
            processing = recording.processing,
            watchActive = listener.watch,
            transferring = transferring,
            elapsedSeconds = recording.elapsedSeconds,
            hasError = recording.error != null
        )
    }.stateIn(scope, SharingStarted.Eagerly, CaptureUiState())

    private val _isLocationRunning = MutableStateFlow(false)
    val isLocationRunning: StateFlow<Boolean> = _isLocationRunning.asStateFlow()

    /**
     * Start keyword listening. Stops recording if active (modes are exclusive).
     */
    fun start(context: Context) {
        synchronized(transitionLock) {
            if (RecordingState.isRecording.value) {
                RecordingState.stopRecording(context)
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SHOULD_RUN, true)
                .putString(KEY_SUSPEND_REASON, SuspendReason.NONE.name)
                .commit()
            _continuousListeningEnabled.value = true
            if (shouldSuppressStart("user_start")) return
            ServiceWatchdogScheduler.schedule(context, reason = "user_start")
            CaptureLog.event(
                gate = CaptureLog.Gate.SERVICE,
                result = CaptureLog.Result.OK,
                text = "service_start_requested"
            )
            val intent = Intent(context, KeywordListenerService::class.java)
            requestListenerStart(context, intent, reason = "user_start")
            scope.launch {
                syncSettingsToWatch(context, continuousListeningEnabled = true)
            }
            _isWatchActive.value = false
            modeRef.set(ServiceMode.LISTENING)
        }
    }

    /**
     * Stop keyword service (user-initiated). Does NOT notify the watch —
     * use transferToWatch() to hand control to the watch explicitly.
     */
    fun stop(context: Context, reason: String = "user_stop") {
        synchronized(transitionLock) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SHOULD_RUN, false)
                .putString(KEY_SUSPEND_REASON, SuspendReason.NONE.name)
                .commit()
            _continuousListeningEnabled.value = false
            ServiceWatchdogScheduler.cancel(context)
            ListenerRecoveryNotifier.cancel(context)
            CaptureLog.event(
                gate = CaptureLog.Gate.SERVICE,
                result = CaptureLog.Result.OK,
                text = "service_stop_requested",
                meta = mapOf("reason" to reason)
            )
            val intent = Intent(context, KeywordListenerService::class.java)
            context.stopService(intent)
            updateListenerState(ListenerState.STOPPED)
            if (!RecordingState.isRecording.value && !_isWatchActive.value) {
                modeRef.set(ServiceMode.IDLE)
            }
        }
    }

    /**
     * Disable background keyword listening everywhere without touching manual
     * recordings, calendar sync or location tracking.
     *
     * Unlike [stop], this also asks a paired watch to release continuous
     * listening. Manual recording remains available on both devices.
     */
    fun disableContinuousListening(context: Context, reason: String = "settings_disabled") {
        stop(context, reason)
        scope.launch {
            syncSettingsToWatch(context, continuousListeningEnabled = false)
            MicCoordinator.sendDisableContinuous(context.applicationContext)
            notifyWatchInactive()
        }
    }

    fun rearm(context: Context, reason: String = "user_rearm") {
        synchronized(transitionLock) {
            if (RecordingState.isRecording.value) return
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SHOULD_RUN, true)
                .putString(KEY_SUSPEND_REASON, SuspendReason.NONE.name)
                .commit()
            _continuousListeningEnabled.value = true
            if (shouldSuppressStart("rearm:$reason")) return
            ServiceWatchdogScheduler.schedule(context, reason = reason)
            CaptureLog.event(
                gate = CaptureLog.Gate.SERVICE,
                result = CaptureLog.Result.OK,
                text = "service_rearm_requested",
                meta = mapOf("reason" to reason)
            )
            val intent = Intent(context, KeywordListenerService::class.java)
                .putExtra("rearmReason", reason)
            requestListenerStart(context, intent, reason = "rearm:$reason")
            _isWatchActive.value = false
            modeRef.set(ServiceMode.LISTENING)
        }
    }

    fun startLocationTracking(context: Context) {
        val intent = Intent(context, LocationForegroundService::class.java)
        ContextCompat.startForegroundService(context, intent)
        _isLocationRunning.value = true
    }

    fun stopLocationTracking(context: Context) {
        context.stopService(Intent(context, LocationForegroundService::class.java))
        _isLocationRunning.value = false
    }

    /**
     * Start recording. Stops keyword listener if active (modes are exclusive).
     */
    fun startRecording(context: Context) {
        synchronized(transitionLock) {
            pauseListeningForRecordingLocked(context, source = "service_controller_start_recording")
            _isWatchActive.value = false
            modeRef.set(ServiceMode.RECORDING)
            RecordingState.startRecording(context)
        }
    }

    /**
     * Pause the listener before taking the microphone for an offline recording.
     *
     * The persisted should_run flag is intentionally treated as authoritative here:
     * after process recreation, _isRunning can be false while the user-facing
     * listening mode is still expected to resume when recording finishes.
     */
    fun pauseListeningForRecording(
        context: Context,
        source: String = "recording_service_start"
    ): Boolean = synchronized(transitionLock) {
        pauseListeningForRecordingLocked(context, source)
    }

    private fun pauseListeningForRecordingLocked(context: Context, source: String): Boolean {
        val shouldResumeListening = _isRunning.value || shouldBeRunning(context)
        if (!shouldResumeListening) {
            CaptureLog.event(
                gate = CaptureLog.Gate.SERVICE,
                result = CaptureLog.Result.OK,
                text = "recording_started_without_listener_resume",
                meta = mapOf(
                    "source" to source,
                    "isRunning" to _isRunning.value,
                    "shouldBeRunning" to false
                )
            )
            return false
        }
        if (!_isRunning.value && suspendReason(context) == SuspendReason.WATCH) {
            CaptureLog.event(
                gate = CaptureLog.Gate.SERVICE,
                result = CaptureLog.Result.OK,
                text = "phone_mic_used_while_watch_listens",
                meta = mapOf("source" to source)
            )
            return false
        }

        setSuspendReason(context, SuspendReason.RECORDING)
        context.stopService(Intent(context, KeywordListenerService::class.java))
        updateListenerState(ListenerState.STOPPED)
        CaptureLog.event(
            gate = CaptureLog.Gate.SERVICE,
            result = CaptureLog.Result.OK,
            text = "listener_paused_for_recording",
            meta = mapOf(
                "source" to source,
                "shouldResumeListening" to true
            )
        )
        return true
    }

    /** Resume only a listener that this process suspended to release the phone microphone. */
    fun resumeListeningAfterRecording(context: Context, source: String = "recording_finished") {
        synchronized(transitionLock) {
            if (!shouldBeRunning(context) || suspendReason(context) != SuspendReason.RECORDING) {
                CaptureLog.event(
                    gate = CaptureLog.Gate.SERVICE,
                    result = CaptureLog.Result.OK,
                    text = "listener_resume_skipped",
                    meta = mapOf("source" to source, "suspendReason" to suspendReason(context).name)
                )
                return
            }
            setSuspendReason(context, SuspendReason.NONE)
            ServiceWatchdogScheduler.schedule(context, reason = "resume:$source")
            val intent = Intent(context, KeywordListenerService::class.java)
            if (requestListenerStart(context, intent, reason = "resume:$source")) {
                modeRef.set(ServiceMode.LISTENING)
            } else {
                modeRef.set(ServiceMode.IDLE)
            }
        }
    }

    /**
     * Stop service because watch took over mic. Does NOT clear should_run,
     * so the service can resume when watch releases.
     */
    fun stopByWatch(context: Context) {
        synchronized(transitionLock) {
            setSuspendReason(context, SuspendReason.WATCH)
            context.stopService(Intent(context, KeywordListenerService::class.java))
            updateListenerState(ListenerState.STOPPED)
            if (RecordingState.isRecording.value) {
                RecordingState.stopRecording(context)
            }
            modeRef.set(ServiceMode.WATCH)
        }
    }

    /**
     * Transfer active mode to watch. Stops everything locally.
     * Does NOT clear should_run so phone can auto-resume if watch returns control.
     */
    fun transferToWatch(context: Context, onResult: ((Boolean) -> Unit)? = null) {
        synchronized(transitionLock) {
            if (_isTransferring.value) return
            _isTransferring.value = true
            val wasListening = _isRunning.value
            val wasRecording = RecordingState.isRecording.value

            if (_isRunning.value) {
                setSuspendReason(context, SuspendReason.WATCH)
                context.stopService(Intent(context, KeywordListenerService::class.java))
                updateListenerState(ListenerState.STOPPED)
            }
            if (wasRecording) {
                RecordingState.stopRecording(context)
            }

            scope.launch {
                syncSettingsToWatch(context)
                val sent = if (wasRecording) {
                    MicCoordinator.sendStartRecording(context)
                } else {
                    MicCoordinator.sendStartKeyword(context)
                }

                if (!sent) {
                    restoreAfterFailedWatchTransfer(context, wasListening)
                    _isTransferring.value = false
                    onResult?.invoke(false)
                    return@launch
                }

                delay(6_000)
                if (_isWatchActive.value) {
                    _isTransferring.value = false
                    onResult?.invoke(true)
                } else {
                    restoreAfterFailedWatchTransfer(context, wasListening)
                    _isTransferring.value = false
                    onResult?.invoke(false)
                }
            }
        }
    }

    /** Return keyword listening to the phone after the watch had control. */
    fun reclaimFromWatch(context: Context, onResult: ((Boolean) -> Unit)? = null) {
        if (_isTransferring.value) return
        _isTransferring.value = true
        scope.launch {
            try {
                val watchReached = MicCoordinator.sendPause(context)
                notifyWatchInactive()
                start(context)
                onResult?.invoke(watchReached)
            } finally {
                _isTransferring.value = false
            }
        }
    }

    private suspend fun syncSettingsToWatch(context: Context) {
        syncSettingsToWatch(
            context = context,
            continuousListeningEnabled = shouldBeRunning(context)
        )
    }

    private suspend fun syncSettingsToWatch(
        context: Context,
        continuousListeningEnabled: Boolean
    ) {
        val appContext = context.applicationContext
        val settings = SettingsDataStore(appContext)
        runCatching {
            SettingsSyncer(appContext).syncPatterns(
                patterns = settings.intentPatterns.first(),
                customKeywords = settings.customKeywords.first(),
                captureProfile = settings.captureProfile.first(),
                continuousListeningEnabled = continuousListeningEnabled,
                force = true
            )
        }
    }

    private fun restoreAfterFailedWatchTransfer(context: Context, wasListening: Boolean) {
        synchronized(transitionLock) {
            _isWatchActive.value = false
            if (wasListening && !RecordingState.isRecording.value) {
                setSuspendReason(context, SuspendReason.NONE)
                val intent = Intent(context, KeywordListenerService::class.java)
                requestListenerStart(context, intent, reason = "watch_transfer_failed")
                modeRef.set(ServiceMode.LISTENING)
            } else if (!RecordingState.isRecording.value) {
                modeRef.set(ServiceMode.IDLE)
            }
        }
    }

    /** Returns true if the user explicitly wants the service running */
    fun shouldBeRunning(context: Context): Boolean {
        val enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOULD_RUN, false)
        _continuousListeningEnabled.value = enabled
        return enabled
    }

    /** Current cross-surface state, hydrating the persisted listening preference if needed. */
    fun captureSnapshot(context: Context): CaptureUiState = resolveCaptureUiState(
        listenerState = _listenerState.value,
        listeningEnabled = shouldBeRunning(context),
        triggerRecognized = _isTriggerRecognized.value,
        recording = RecordingState.isRecording.value,
        processing = RecordingState.isProcessing.value,
        watchActive = _isWatchActive.value,
        transferring = _isTransferring.value,
        elapsedSeconds = RecordingState.elapsedSeconds.value,
        hasError = RecordingState.lastError.value != null
    )

    fun suspendReason(context: Context): SuspendReason {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SUSPEND_REASON, SuspendReason.NONE.name)
        return runCatching { SuspendReason.valueOf(raw ?: SuspendReason.NONE.name) }
            .getOrDefault(SuspendReason.NONE)
    }

    private fun setSuspendReason(context: Context, reason: SuspendReason) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SUSPEND_REASON, reason.name)
            .commit()
    }

    fun notifyStarting() = updateListenerState(ListenerState.STARTING)

    fun notifyListening() = updateListenerState(ListenerState.LISTENING)

    fun notifyTriggerRecognized(recognized: Boolean) {
        _isTriggerRecognized.value = recognized && _listenerState.value == ListenerState.LISTENING
    }

    fun notifyPaused() = updateListenerState(ListenerState.PAUSED)

    fun notifyFailed() = updateListenerState(ListenerState.FAILED)

    fun notifyStopped() {
        updateListenerState(ListenerState.STOPPED)
        if (!RecordingState.isRecording.value && !_isWatchActive.value) {
            modeRef.set(ServiceMode.IDLE)
        }
    }

    private fun updateListenerState(state: ListenerState) {
        _listenerState.value = state
        _isRunning.value = state != ListenerState.STOPPED
        if (state != ListenerState.LISTENING) {
            _isTriggerRecognized.value = false
        }
    }

    private fun requestListenerStart(context: Context, intent: Intent, reason: String): Boolean {
        return try {
            ContextCompat.startForegroundService(context, intent)
            updateListenerState(ListenerState.STARTING)
            true
        } catch (error: RuntimeException) {
            updateListenerState(ListenerState.STOPPED)
            val notified = ListenerRecoveryNotifier.show(context, reason)
            CaptureLog.event(
                gate = CaptureLog.Gate.SERVICE,
                result = CaptureLog.Result.REJECT,
                text = "service_start_deferred_to_user",
                meta = mapOf(
                    "reason" to reason,
                    "error" to error.javaClass.simpleName,
                    "notificationShown" to notified
                )
            )
            false
        }
    }

    fun notifyWatchActive() {
        _isWatchActive.value = true
        modeRef.set(ServiceMode.WATCH)
    }

    fun notifyWatchInactive() {
        _isWatchActive.value = false
        if (_isRunning.value) {
            modeRef.set(ServiceMode.LISTENING)
        } else if (RecordingState.isRecording.value) {
            modeRef.set(ServiceMode.RECORDING)
        } else {
            modeRef.set(ServiceMode.IDLE)
        }
    }

    fun notifyLocationRunning(running: Boolean) {
        _isLocationRunning.value = running
    }

    internal fun resolveCaptureUiState(
        listenerState: ListenerState,
        listeningEnabled: Boolean,
        triggerRecognized: Boolean,
        recording: Boolean,
        processing: Boolean,
        watchActive: Boolean,
        transferring: Boolean,
        elapsedSeconds: Long,
        hasError: Boolean
    ): CaptureUiState {
        val mode = when {
            transferring -> CaptureMode.TRANSFERRING
            recording -> CaptureMode.RECORDING
            processing -> CaptureMode.PROCESSING
            watchActive -> CaptureMode.WATCH
            hasError || listenerState == ListenerState.FAILED -> CaptureMode.ERROR
            triggerRecognized && listenerState == ListenerState.LISTENING ->
                CaptureMode.TRIGGER_RECOGNIZED
            listenerState == ListenerState.STARTING -> CaptureMode.STARTING
            listenerState == ListenerState.LISTENING -> CaptureMode.LISTENING
            listenerState == ListenerState.PAUSED -> CaptureMode.PAUSED
            else -> CaptureMode.IDLE
        }
        return CaptureUiState(
            mode = mode,
            listeningActive = listeningEnabled || listenerState != ListenerState.STOPPED,
            recording = recording,
            processing = processing,
            watchActive = watchActive,
            transferring = transferring,
            triggerRecognized = mode == CaptureMode.TRIGGER_RECOGNIZED,
            elapsedSeconds = elapsedSeconds
        )
    }
}
