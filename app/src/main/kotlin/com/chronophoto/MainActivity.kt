package com.chronophoto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chronophoto.ui.nav.ChronophotoNavHost
import com.chronophoto.ui.theme.ChronophotoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChronophotoTheme {
                ChronophotoNavHost()
            }
        }
    }
}
