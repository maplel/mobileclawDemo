package com.mobilebot.chat.ui.runtime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AgentRunningScreen(
    taskTitle: String,
    subAgentName: String,
    traceSteps: List<AgentTraceStep>,
    messages: List<AgentMessage>,
    state: String = "",
    onButtonClick: ((String, AgentActionButton) -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Top half: Execution trace panel
        Box(
            modifier = Modifier
                .fillMaxHeight(0.35f)
                .padding(horizontal = 12.dp)
                .padding(top = 16.dp, bottom = 8.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            AgentExecutionTracePanel(
                taskTitle = taskTitle,
                subAgentName = subAgentName,
                steps = traceSteps,
            )
        }

        // Bottom half: Messages
        Column(modifier = Modifier.weight(1f)) {
            // Status indicator
            if (state.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFF2FE8C8), CircleShape),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = state,
                        fontSize = 10.sp,
                        color = Color(0xFF6F6F6F),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            val filteredMessages = messages.filter { it.role != MessageRole.System }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 4.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filteredMessages, key = { it.id }) { msg ->
                    AgentMessageBubble(
                        text = msg.text,
                        role = msg.role,
                        highlight = msg.role == MessageRole.User,
                        buttons = msg.actionButtons,
                        onButtonClick = { btn -> onButtonClick?.invoke(msg.id, btn) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            bottomBar()
        }
    }
}
