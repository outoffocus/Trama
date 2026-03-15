package com.mydiary.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object ServiceController {

    @Volatile
    private var running = false

    fun start(context: Context) {
        val intent = Intent(context, KeywordListenerService::class.java)
        ContextCompat.startForegroundService(context, intent)
        running = true
    }

    fun stop(context: Context) {
        val intent = Intent(context, KeywordListenerService::class.java)
        context.stopService(intent)
        running = false
    }

    fun isRunning(context: Context): Boolean = running

    // Called from service onDestroy to keep state in sync
    fun notifyStopped() {
        running = false
    }
}
