package com.mobilebot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mobilebot.chat.PopupChatScreen
import com.mobilebot.chat.ui.MobileBotTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PopupChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Ensure this activity is treated as a high-priority overlay
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        setContent {
            MobileBotTheme {
                PopupChatScreen(onDismiss = { finish() })
            }
        }
    }
}
