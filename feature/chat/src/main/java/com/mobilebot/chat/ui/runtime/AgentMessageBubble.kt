package com.mobilebot.chat.ui.runtime

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AgentMessageBubble(
    text: String,
    role: MessageRole,
    highlight: Boolean = false,
    buttons: List<AgentActionButton> = emptyList(),
    onButtonClick: ((AgentActionButton) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val isAgent = role == MessageRole.Agent || role == MessageRole.System

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isAgent) Arrangement.Start else Arrangement.End,
            verticalAlignment = Alignment.Top,
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 320.dp),
                shape = RoundedCornerShape(14.dp),
                colors = if (role == MessageRole.User) {
                    CardDefaults.cardColors(containerColor = Color(0xFF0D2A2A))
                } else {
                    CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))
                },
                border = if (role == MessageRole.User) {
                    BorderStroke(1.dp, if (highlight) Color(0xFF2FE8C8) else Color(0xFF2A2A2A))
                } else {
                    BorderStroke(1.dp, Color(0xFF2A2A2A))
                },
            ) {
                Text(
                    text = text,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = if (role == MessageRole.Agent) Color(0xFFE0E0E0) else Color.White,
                    fontWeight = if (role == MessageRole.User) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        // Action buttons below the message
        if (buttons.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (button in buttons) {
                    OutlinedButton(
                        onClick = { onButtonClick?.invoke(button) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFF2A2A2A)),
                    ) {
                        Text(
                            text = button.label,
                            fontSize = 12.sp,
                            color = Color(0xFFBEBEBE),
                        )
                    }
                }
            }
        }
    }
}
