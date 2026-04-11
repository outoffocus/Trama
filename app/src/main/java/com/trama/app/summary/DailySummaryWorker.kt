package com.trama.app.summary

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.trama.app.MainActivity
import com.trama.app.NotificationConfig
import com.trama.app.R
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
        const val CHANNEL_ID = NotificationConfig.CHANNEL_DAILY_SUMMARY
        private const val NOTIFICATION_ID = NotificationConfig.ID_DAILY_SUMMARY
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting daily summary generation")

        try {
            val generator = DailyPageGenerator(applicationContext)

            val cal = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateStr = dateFormat.format(cal.time)

            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startOfDay = cal.timeInMillis
            val page = generator.generateAndPersist(
                dayStartMillis = startOfDay,
                status = com.trama.shared.model.DailyPageStatus.FINAL
            )

            Log.i(TAG, "Daily page generated for $dateStr: ${page.briefSummary}")

            // Show notification
            showNotification(page.briefSummary.orEmpty())

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Summary generation failed", e)
            return Result.retry()
        }
    }

    private fun showNotification(briefSummary: String) {
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
            putExtra("navigate_to", "calendar")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Memoria del dia lista")
            .setContentText(briefSummary.ifBlank { "Tu pagina diaria ya esta lista." })
            .setSubText("Abre el calendario para revisar ese dia")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(briefSummary))
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }
}
