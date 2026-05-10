package com.chronophoto.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.getSystemService
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.chronophoto.data.db.AppDatabase
import com.chronophoto.data.db.entity.Project
import com.chronophoto.ui.components.DurationUnit
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    private const val TAG = "ReminderScheduler"

    suspend fun rescheduleAll(context: Context) {
        val projects = AppDatabase.get(context).projectDao().getActiveWithReminders()
        projects.forEach { schedule(context, it) }
        Log.d(TAG, "Rescheduled ${projects.size} project reminders")
    }

    fun schedule(context: Context, project: Project) {
        val amount = project.reminderIntervalAmount
        val unitName = project.reminderIntervalUnit
        val anchorMs = project.reminderAnchorMillis
        val unit = unitName?.let { runCatching { DurationUnit.valueOf(it) }.getOrNull() }
        if (amount == null || unit == null || anchorMs == null) {
            cancel(context, project.id)
            return
        }
        val nextMs = ReminderSchedule.nextFireMillis(System.currentTimeMillis(), amount, unit, anchorMs)
        val mgr = context.getSystemService<AlarmManager>() ?: return
        mgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMs, alarmPendingIntent(context, project.id))
        Log.d(TAG, "Scheduled project ${project.id} (${project.name}) for $nextMs (${ReminderSchedule.describe(amount, unit, anchorMs)})")
    }

    fun cancel(context: Context, projectId: Long) {
        val mgr = context.getSystemService<AlarmManager>() ?: return
        val existing = PendingIntent.getBroadcast(
            context,
            projectId.toInt(),
            alarmIntent(context, projectId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (existing != null) mgr.cancel(existing)
        cancelRefire(context, projectId)
        ReminderNotifications.cancel(context, projectId)
    }

    fun scheduleRefire(context: Context, project: Project) {
        val refire = project.reminderRefireMinutes ?: return
        if (project.reminderActiveFireMillis == null) return
        val request = OneTimeWorkRequestBuilder<ReminderRefireWorker>()
            .setInputData(workDataOf(ReminderRefireWorker.KEY_PROJECT_ID to project.id))
            .setInitialDelay(refire.toLong(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ReminderRefireWorker.uniqueName(project.id),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancelRefire(context: Context, projectId: Long) {
        WorkManager.getInstance(context)
            .cancelUniqueWork(ReminderRefireWorker.uniqueName(projectId))
    }

    suspend fun fire(context: Context, projectId: Long) {
        val db = AppDatabase.get(context)
        val project = db.projectDao().getById(projectId) ?: return
        if (project.archivedAt != null) return
        if (project.reminderIntervalAmount == null || project.reminderIntervalUnit == null || project.reminderAnchorMillis == null) return

        val now = System.currentTimeMillis()
        db.projectDao().setReminderActiveFire(projectId, now)
        ReminderNotifications.post(context, projectId, project.name)
        schedule(context, project)
        // Start the re-fire chain if configured
        val updated = db.projectDao().getById(projectId)
        if (updated != null) scheduleRefire(context, updated)
    }

    private fun alarmIntent(context: Context, projectId: Long) =
        Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderNotifications.ACTION_OPEN_CAPTURE + ".alarm"
            putExtra(ReminderNotifications.EXTRA_PROJECT_ID, projectId)
        }

    private fun alarmPendingIntent(context: Context, projectId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            projectId.toInt(),
            alarmIntent(context, projectId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
