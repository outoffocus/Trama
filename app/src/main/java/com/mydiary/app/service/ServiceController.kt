package com.mydiary.app.service

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

object ServiceController {

    fun start(context: Context) {
        val intent = Intent(context, KeywordListenerService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, KeywordListenerService::class.java)
        context.stopService(intent)
    }

    fun isRunning(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == KeywordListenerService::class.java.name }
    }
}
