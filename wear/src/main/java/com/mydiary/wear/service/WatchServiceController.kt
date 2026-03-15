package com.mydiary.wear.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object WatchServiceController {

    fun start(context: Context) {
        val intent = Intent(context, WatchKeywordListenerService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, WatchKeywordListenerService::class.java))
    }

    fun isRunning(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == WatchKeywordListenerService::class.java.name }
    }
}
