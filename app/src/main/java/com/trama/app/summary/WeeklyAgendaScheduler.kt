package com.trama.app.summary

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Schedules the weekly agenda worker to fire on the user-configured day of
 * week + hour. WorkManager's periodic minimum is 15 min; we use a 7-day
 * interval and align the first run with [calculateInitialDelay].
 */
object WeeklyAgendaScheduler {

    private const val TAG = "WeeklyAgendaScheduler"
    private const val WORK_NAME = "weekly_agenda"

    /** [dayOfWeek] uses [Calendar] constants (SUNDAY=1..SATURDAY=7). */
    fun schedule(context: Context, dayOfWeek: Int, hour: Int, minute: Int = 0) {
        val delay = calculateInitialDelay(dayOfWeek, hour, minute)
        val request = PeriodicWorkRequestBuilder<WeeklyAgendaWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        val hours = delay / (1000 * 60 * 60)
        Log.i(TAG, "Weekly agenda scheduled (dow=$dayOfWeek hour=$hour, first run in ${hours}h)")
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        Log.i(TAG, "Weekly agenda cancelled")
    }

    /** Fire the worker once, immediately. Useful for "Probar ahora". */
    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<WeeklyAgendaWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "weekly-agenda-now",
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
        Log.i(TAG, "Weekly agenda triggered immediately")
    }

    private fun calculateInitialDelay(targetDow: Int, targetHour: Int, targetMinute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Roll forward until target falls on the requested day-of-week
        // (and is in the future).
        while (target.get(Calendar.DAY_OF_WEEK) != targetDow || !target.after(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
