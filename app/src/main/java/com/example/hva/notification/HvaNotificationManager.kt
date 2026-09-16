package com.example.hva.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.hva.runtime.HvaEnvironment

/**
 * Manages persistent and event notifications for active HVA Terminal sessions.
 */
object HvaNotificationManager {
    const val CHANNEL_ID = "hva_terminal_sessions"
    const val CHANNEL_NAME = "Hva Terminal Sessions"
    const val NOTIFICATION_ID = 1001
    const val ACTION_EXIT_APP = "com.example.hva.ACTION_EXIT_APP"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications d'état des sessions de terminal actives"
                setShowBadge(false)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    fun updateSessionNotification(
        context: Context,
        sessionCount: Int,
        activeSessionTitle: String = "sh",
        activePid: Int = -1
    ) {
        if (sessionCount <= 0) {
            cancelNotification(context)
            return
        }

        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val exitIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_EXIT_APP
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXIT_APP", true)
        }
        val exitPendingIntent = PendingIntent.getActivity(
            context,
            1,
            exitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val subtitle = if (activePid > 0) {
            "$sessionCount session(s) active(s) • $activeSessionTitle (PID: $activePid)"
        } else {
            "$sessionCount session(s) active(s) • $activeSessionTitle"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("Hva Terminal")
            .setContentText(subtitle)
            .setSubText("v${HvaEnvironment.VERSION}")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .addAction(
                android.R.drawable.ic_menu_view,
                "Ouvrir",
                pendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Quitter",
                exitPendingIntent
            )
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIFICATION_ID, notification)
    }

    fun cancelNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(NOTIFICATION_ID)
    }
}
