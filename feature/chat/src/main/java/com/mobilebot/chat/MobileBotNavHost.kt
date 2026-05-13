package com.mobilebot.chat

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mobilebot.chat.ui.MobileBotTheme

@Composable
fun MobileBotApp() {
    MobileBotTheme {
        val nav = rememberNavController()
        NavHost(navController = nav, startDestination = "agent") {
            composable("agent") {
                AgentExperienceScreen(
                    onOpenChat = { nav.navigate("chat") },
                    onOpenSettings = { nav.navigate("settings") },
                )
            }
            composable("chat") {
                ChatScreen(onOpenSettings = { nav.navigate("settings") })
            }
            composable("settings") {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
