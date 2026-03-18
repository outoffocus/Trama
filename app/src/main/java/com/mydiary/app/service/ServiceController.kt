package com.mydiary.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ServiceController {

    private const val PREFS = "service_prefs"
    private const val KEY_SHOULD_RUN = "should_run"

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    fun start(context: Context) {
        val intent = Intent(context, KeywordListenerService::class.java)
        ContextCompat.startForegroundService(context, intent)
        _isRunning.value = true
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHOULD_RUN, true).apply()
    }

    fun stop(context: Context) {
        val intent = Intent(context, KeywordListenerService::class.java)
        context.stopService(intent)
        _isRunning.value = false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHOULD_RUN, false).apply()
    }

    /**
     * Stop service because watch took over mic. Does NOT clear should_run,
     * so the service can resume when watch releases.
     */
    fun stopByWatch(context: Context) {
        val intent = Intent(context, KeywordListenerService::class.java)
        context.stopService(intent)
        _isRunning.value = false
    }

    /** Returns true if the user explicitly wants the service running */
    fun shouldBeRunning(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOULD_RUN, false)
    }

    // Called from service onDestroy to keep state in sync
    fun notifyStopped() {
        _isRunning.value = false
    }
}
