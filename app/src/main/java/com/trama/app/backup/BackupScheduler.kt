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

    fun schedule(context: Context, hour: Int) {
        val delay = calculateDelay(hour)

        val workRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(
            1, TimeUnit.DAYS
        )
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )

        val hours = delay / (1000 * 60 * 60)
        val mins = (delay / (1000 * 60)) % 60
        Log.i(TAG, "Daily backup scheduled at $hour:00 (in ${hours}h ${mins}m)")
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        Log.i(TAG, "Daily backup cancelled")
    }

    private fun calculateDelay(targetHour: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.before(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
