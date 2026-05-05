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

/**
 * Periodic worker that builds an [AgendaBriefing] including calendar events
 * and pending tasks, then posts a notification linking to the AgendaScreen.
 *
 * Scheduled by [WeeklyAgendaScheduler] to fire on the user-configured day
 * of week + hour. Skips silently if there is no content to surface.
 */
class WeeklyAgendaWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "WeeklyAgendaWorker"
        const val CHANNEL_ID = NotificationConfig.CHANNEL_WEEKLY_AGENDA
        const val NOTIFICATION_ID = NotificationConfig.ID_WEEKLY_AGENDA
        const val NAVIGATE_TO_AGENDA = "agenda"
    }

    override suspend fun doWork(): Result {
        return try {
            val agenda = AgendaBriefingBuilder.build(applicationContext)
            showNotification(agenda)
            Log.i(TAG, "Weekly agenda posted: ${agenda.shortText}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Weekly agenda generation failed", e)
            Result.retry()
        }
    }

    private fun showNotification(agenda: AgendaBriefing) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Agenda semanal",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Resumen de eventos y tareas para la semana"
            }
        )

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra("navigate_to", NAVIGATE_TO_AGENDA)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(agenda.title)
            .setContentText(agenda.shortText)
            .setSubText("Toca para ver tu agenda")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(agenda.longText))
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }
}
