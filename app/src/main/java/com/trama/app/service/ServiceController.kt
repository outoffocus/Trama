package com.trama.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
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

    private enum class ServiceMode {
        IDLE,
        LISTENING,
        RECORDING,
        WATCH
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val transitionLock = Any()
    private val modeRef = AtomicReference(ServiceMode.IDLE)

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
                .edit().putBoolean(KEY_SHOULD_RUN, true).commit()
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
    fun stop(context: Context) {
        synchronized(transitionLock) {
            val intent = Intent(context, KeywordListenerService::class.java)
            context.stopService(intent)
            _isRunning.value = false
            if (!RecordingState.isRecording.value && !_isWatchActive.value) {
                modeRef.set(ServiceMode.IDLE)
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_SHOULD_RUN, false).commit()
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
