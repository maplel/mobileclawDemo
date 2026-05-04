package com.mobilebot.chat.ui.runtime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AgentConversationScreen(
    messages: List<AgentMessage>,
    isListening: Boolean = false,
    isLiveMode: Boolean = false,
    state: String = "",
    bottomBarTitle: String = "Agent Runtime",
    onTalkClick: () -> Unit = {},
    onLiveClick: () -> Unit = {},
    onButtonClick: ((String, AgentActionButton) -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Top status area
        AgentTopBar(
            title = bottomBarTitle,
            state = state,
            isListening = isListening,
            isLiveMode = isLiveMode,
            onTalkClick = onTalkClick,
            onLiveClick = onLiveClick,
        )

        // Status indicator
        if (state.isNotBlank() && state != "RESPONDING" && state != "OBSERVING") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state == "THINKING" || state == "EXECUTING_TOOL") {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFF2FE8C8), CircleShape),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFF6F6F6F), CircleShape),
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = state,
                    fontSize = 10.sp,
                    color = Color(0xFF6F6F6F),
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // Messages
        val filteredMessages = messages.filter { it.role != MessageRole.System }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(filteredMessages, key = { it.id }) { message ->
                AgentMessageBubble(
                    text = message.text,
                    role = message.role,
                    highlight = message.role == MessageRole.User,
                    buttons = message.actionButtons,
                    onButtonClick = { btn -> onButtonClick?.invoke(message.id, btn) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (filteredMessages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Ask MobileBot to do something.",
                            fontSize = 14.sp,
                            color = Color(0xFF6F6F6F),
                            fontWeight = FontWeight.Normal,
                        )
                    }
                }
            }
        }

        // Bottom bar
        bottomBar()
    }
}

@Composable
private fun AgentTopBar(
    title: String,
    state: String,
    isListening: Boolean,
    isLiveMode: Boolean,
    onTalkClick: () -> Unit,
    onLiveClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // User initial
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(Color(0xFF3A3A3A), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "NY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }

        // Agent status dot (overlaps)
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(Color(0xFF1A1A1A), CircleShape)
                .border(1.5.dp, Color(0xFF2FE8C8), CircleShape)
                .padding(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        if (isListening) Color(0xFFFF5252)
                        else if (isLiveMode) Color(0xFF2FE8C8)
                        else Color(0xFF6F6F6F),
                        CircleShape,
                    ),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // State badge
        if (state.isNotBlank()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF1A1A1A))
                    .padding(horizontal = 8.dp)
                    .padding(vertical = 3.dp),
            ) {
                Text(
                    text = state,
                    fontSize = 9.sp,
                    color = Color(0xFF6F6F6F),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
