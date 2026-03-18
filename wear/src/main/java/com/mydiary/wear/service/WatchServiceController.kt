package com.mydiary.wear.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object WatchServiceController {

    private const val PREFS = "watch_sync_prefs"
    private const val KEY_USER_ENABLED = "user_enabled"
    private const val KEY_PHONE_ACTIVE = "phone_active"

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /**
     * Start service (user-initiated). Always starts, even if phone is active.
     * The service will send PAUSE to the phone to take over the mic.
     */
    fun start(context: Context) {
        // Clear phone_active flag — user is explicitly taking over
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_USER_ENABLED, true)
            .putBoolean(KEY_PHONE_ACTIVE, false)
            .apply()

        val intent = Intent(context, WatchKeywordListenerService::class.java)
        ContextCompat.startForegroundService(context, intent)
        _isRunning.value = true
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
     * Stop and record that user explicitly disabled the service.
     */
    fun stopByUser(context: Context) {
        stop(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_USER_ENABLED, false).apply()
    }

    /** Called from service onDestroy to keep state in sync */
    fun notifyStopped() {
        _isRunning.value = false
    }

    /** Returns true if the user explicitly wants the service running */
    fun isUserEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_USER_ENABLED, false)
    }

    /** Returns true if the phone's listener service is currently active */
    fun isPhoneActive(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PHONE_ACTIVE, false)
    }
}
