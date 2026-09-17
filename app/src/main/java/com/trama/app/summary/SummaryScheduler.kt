package com.trama.app.summary

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Materializes yesterday's private memory without adding a user-facing surface. */
object SummaryScheduler {

    private const val TAG = "SummaryScheduler"
    private const val WORK_NAME = "daily_summary"
    private const val MEMORY_HOUR = 3

    /**
     * Runs after the day has closed. The existing work name is retained so upgrades
     * replace the former summary-and-notification schedule instead of duplicating it.
     */
    fun schedule(context: Context) {
        val delay = calculateDelay(MEMORY_HOUR, 0)

        val workRequest = PeriodicWorkRequestBuilder<DailySummaryWorker>(
            1, TimeUnit.DAYS
        )
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )

        val hours = delay / (1000 * 60 * 60)
        val mins = (delay / (1000 * 60)) % 60
        Log.i(TAG, "Private daily memory scheduled at 03:00 (in ${hours}h ${mins}m)")
    }

    /**
     * Run the summary immediately (for testing or manual trigger).
     */
    fun runNow(context: Context) {
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<DailySummaryWorker>()
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "daily-summary-now",
            androidx.work.ExistingWorkPolicy.REPLACE,
            workRequest
        )
        Log.i(TAG, "Private daily memory triggered immediately")
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
