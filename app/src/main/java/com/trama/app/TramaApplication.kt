package com.trama.app

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import com.trama.app.diagnostics.CaptureLog
import com.trama.app.backup.BackupScheduler
import com.trama.app.service.EntryProcessingState
import com.trama.app.service.ServiceController
import com.trama.app.summary.GemmaClient
import com.trama.app.summary.GoogleCalendarSyncManager
import com.trama.app.summary.SummaryScheduler
import com.trama.app.ui.SettingsDataStore
import com.trama.app.widget.TramaWidgetProvider
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.util.DayRange
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

@HiltAndroidApp
class TramaApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // IsolatedGemmaVisionService runs in the :llm process. Android creates a fresh
        // TramaApplication there too, but WorkManager and calendar sync belong only to
        // the main process — calling them in :llm causes a fatal WorkManager crash.
        if (!isMainProcess()) return
        removeLegacyCloudCredential()
        CaptureLog.init(applicationContext)
        appScope.launch {
            SettingsDataStore(applicationContext).asrDebugEnabled.collectLatest {
                CaptureLog.setContentLoggingEnabled(it)
            }
        }
        schedulePrivateDailyMemory()
        restoreDailyBackupSchedule()
        syncSelectedCalendars()
        observeWidgetData()
        registerMemoryCallback()
    }

    private fun isMainProcess(): Boolean {
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            try {
                val cmdline = java.io.File("/proc/self/cmdline").readText()
                cmdline.trimEnd('\u0000')
            } catch (_: Exception) {
                packageName
            }
        }
        return name == packageName
    }

    /** Remove credentials left by versions that still offered remote model processing. */
    private fun removeLegacyCloudCredential() {
        getSharedPreferences("daily_summary", MODE_PRIVATE)
            .edit()
            .remove("gemini_api_key")
            .apply()
    }

    private fun schedulePrivateDailyMemory() {
        SummaryScheduler.schedule(applicationContext)
    }

    /**
     * WorkManager persists work in normal conditions, but its database can be rebuilt
     * after an app update or device restore. Reconcile the user's saved preference on
     * every normal app start so an enabled daily backup cannot silently disappear.
     */
    private fun restoreDailyBackupSchedule() {
        appScope.launch {
            val settings = SettingsDataStore(applicationContext)
            BackupScheduler.reconcile(
                context = applicationContext,
                enabled = settings.backupEnabled.first(),
                hour = settings.backupHour.first(),
                minute = settings.backupMinute.first()
            )
        }
    }

    private fun syncSelectedCalendars() {
        appScope.launch {
            GoogleCalendarSyncManager(applicationContext).syncSelectedCalendars()
        }
    }

    private fun observeWidgetData() {
        appScope.launch {
            val repository = DatabaseProvider.getRepository(applicationContext)
            val today = DayRange.of(System.currentTimeMillis())
            combine(
                repository.getPending(),
                repository.getCompletedByCompletedAt(today.startMs, today.endInclusiveMs)
            ) { pending, completed ->
                pending.hashCode() to completed.hashCode()
            }
                .distinctUntilChanged()
                .collectLatest { TramaWidgetProvider.requestRefresh(applicationContext) }
        }
        appScope.launch {
            EntryProcessingState.processingIds
                .collectLatest { TramaWidgetProvider.requestRefresh(applicationContext) }
        }
        appScope.launch {
            ServiceController.captureState
                .collectLatest { TramaWidgetProvider.requestRefresh(applicationContext) }
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

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() {
                GemmaClient.release()
            }
        })
    }
}
