package com.trama.app.summary

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Materializes the most recent completed day for private retrieval and chat context.
 * It has no user-facing output: Home is built from source events and actionable items.
 */
class DailySummaryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DailySummaryWorker"
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting private daily memory generation")

        try {
            val generator = DailyPageGenerator(applicationContext)
            val startOfDay = DailyMemoryPolicy.previousDayStart(System.currentTimeMillis())
            val page = generator.generateAndPersist(
                dayStartMillis = startOfDay,
                status = com.trama.shared.model.DailyPageStatus.FINAL
            )
            Log.i(TAG, "Private daily memory generated for ${page.date}")

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Summary generation failed", e)
            return Result.retry()
        }
    }

}
