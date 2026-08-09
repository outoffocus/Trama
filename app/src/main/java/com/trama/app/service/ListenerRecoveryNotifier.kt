package com.trama.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.trama.app.MainActivity
import com.trama.app.NotificationConfig
import com.trama.app.R

/**
 * Requests a visible user action before restoring microphone capture.
 *
 * Android 14+ does not allow a microphone foreground service to be launched
 * from BOOT_COMPLETED or an alarm receiver. Opening Trama from this notification
 * gives the app a visible lifecycle from which it can safely restore listening.
 */
object ListenerRecoveryNotifier {
    const val EXTRA_REACTIVATE_LISTENER = "reactivate_listener"

    fun show(context: Context, reason: String): Boolean {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val manager = appContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NotificationConfig.CHANNEL_LISTENER_RECOVERY,
                "Reactivar escucha",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avisa cuando Trama necesita una acción para volver a usar el micrófono"
            }
        )

        val openApp = Intent(appContext, MainActivity::class.java)
            .putExtra(EXTRA_REACTIVATE_LISTENER, true)
            .putExtra("listener_recovery_reason", reason)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            0,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(
            appContext,
            NotificationConfig.CHANNEL_LISTENER_RECOVERY
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Trama necesita reactivar la escucha")
            .setContentText("Toca para volver a activar el micrófono")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        manager.notify(NotificationConfig.ID_LISTENER_RECOVERY, notification)
        return true
    }
}
