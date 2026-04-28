package com.trama.app.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.trama.app.diagnostics.CaptureLog

class ServiceRescueReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ServiceWatchdogScheduler.ACTION_CHECK_KEYWORD_LISTENER &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        CaptureLog.init(context)

        if (!ServiceController.shouldBeRunning(context)) {
            ServiceWatchdogScheduler.cancel(context)
            log("watchdog_skip_disabled")
            return
        }

        val suspendReason = ServiceController.suspendReason(context)
        if (suspendReason != ServiceController.SuspendReason.NONE) {
            log("watchdog_skip_suspended", mapOf("suspendReason" to suspendReason.name))
            ServiceWatchdogScheduler.schedule(context, reason = "suspended_${suspendReason.name.lowercase()}")
            return
        }

        if (ServiceController.isRunning.value) {
            log("watchdog_service_alive")
            ServiceWatchdogScheduler.schedule(context, reason = "service_alive")
            return
        }

        if (!hasAudioPermission(context)) {
            log("watchdog_skip_no_permission", result = CaptureLog.Result.REJECT)
            ServiceWatchdogScheduler.schedule(context, reason = "no_permission")
            return
        }

        if (isBatteryLow(context)) {
            log("watchdog_skip_low_battery", result = CaptureLog.Result.REJECT)
            ServiceWatchdogScheduler.schedule(context, reason = "low_battery")
            return
        }

        val started = ServiceController.startFromWatchdog(context, reason = intent.action.orEmpty())
        if (started) {
            log("watchdog_started_service")
        } else {
            log("watchdog_start_failed", result = CaptureLog.Result.REJECT)
            ServiceWatchdogScheduler.schedule(context, reason = "start_failed")
        }
    }

    private fun hasAudioPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun isBatteryLow(context: Context): Boolean {
        val bm = context.getSystemService(BatteryManager::class.java) ?: return false
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return level in 1 until 15
    }

    private fun log(
        state: String,
        meta: Map<String, Any?> = emptyMap(),
        result: CaptureLog.Result = CaptureLog.Result.OK
    ) {
        Log.i("ServiceRescueReceiver", state)
        CaptureLog.event(
            gate = CaptureLog.Gate.SERVICE,
            result = result,
            text = state,
            meta = meta
        )
    }
}
