package com.trama.app

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.util.Log
import com.trama.app.diagnostics.CaptureLog
import com.trama.app.summary.GemmaClient
import com.trama.app.summary.GoogleCalendarSyncManager
import com.trama.app.summary.SummaryScheduler
import com.trama.app.ui.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TramaApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        CaptureLog.init(applicationContext)
        scheduleDailySummaryIfEnabled()
        syncSelectedCalendars()
        registerMemoryCallback()
    }

    private fun scheduleDailySummaryIfEnabled() {
        appScope.launch {
            val settings = SettingsDataStore(applicationContext)
            val enabled = settings.summaryEnabled.first()
            if (enabled) {
                val hour = settings.summaryHour.first()
                SummaryScheduler.schedule(applicationContext, hour)
            }
        }
    }

    private fun syncSelectedCalendars() {
        appScope.launch {
            GoogleCalendarSyncManager(applicationContext).syncSelectedCalendars()
        }
    }

    private fun registerMemoryCallback() {
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                    Log.i("TramaApp", "Memory low (level=$level), releasing Gemma model")
                    GemmaClient.release()
                }
            }
            override fun onConfigurationChanged(newConfig: Configuration) {}
            @Deprecated("Deprecated in Java")
            override fun onLowMemory() {
                GemmaClient.release()
            }
        })
    }
}
