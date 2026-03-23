package com.mydiary.app

import android.app.Application
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
}
