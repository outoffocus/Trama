package com.mydiary.app

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.util.Log
import com.mydiary.app.summary.GemmaClient
import com.mydiary.app.summary.SummaryScheduler
import com.mydiary.app.ui.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MyDiaryApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        scheduleDailySummaryIfEnabled()
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

    private fun registerMemoryCallback() {
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                    Log.i("MyDiaryApp", "Memory low (level=$level), releasing Gemma model")
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
