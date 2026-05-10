package com.chronophoto.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chronophoto.data.db.AppDatabase

class ReminderRefireWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val projectId = inputData.getLong(KEY_PROJECT_ID, -1L)
        if (projectId < 0) return Result.failure()

        val app = applicationContext
        val project = AppDatabase.get(app).projectDao().getById(projectId)
            ?: return Result.success()

        // Chain ends if no active fire (photo taken / dismissed) or no re-fire configured.
        if (project.reminderActiveFireMillis == null) return Result.success()
        if (project.reminderRefireMinutes == null) return Result.success()
        if (project.archivedAt != null) return Result.success()

        ReminderNotifications.post(app, projectId, project.name)
        ReminderScheduler.scheduleRefire(app, project)
        return Result.success()
    }

    companion object {
        const val KEY_PROJECT_ID = "projectId"
        fun uniqueName(projectId: Long): String = "reminder-refire-$projectId"
    }
}
