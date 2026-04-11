package com.trama.wear.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.sync.MicCoordinator
import com.trama.wear.sync.WatchToPhoneSyncer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object WatchServiceController {

    private const val PREFS = "watch_sync_prefs"
    private const val KEY_USER_ENABLED = "user_enabled"
    private const val KEY_PHONE_ACTIVE = "phone_active"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isPhoneActive = MutableStateFlow(false)
    val isPhoneActive: StateFlow<Boolean> = _isPhoneActive.asStateFlow()

    /**
     * Start keyword listening. Stops recording if active (modes are exclusive).
     * Sends PAUSE to phone to take over the mic.
     */
    fun start(context: Context) {
        // Stop recording if active — modes are exclusive
        if (RecordingController.isRecording.value) {
            RecordingController.stopRecording(context)
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_USER_ENABLED, true)
            .putBoolean(KEY_PHONE_ACTIVE, false)
            .apply()

        val intent = Intent(context, WatchKeywordListenerService::class.java)
        ContextCompat.startForegroundService(context, intent)
        _isRunning.value = true
        _isPhoneActive.value = false
    }

    /**
     * Start recording. Stops keyword listener if active (modes are exclusive).
     */
    fun startRecording(context: Context) {
        // Stop keyword listener — modes are exclusive
        if (_isRunning.value) {
            context.stopService(Intent(context, WatchKeywordListenerService::class.java))
            _isRunning.value = false
        }
        _isPhoneActive.value = false
        RecordingController.startRecording(context)
    }

    /**
     * Auto-resume after phone releases mic. Only starts if phone is not active.
     */
    fun resumeIfAllowed(context: Context) {
        if (isPhoneActive(context)) return
        if (!isUserEnabled(context)) return

        val intent = Intent(context, WatchKeywordListenerService::class.java)
        ContextCompat.startForegroundService(context, intent)
        _isRunning.value = true
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, WatchKeywordListenerService::class.java))
        _isRunning.value = false
    }

    /**
     * Stop keyword listener (user-initiated). Does NOT notify the phone —
     * use transferToPhone() to hand control to the phone explicitly.
     */
    fun stopByUser(context: Context) {
        stop(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_USER_ENABLED, false).apply()
    }

    /**
     * Stop everything because phone took over. Does NOT clear user_enabled
     * so watch can auto-resume when phone returns control.
     */
    fun stopByPhone(context: Context) {
        context.stopService(Intent(context, WatchKeywordListenerService::class.java))
        _isRunning.value = false
        if (RecordingController.isRecording.value) {
            RecordingController.stopRecording(context)
        }
    }

    /**
     * Transfer active mode to phone. Stops everything locally.
     */
    fun transferToPhone(context: Context) {
        val wasRecording = RecordingController.isRecording.value

        // Stop everything locally
        if (_isRunning.value) {
            context.stopService(Intent(context, WatchKeywordListenerService::class.java))
            _isRunning.value = false
        }
        if (wasRecording) {
            RecordingController.stopRecording(context)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_USER_ENABLED, false).apply()

        _isPhoneActive.value = true

        scope.launch {
            try {
                WatchToPhoneSyncer(
                    context = context.applicationContext,
                    repository = DatabaseProvider.getRepository(context.applicationContext)
                ).syncUnsentEntries()
            } catch (_: Exception) {
            }
            if (wasRecording) {
                MicCoordinator.sendStartRecording(context)
            } else {
                MicCoordinator.sendStartKeyword(context)
            }
        }
    }

    fun reclaimFromPhone(context: Context) {
        notifyPhoneInactive(context)
        start(context)
    }

    /** Called from service onDestroy to keep state in sync */
    fun notifyStopped() {
        _isRunning.value = false
    }

    fun isUserEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_USER_ENABLED, false)
    }

    fun isPhoneActive(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PHONE_ACTIVE, false)
    }

    /** Send RESUME to phone — used when watch stops involuntarily (battery, crash) */
    fun sendResumeToPhone(context: Context) {
        scope.launch { MicCoordinator.sendResume(context) }
    }

    fun notifyPhoneActive(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PHONE_ACTIVE, true).apply()
        _isPhoneActive.value = true
    }

    fun notifyPhoneInactive(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PHONE_ACTIVE, false).apply()
        _isPhoneActive.value = false
    }
}
