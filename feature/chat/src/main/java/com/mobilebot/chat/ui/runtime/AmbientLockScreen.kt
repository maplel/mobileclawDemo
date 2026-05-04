package com.mobilebot.chat.ui.runtime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AmbientLockScreen(
    time: String,
    proactiveMessage: String,
    backgroundImage: Any? = null,
    onOpenAgent: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Black gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF000000).copy(alpha = 0.6f),
                            Color(0xFF000000).copy(alpha = 0.85f),
                            Color(0xFF000000),
                        ),
                    ),
                ),
        )
        // Time at top
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp)
                .clickable(onClick = onOpenAgent),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = time,
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
                letterSpacing = 2.sp,
            )
        }
        // Proactive message at bottom
        Column(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp)
                .padding(bottom = 64.dp)
                .clickable(onClick = onOpenAgent),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF050505), RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Text(
                    text = proactiveMessage,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFFBEBEBE),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
