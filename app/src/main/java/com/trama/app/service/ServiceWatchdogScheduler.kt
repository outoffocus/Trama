package com.trama.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.trama.app.diagnostics.CaptureLog

object ServiceWatchdogScheduler {
    private const val TAG = "ServiceWatchdog"
    const val ACTION_CHECK_KEYWORD_LISTENER = "com.trama.app.action.CHECK_KEYWORD_LISTENER"

    private const val REQUEST_CODE = 4107
    private const val DEFAULT_DELAY_MS = 5L * 60L * 1000L

    fun schedule(context: Context, delayMs: Long = DEFAULT_DELAY_MS, reason: String) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = SystemClock.elapsedRealtime() + delayMs.coerceAtLeast(15_000L)

        runCatching {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pendingIntent(appContext)
            )
            CaptureLog.event(
                gate = CaptureLog.Gate.SERVICE,
                result = CaptureLog.Result.OK,
                text = "watchdog_scheduled",
                meta = mapOf(
                    "reason" to reason,
                    "delayMs" to delayMs
                )
            )
        }.onFailure { error ->
            Log.w(TAG, "Failed to schedule watchdog", error)
        }
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent(appContext))
        CaptureLog.event(
            gate = CaptureLog.Gate.SERVICE,
            result = CaptureLog.Result.OK,
            text = "watchdog_cancelled"
        )
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ServiceRescueReceiver::class.java)
            .setAction(ACTION_CHECK_KEYWORD_LISTENER)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
