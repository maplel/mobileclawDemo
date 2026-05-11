package com.mobilebot.chat.ui.runtime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ContextResultScreen(
    contextTitle: String,
    backgroundImage: Any? = null,
    userQuestion: String = "",
    agentAnswer: String = "",
    bottomBar: @Composable () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Background with gradient overlay
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF000000).copy(alpha = 0.7f),
                                Color(0xFF000000).copy(alpha = 0.85f),
                            ),
                        ),
                    ),
            )
        }

        // Content overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // Context title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF2FE8C8)),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = contextTitle,
                    fontSize = 12.sp,
                    color = Color(0xFF9A9A9A),
                    fontWeight = FontWeight.Medium,
                )
            }

            HorizontalDivider(color = Color(0xFF2A2A2A).copy(alpha = 0.5f))

            // User question
            if (userQuestion.isNotBlank()) {
                AgentMessageBubble(
                    text = userQuestion,
                    role = MessageRole.User,
                    highlight = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(vertical = 12.dp),
                )
            }

            // Agent answer
            if (agentAnswer.isNotBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                    border = BorderStroke(1.dp, Color(0xFF2A2A2A)),
                ) {
                    Text(
                        text = agentAnswer,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = Color(0xFFE0E0E0),
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            // Bottom bar spacer
            Spacer(modifier = Modifier.height(80.dp))

            // Bottom bar
            bottomBar()
        }
    }
}
