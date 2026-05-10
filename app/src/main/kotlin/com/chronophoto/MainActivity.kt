package com.chronophoto

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import com.chronophoto.reminder.ReminderNotifications
import com.chronophoto.ui.nav.ChronophotoNavHost
import com.chronophoto.ui.nav.Routes
import com.chronophoto.ui.theme.ChronophotoTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val pendingIntentFlow = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingIntentFlow.value = intent
        enableEdgeToEdge()
        setContent {
            ChronophotoTheme {
                val navController = rememberNavController()
                ChronophotoNavHost(navController)

                val current by pendingIntentFlow.collectAsState()
                LaunchedEffect(current) {
                    val ci = current ?: return@LaunchedEffect
                    if (ci.action == ReminderNotifications.ACTION_OPEN_CAPTURE) {
                        val pid = ci.getLongExtra(ReminderNotifications.EXTRA_PROJECT_ID, -1L)
                        if (pid >= 0) {
                            navController.navigate(Routes.capture(pid))
                            ReminderNotifications.cancel(applicationContext, pid)
                        }
                    }
                    pendingIntentFlow.value = null
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIntentFlow.value = intent
    }
}
