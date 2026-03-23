package com.mydiary.app.summary

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mydiary.app.MainActivity
import com.mydiary.app.R
import com.mydiary.app.ui.DatabaseProvider
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * WorkManager worker that runs daily at the configured time.
 * Queries the day's entries, generates a summary via Gemini Nano,
 * saves it, and shows a notification.
 */
class DailySummaryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DailySummaryWorker"
        const val CHANNEL_ID = "mydiary_daily_summary"
        private const val NOTIFICATION_ID = 2000
        private const val PREFS = "daily_summary"
        private const val KEY_LATEST = "latest_summary"
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting daily summary generation")

        try {
            val repository = DatabaseProvider.getRepository(applicationContext)
            val generator = SummaryGenerator(applicationContext)

            // Get today's entries
            val cal = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateStr = dateFormat.format(cal.time)

            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startOfDay = cal.timeInMillis

            cal.add(Calendar.DAY_OF_YEAR, 1)
            val endOfDay = cal.timeInMillis

            val entries = repository.byDateRange(startOfDay, endOfDay).first()

            if (entries.isEmpty()) {
                Log.i(TAG, "No entries today, skipping summary")
                return Result.success()
            }

            // Generate summary
            val summary = generator.generate(entries, dateStr)

            // Save to SharedPreferences
            val jsonStr = Json.encodeToString(summary)
            applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LATEST, jsonStr)
                .apply()

            Log.i(TAG, "Summary generated: ${summary.narrative}")

            // Show notification
            showNotification(summary)

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Summary generation failed", e)
            return Result.retry()
        }
    }

    private fun showNotification(summary: DailySummary) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)

        // Create channel
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Resumen diario",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Resumen y acciones sugeridas al final del dia"
            }
        )

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra("navigate_to", "summary")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val actionCount = summary.actions.size
        val subtitle = if (actionCount > 0) "$actionCount acciones sugeridas" else "Sin acciones pendientes"

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Resumen del dia")
            .setContentText(summary.narrative)
            .setSubText(subtitle)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary.narrative))
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }
}
