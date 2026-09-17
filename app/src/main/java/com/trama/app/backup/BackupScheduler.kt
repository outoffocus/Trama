package com.trama.app.backup

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Schedules daily auto-backup to Downloads folder.
 */
object BackupScheduler {

    private const val TAG = "BackupScheduler"
    private const val WORK_NAME = "daily_backup"

    fun reconcile(
        context: Context,
        enabled: Boolean,
        hour: Int,
        minute: Int = 0,
        realignExisting: Boolean = false
    ) {
        if (enabled) {
            schedule(context, hour, minute, updateExisting = realignExisting)
        } else {
            cancel(context)
        }
    }

    fun schedule(context: Context, hour: Int, minute: Int = 0) {
        schedule(context, hour, minute, updateExisting = true)
    }

    private fun schedule(
        context: Context,
        hour: Int,
        minute: Int,
        updateExisting: Boolean
    ) {
        val normalizedHour = hour.coerceIn(0, 23)
        val normalizedMinute = minute.coerceIn(0, 59)
        val delay = calculateDelay(normalizedHour, normalizedMinute)

        val workRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(
            1, TimeUnit.DAYS
        )
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            if (updateExisting) ExistingPeriodicWorkPolicy.UPDATE
            else ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        val hours = delay / (1000 * 60 * 60)
        val mins = (delay / (1000 * 60)) % 60
        Log.i(
            TAG,
            "Daily backup scheduled at %02d:%02d (in %dh %dm)".format(
                normalizedHour,
                normalizedMinute,
                hours,
                mins
            )
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        Log.i(TAG, "Daily backup cancelled")
    }

    internal fun calculateDelay(
        targetHour: Int,
        targetMinute: Int = 0,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val normalizedHour = targetHour.coerceIn(0, 23)
        val normalizedMinute = targetMinute.coerceIn(0, 59)
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val target = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, normalizedHour)
            set(Calendar.MINUTE, normalizedMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!target.after(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
