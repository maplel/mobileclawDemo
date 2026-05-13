package com.mobilebot.chat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val colors =
    lightColorScheme()

@Composable
fun MobileBotTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
