package com.trama.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.trama.app.service.ServiceController
import com.trama.app.ui.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val autoStart = SettingsDataStore(context).autoStart.first()
                val shouldRestore = ServiceController.shouldBeRunning(context)
                if (autoStart || shouldRestore) {
                    Log.i("BootReceiver", "Boot completed, restoring keyword listener")
                    ServiceController.start(context)
                } else {
                    Log.i("BootReceiver", "Boot completed, auto-start disabled")
                }

                val locationEnabled = SettingsDataStore(context).locationEnabled.first()
                val hasLocationPermission = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (locationEnabled && hasLocationPermission) {
                    Log.i("BootReceiver", "Boot completed, auto-starting location tracking")
                    ServiceController.startLocationTracking(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
