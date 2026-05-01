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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

object ServiceController {

    private const val PREFS = "service_prefs"
    private const val KEY_SHOULD_RUN = "should_run"
    private const val KEY_SUSPEND_REASON = "suspend_reason"

    enum class SuspendReason {
        NONE,
        RECORDING,
        WATCH
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

    private val _isWatchActive = MutableStateFlow(false)
    val isWatchActive: StateFlow<Boolean> = _isWatchActive.asStateFlow()

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
            if (shouldSuppressStart("user_start")) return
            ServiceWatchdogScheduler.schedule(context, reason = "user_start")
            CaptureLog.event(
                gate = CaptureLog.Gate.SERVICE,
                result = CaptureLog.Result.OK,
                text = "service_start_requested"
            )
            val intent = Intent(context, KeywordListenerService::class.java)
            ContextCompat.startForegroundService(context, intent)
            _isRunning.value = true
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
            ServiceWatchdogScheduler.cancel(context)
            CaptureLog.event(
                gate = CaptureLog.Gate.SERVICE,
                result = CaptureLog.Result.OK,
                text = "service_stop_requested",
                meta = mapOf("reason" to reason)
            )
            val intent = Intent(context, KeywordListenerService::class.java)
            context.stopService(intent)
            _isRunning.value = false
            if (!RecordingState.isRecording.value && !_isWatchActive.value) {
                modeRef.set(ServiceMode.IDLE)
            }
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
            ContextCompat.startForegroundService(context, intent)
            _isRunning.value = true
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
            if (_isRunning.value) {
                setSuspendReason(context, SuspendReason.RECORDING)
                context.stopService(Intent(context, KeywordListenerService::class.java))
                _isRunning.value = false
            }
            _isWatchActive.value = false
            modeRef.set(ServiceMode.RECORDING)
            RecordingState.startRecording(context)
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
            _isRunning.value = false
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
            val wasListening = _isRunning.value
            val wasRecording = RecordingState.isRecording.value

            if (_isRunning.value) {
                setSuspendReason(context, SuspendReason.WATCH)
                context.stopService(Intent(context, KeywordListenerService::class.java))
                _isRunning.value = false
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
                    onResult?.invoke(false)
                    return@launch
                }

                delay(6_000)
                if (_isWatchActive.value) {
                    onResult?.invoke(true)
                } else {
                    restoreAfterFailedWatchTransfer(context, wasListening)
                    onResult?.invoke(false)
                }
            }
        }
    }

    private suspend fun syncSettingsToWatch(context: Context) {
        val appContext = context.applicationContext
        val settings = SettingsDataStore(appContext)
        runCatching {
            SettingsSyncer(appContext).syncPatterns(
                patterns = settings.intentPatterns.first(),
                customKeywords = settings.customKeywords.first(),
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
                ContextCompat.startForegroundService(context, intent)
                _isRunning.value = true
                modeRef.set(ServiceMode.LISTENING)
            } else if (!RecordingState.isRecording.value) {
                modeRef.set(ServiceMode.IDLE)
            }
        }
    }

    /** Returns true if the user explicitly wants the service running */
    fun shouldBeRunning(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOULD_RUN, false)
    }

    fun suspendReason(context: Context): SuspendReason {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SUSPEND_REASON, SuspendReason.NONE.name)
        return runCatching { SuspendReason.valueOf(raw ?: SuspendReason.NONE.name) }
            .getOrDefault(SuspendReason.NONE)
    }

    fun startFromWatchdog(context: Context, reason: String): Boolean {
        synchronized(transitionLock) {
            if (!shouldBeRunning(context)) return false
            if (suspendReason(context) != SuspendReason.NONE) return false
            if (shouldSuppressStart("watchdog:$reason")) return true
            return runCatching {
                val intent = Intent(context, KeywordListenerService::class.java)
                    .putExtra("watchdogReason", reason)
                ContextCompat.startForegroundService(context, intent)
                _isRunning.value = true
                _isWatchActive.value = false
                modeRef.set(ServiceMode.LISTENING)
            }.isSuccess
        }
    }

    private fun setSuspendReason(context: Context, reason: SuspendReason) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SUSPEND_REASON, reason.name)
            .commit()
    }

    fun notifyStopped() {
        _isRunning.value = false
        if (!RecordingState.isRecording.value && !_isWatchActive.value) {
            modeRef.set(ServiceMode.IDLE)
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
}
