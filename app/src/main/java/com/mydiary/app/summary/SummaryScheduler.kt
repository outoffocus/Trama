package com.mydiary.app.summary

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Schedules the daily summary worker at the user-configured time.
 */
object SummaryScheduler {

    private const val TAG = "SummaryScheduler"
    private const val WORK_NAME = "daily_summary"

    /**
     * Schedule the daily summary at the given hour (0-23).
     * If the time has already passed today, it schedules for tomorrow.
     */
    fun schedule(context: Context, hour: Int, minute: Int = 0) {
        val delay = calculateDelay(hour, minute)

        val workRequest = PeriodicWorkRequestBuilder<DailySummaryWorker>(
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
        Log.i(TAG, "Daily summary scheduled at $hour:${minute.toString().padStart(2, '0')} (in ${hours}h ${mins}m)")
    }

    /**
     * Cancel the scheduled summary.
     */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        Log.i(TAG, "Daily summary cancelled")
    }

    /**
     * Run the summary immediately (for testing or manual trigger).
     */
    fun runNow(context: Context) {
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<DailySummaryWorker>()
            .build()
        WorkManager.getInstance(context).enqueue(workRequest)
        Log.i(TAG, "Daily summary triggered immediately")
    }

    private fun calculateDelay(targetHour: Int, targetMinute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If the target time already passed today, schedule for tomorrow
        if (target.before(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        return target.timeInMillis - now.timeInMillis
    }
}
