package com.mobilebot.chat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MobileBotDarkColorScheme = darkColorScheme(
    primary = Color(0xFF2FE8C8),
    onPrimary = Color(0xFF000000),
    secondary = Color(0xFF9A9A9A),
    onSecondary = Color(0xFF000000),
    tertiary = Color(0xFF4B4B4B),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF0A0A0A),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFF9A9A9A),
    outline = Color(0xFF2A2A2A),
    outlineVariant = Color(0xFF4B4B4B),
    error = Color(0xFFFF5252),
)

@Composable
fun MobileBotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MobileBotDarkColorScheme,
        content = content
    )
}
