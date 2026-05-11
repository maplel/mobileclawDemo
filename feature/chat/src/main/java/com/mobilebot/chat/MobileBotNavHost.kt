package com.mobilebot.chat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mobilebot.chat.ui.MobileBotTheme

@Composable
fun MobileBotApp() {
    MobileBotTheme {
        val nav = rememberNavController()

        NavHost(
            navController = nav,
            startDestination = "chat",
            modifier = Modifier.fillMaxSize()
        ) {
            composable("chat") {
                ChatScreen(
                    onOpenSettings = { nav.navigate("settings") }
                )
            }
            composable("settings") {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
