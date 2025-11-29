package com.netomi.assignment.rtchatbotapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.netomi.assignment.rtchatbotapp.ui.AppNavHost
import com.netomi.assignment.rtchatbotapp.ui.theme.RTChatBotAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RTChatBotAppTheme {
                AppNavHost()
            }
        }
    }
}