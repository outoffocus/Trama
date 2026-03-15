package com.mydiary.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mydiary.app.service.ServiceController
import com.mydiary.app.ui.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val autoStart = runBlocking {
                SettingsDataStore(context).autoStart.first()
            }
            if (autoStart) {
                Log.i("BootReceiver", "Boot completed, auto-starting keyword listener")
                ServiceController.start(context)
            } else {
                Log.i("BootReceiver", "Boot completed, auto-start disabled")
            }
        }
    }
}
