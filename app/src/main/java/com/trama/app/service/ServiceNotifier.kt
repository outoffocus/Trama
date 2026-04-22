package com.trama.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.trama.app.MainActivity
import com.trama.app.NotificationConfig
import com.trama.app.R
import com.trama.shared.model.DiaryEntry

/**
 * Owns the foreground notification shown by the listener service plus one-off
 * "new entry" notifications. Keeps channel creation, text memoization and the
 * notification builders out of the service body.
 */
class ServiceNotifier(context: Context) {

    private val appContext: Context = context.applicationContext
    private val manager: NotificationManager =
        appContext.getSystemService(NotificationManager::class.java)

    @Volatile private var lastForegroundText: String = ""

    val foregroundId: Int = NotificationConfig.ID_LISTENER

    fun createChannels() {
        manager.createNotificationChannel(
            NotificationChannel(
                NotificationConfig.CHANNEL_LISTENER,
                "Servicio de escucha",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muestra el estado del servicio de escucha de palabras clave"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                NotificationConfig.CHANNEL_NEW_ENTRY,
                "Nuevas entradas",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaciones cuando se captura una nueva entrada"
            }
        )
    }

    fun buildForeground(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            appContext, 0, Intent(appContext, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(appContext, NotificationConfig.CHANNEL_LISTENER)
            .setContentTitle("Trama")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    fun updateForegroundIfChanged(text: String) {
        if (text == lastForegroundText) return
        lastForegroundText = text
        manager.notify(foregroundId, buildForeground(text))
    }

    fun showNewEntry(entry: DiaryEntry) {
        val pendingIntent = PendingIntent.getActivity(
            appContext, 0, Intent(appContext, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val reviewBadge = if (entry.wasReviewedByLLM) " (revisado por IA)" else ""
        val notification = NotificationCompat.Builder(appContext, NotificationConfig.CHANNEL_NEW_ENTRY)
            .setContentTitle("${entry.category}$reviewBadge")
            .setContentText(entry.displayText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(entry.id.toInt() + 1000, notification)
    }
}
