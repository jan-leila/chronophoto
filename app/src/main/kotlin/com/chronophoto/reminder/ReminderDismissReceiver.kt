package com.chronophoto.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chronophoto.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val projectId = intent.getLongExtra(ReminderNotifications.EXTRA_PROJECT_ID, -1L)
        if (projectId < 0) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AppDatabase.get(app).projectDao().setReminderActiveFire(projectId, null)
                ReminderNotifications.cancel(app, projectId)
                ReminderScheduler.cancelRefire(app, projectId)
            } finally {
                pending.finish()
            }
        }
    }
}
