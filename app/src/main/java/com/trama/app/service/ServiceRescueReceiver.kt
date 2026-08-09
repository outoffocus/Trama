package com.trama.app.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
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
            log(
                "watchdog_skip_disabled",
                meta = serviceState(context) + mapOf("outcome" to CaptureLog.CaptureOutcome.SERVICE_SUSPENDED)
            )
            return
        }

        val suspendReason = ServiceController.suspendReason(context)
        if (suspendReason != ServiceController.SuspendReason.NONE) {
            log(
                "watchdog_skip_suspended",
                meta = serviceState(context) + mapOf(
                    "suspendReason" to suspendReason.name,
                    "outcome" to CaptureLog.CaptureOutcome.SERVICE_SUSPENDED
                )
            )
            ServiceWatchdogScheduler.schedule(context, reason = "suspended_${suspendReason.name.lowercase()}")
            return
        }

        if (ServiceController.isRunning.value) {
            log(
                "watchdog_service_alive",
                meta = serviceState(context) + mapOf("outcome" to CaptureLog.CaptureOutcome.SERVICE_AVAILABLE)
            )
            ServiceWatchdogScheduler.schedule(context, reason = "service_alive")
            return
        }

        if (!hasAudioPermission(context)) {
            log(
                "watchdog_skip_no_permission",
                result = CaptureLog.Result.REJECT,
                meta = serviceState(context) + mapOf(
                    "serviceStartError" to "missing_record_audio_permission",
                    "outcome" to CaptureLog.CaptureOutcome.SERVICE_UNAVAILABLE
                )
            )
            ServiceWatchdogScheduler.schedule(context, reason = "no_permission")
            return
        }

        if (isBatteryLow(context)) {
            log(
                "watchdog_skip_low_battery",
                result = CaptureLog.Result.REJECT,
                meta = serviceState(context) + mapOf(
                    "serviceStartError" to "battery_low",
                    "outcome" to CaptureLog.CaptureOutcome.SERVICE_UNAVAILABLE
                )
            )
            ServiceWatchdogScheduler.schedule(context, reason = "low_battery")
            return
        }

        // Android 14+ rejects microphone foreground-service starts from an
        // alarm or package receiver. Request visible user interaction instead.
        val notified = ListenerRecoveryNotifier.show(
            context,
            reason = intent.action.orEmpty()
        )
        if (notified) {
            log(
                "watchdog_requested_user_reactivation",
                meta = serviceState(context) + mapOf("outcome" to CaptureLog.CaptureOutcome.SERVICE_SUSPENDED)
            )
        } else {
            log(
                "watchdog_cannot_notify",
                result = CaptureLog.Result.REJECT,
                meta = serviceState(context) + mapOf(
                    "serviceStartError" to "missing_notification_permission",
                    "outcome" to CaptureLog.CaptureOutcome.SERVICE_UNAVAILABLE
                )
            )
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

    private fun serviceState(context: Context): Map<String, Any?> {
        val bm = context.getSystemService(BatteryManager::class.java)
        return watchdogServiceState(context) + mapOf(
            "batteryPct" to bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            "sdk" to Build.VERSION.SDK_INT
        )
    }

    companion object {
        fun watchdogServiceState(context: Context): Map<String, Any?> = mapOf(
            "shouldBeRunning" to ServiceController.shouldBeRunning(context),
            "isRunning" to ServiceController.isRunning.value,
            "suspendReason" to ServiceController.suspendReason(context).name
        )
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
