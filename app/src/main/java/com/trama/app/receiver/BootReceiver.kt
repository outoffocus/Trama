package com.trama.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.trama.app.backup.BackupScheduler
import com.trama.app.service.ServiceController
import com.trama.app.service.ContinuousListeningPolicy
import com.trama.app.service.ListenerRecoveryNotifier
import com.trama.app.summary.RecordingRecoveryWorker
import com.trama.app.summary.SummaryScheduler
import com.trama.app.summary.WeeklyAgendaScheduler
import com.trama.app.ui.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (!ScheduleRecoveryPolicy.isSupported(action)) return
        val isBoot = action == Intent.ACTION_BOOT_COMPLETED
        if (isBoot) RecordingRecoveryWorker.enqueue(context)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsDataStore(context)
                SummaryScheduler.schedule(context)
                BackupScheduler.reconcile(
                    context = context,
                    enabled = settings.backupEnabled.first(),
                    hour = settings.backupHour.first(),
                    minute = settings.backupMinute.first(),
                    realignExisting = true
                )
                if (settings.weeklyAgendaEnabled.first()) {
                    WeeklyAgendaScheduler.schedule(
                        context,
                        settings.weeklyAgendaDayOfWeek.first(),
                        settings.weeklyAgendaHour.first()
                    )
                }
                Log.i("ScheduleRecovery", "Schedules realigned after $action")

                if (!isBoot) return@launch

                val autoStart = settings.autoStart.first()
                val shouldRestore = ServiceController.shouldBeRunning(context)
                if (ContinuousListeningPolicy.shouldRequestBootReactivation(
                        continuousListeningEnabled = shouldRestore,
                        reminderEnabled = autoStart
                    )
                ) {
                    // Android requires a visible user action before starting the microphone.
                    val notified = ListenerRecoveryNotifier.show(context, reason = "boot_completed")
                    Log.i(
                        "BootReceiver",
                        if (notified) "Boot completed, listener reactivation requested" else
                            "Boot completed, notification permission missing"
                    )
                } else {
                    Log.i("BootReceiver", "Boot completed, listening or reminder disabled")
                }

                val locationEnabled = settings.locationEnabled.first()
                val hasFineLocationPermission = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val hasBackgroundLocationPermission = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (locationEnabled && hasFineLocationPermission && hasBackgroundLocationPermission) {
                    Log.i("BootReceiver", "Boot completed, auto-starting location tracking")
                    ServiceController.startLocationTracking(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

internal object ScheduleRecoveryPolicy {
    fun isSupported(action: String): Boolean = action in setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED
    )
}
