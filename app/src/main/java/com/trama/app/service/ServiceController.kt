package com.trama.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.trama.shared.sync.MicCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object ServiceController {

    private const val PREFS = "service_prefs"
    private const val KEY_SHOULD_RUN = "should_run"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isWatchActive = MutableStateFlow(false)
    val isWatchActive: StateFlow<Boolean> = _isWatchActive.asStateFlow()

    /**
     * Start keyword listening. Stops recording if active (modes are exclusive).
     */
    fun start(context: Context) {
        // Stop recording if active — modes are exclusive
        if (RecordingState.isRecording.value) {
            RecordingState.stopRecording(context)
        }
        val intent = Intent(context, KeywordListenerService::class.java)
        ContextCompat.startForegroundService(context, intent)
        _isRunning.value = true
        _isWatchActive.value = false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHOULD_RUN, true).apply()
    }

    /**
     * Stop keyword service (user-initiated). Does NOT notify the watch —
     * use transferToWatch() to hand control to the watch explicitly.
     */
    fun stop(context: Context) {
        val intent = Intent(context, KeywordListenerService::class.java)
        context.stopService(intent)
        _isRunning.value = false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHOULD_RUN, false).apply()
    }

    /**
     * Start recording. Stops keyword listener if active (modes are exclusive).
     */
    fun startRecording(context: Context) {
        // Stop keyword listener — modes are exclusive
        if (_isRunning.value) {
            context.stopService(Intent(context, KeywordListenerService::class.java))
            _isRunning.value = false
        }
        _isWatchActive.value = false
        RecordingState.startRecording(context)
    }

    /**
     * Stop service because watch took over mic. Does NOT clear should_run,
     * so the service can resume when watch releases.
     */
    fun stopByWatch(context: Context) {
        context.stopService(Intent(context, KeywordListenerService::class.java))
        _isRunning.value = false
        if (RecordingState.isRecording.value) {
            RecordingState.stopRecording(context)
        }
    }

    /**
     * Transfer active mode to watch. Stops everything locally.
     * Does NOT clear should_run so phone can auto-resume if watch returns control.
     */
    fun transferToWatch(context: Context) {
        val wasRecording = RecordingState.isRecording.value

        // Stop everything locally
        if (_isRunning.value) {
            context.stopService(Intent(context, KeywordListenerService::class.java))
            _isRunning.value = false
        }
        if (wasRecording) {
            RecordingState.stopRecording(context)
        }

        _isWatchActive.value = true

        // Send the appropriate command to the watch
        scope.launch {
            if (wasRecording) {
                MicCoordinator.sendStartRecording(context)
            } else {
                MicCoordinator.sendStartKeyword(context)
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
    }

    fun notifyWatchActive() {
        _isWatchActive.value = true
    }

    fun notifyWatchInactive() {
        _isWatchActive.value = false
    }
}
