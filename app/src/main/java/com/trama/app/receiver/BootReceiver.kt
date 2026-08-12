package com.trama.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.trama.app.service.ServiceController
import com.trama.app.service.ContinuousListeningPolicy
import com.trama.app.service.ListenerRecoveryNotifier
import com.trama.app.summary.RecordingRecoveryWorker
import com.trama.app.ui.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        RecordingRecoveryWorker.enqueue(context)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val autoStart = SettingsDataStore(context).autoStart.first()
                val shouldRestore = ServiceController.shouldBeRunning(context)
                if (ContinuousListeningPolicy.shouldRequestBootReactivation(
                        continuousListeningEnabled = shouldRestore,
                        reminderEnabled = autoStart
                    )
                ) {
                    // A microphone foreground service cannot be started directly
                    // from BOOT_COMPLETED on current Android versions. Ask for an
                    // explicit user interaction instead.
                    val notified = ListenerRecoveryNotifier.show(context, reason = "boot_completed")
                    Log.i(
                        "BootReceiver",
                        if (notified) "Boot completed, listener reactivation requested" else
                            "Boot completed, notification permission missing"
                    )
                } else {
                    Log.i("BootReceiver", "Boot completed, listening or reminder disabled")
                }

                val locationEnabled = SettingsDataStore(context).locationEnabled.first()
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
