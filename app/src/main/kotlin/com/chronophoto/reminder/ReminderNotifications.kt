package com.chronophoto.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.chronophoto.MainActivity
import com.chronophoto.R

object ReminderNotifications {

    const val CHANNEL_ID = "chronophoto.reminders"
    const val EXTRA_PROJECT_ID = "projectId"
    const val ACTION_OPEN_CAPTURE = "com.chronophoto.action.OPEN_CAPTURE"
    const val ACTION_DISMISS_REMINDER = "com.chronophoto.action.DISMISS_REMINDER"

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService<NotificationManager>() ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Photo reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Daily / interval reminders to capture project photos."
        }
        mgr.createNotificationChannel(channel)
    }

    fun post(context: Context, projectId: Long, projectName: String) {
        ensureChannel(context)
        val mgr = context.getSystemService<NotificationManager>() ?: return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_CAPTURE
            putExtra(EXTRA_PROJECT_ID, projectId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            context,
            projectId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val dismissIntent = Intent(context, ReminderDismissReceiver::class.java).apply {
            action = ACTION_DISMISS_REMINDER
            putExtra(EXTRA_PROJECT_ID, projectId)
        }
        val dismissPi = PendingIntent.getBroadcast(
            context,
            (projectId * 31 + 1).toInt(),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Time to capture: $projectName")
            .setContentText("Tap to take today's photo.")
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(android.R.drawable.ic_menu_camera, "Take photo", openPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPi)
            .build()

        mgr.notify(notificationIdFor(projectId), notification)
    }

    fun cancel(context: Context, projectId: Long) {
        val mgr = context.getSystemService<NotificationManager>() ?: return
        mgr.cancel(notificationIdFor(projectId))
    }

    fun notificationIdFor(projectId: Long): Int = (projectId and 0x7fffffff).toInt()
}
