package com.mobilebot.chat.ui.runtime

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MobileAgentRuntimeShell(
    runtimeState: RuntimeUiState,
    onSendMessage: (String) -> Unit,
    onActionClick: (String, AgentActionButton) -> Unit,
    onOpenSettings: () -> Unit,
    onModeChange: (RuntimeMode) -> Unit = {},
    bottomBarState: BottomBarState = BottomBarState.Hidden,
    onBottomBarAction: (BottomBarAction) -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = runtimeState,
            transitionSpec = {
                (fadeIn() + slideInVertically { it / 4 }).togetherWith(
                    fadeOut() + slideOutVertically { it / 4 },
                )
            },
            label = "RuntimeShell",
        ) { state ->
            when (state) {
                is RuntimeUiState.Ambient -> {
                    AmbientLockScreen(
                        time = state.time,
                        proactiveMessage = state.proactiveMessage,
                        backgroundImage = state.backgroundImage,
                        onOpenAgent = { onModeChange(RuntimeMode.Conversation) },
                    )
                }
                is RuntimeUiState.Conversation -> {
                    AgentConversationScreen(
                        messages = state.messages,
                        isListening = state.isListening,
                        isLiveMode = state.isLiveMode,
                        state = state.state,
                        bottomBarTitle = "Agent Runtime",
                        onTalkClick = { onBottomBarAction(BottomBarAction.Talk) },
                        onLiveClick = { onBottomBarAction(BottomBarAction.Live) },
                        onButtonClick = onActionClick,
                        bottomBar = {
                            if (bottomBarState == BottomBarState.Visible) {
                                AgentBottomBar(
                                    isListening = state.isListening,
                                    isLiveMode = state.isLiveMode,
                                    onAddClick = { onBottomBarAction(BottomBarAction.Add) },
                                    onTalkClick = { onBottomBarAction(BottomBarAction.Talk) },
                                    onLiveClick = { onBottomBarAction(BottomBarAction.Live) },
                                    onShowToolGrid = { onBottomBarAction(BottomBarAction.ToolGrid) },
                                    onShowAppConnectors = { onBottomBarAction(BottomBarAction.AppConnectors) },
                                )
                            }
                        },
                    )
                }
                is RuntimeUiState.RunningTask -> {
                    AgentRunningScreen(
                        taskTitle = state.taskTitle,
                        subAgentName = state.subAgentName,
                        traceSteps = state.traceSteps,
                        messages = state.messages,
                        state = state.state,
                        onButtonClick = onActionClick,
                        bottomBar = {
                            if (bottomBarState == BottomBarState.Visible) {
                                AgentBottomBar(
                                    isListening = false,
                                    isLiveMode = state.traceSteps.any { it.status == TraceStatus.Running },
                                    onAddClick = { onBottomBarAction(BottomBarAction.Add) },
                                    onTalkClick = { onBottomBarAction(BottomBarAction.Talk) },
                                    onLiveClick = { onBottomBarAction(BottomBarAction.Live) },
                                    onShowToolGrid = { onBottomBarAction(BottomBarAction.ToolGrid) },
                                    onShowAppConnectors = { onBottomBarAction(BottomBarAction.AppConnectors) },
                                )
                            }
                        },
                    )
                }
                is RuntimeUiState.ToolGrid -> {
                    ToolGridScreen(
                        tools = state.tools,
                        onToolClick = { onBottomBarAction(BottomBarAction.ToolClick(it)) },
                        onBack = { onModeChange(RuntimeMode.Conversation) },
                    )
                }
                is RuntimeUiState.AppConnectors -> {
                    AppConnectorScreen(
                        apps = state.apps,
                        onAppClick = { onBottomBarAction(BottomBarAction.AppClick(it)) },
                        onBack = { onModeChange(RuntimeMode.Conversation) },
                    )
                }
                is RuntimeUiState.ContextResult -> {
                    ContextResultScreen(
                        contextTitle = state.contextTitle,
                        backgroundImage = state.backgroundImage,
                        userQuestion = state.userQuestion,
                        agentAnswer = state.agentAnswer,
                        bottomBar = {
                            if (bottomBarState == BottomBarState.Visible) {
                                AgentBottomBar(
                                    isListening = false,
                                    isLiveMode = true,
                                    onAddClick = { onBottomBarAction(BottomBarAction.Add) },
                                    onTalkClick = { onBottomBarAction(BottomBarAction.Talk) },
                                    onLiveClick = { onBottomBarAction(BottomBarAction.Live) },
                                    onShowToolGrid = { onBottomBarAction(BottomBarAction.ToolGrid) },
                                    onShowAppConnectors = { onBottomBarAction(BottomBarAction.AppConnectors) },
                                )
                            }
                        },
                    )
                }
            }
        }

        // Settings button at top-right
        if (runtimeState is RuntimeUiState.Conversation || runtimeState is RuntimeUiState.RunningTask) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1A1A1A))
                    .clickable { onOpenSettings() }
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "⚙",
                    fontSize = 14.sp,
                    color = Color(0xFF6F6F6F),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

enum class RuntimeMode {
    Ambient,
    Conversation,
    RunningTask,
    ToolGrid,
    AppConnectors,
    ContextResult,
}

enum class BottomBarState {
    Visible,
    Hidden,
}

sealed interface BottomBarAction {
    object Add : BottomBarAction
    object Type : BottomBarAction
    object Talk : BottomBarAction
    object Live : BottomBarAction
    object ToolGrid : BottomBarAction
    object AppConnectors : BottomBarAction
    data class ToolClick(val tool: AgentTool) : BottomBarAction
    data class AppClick(val app: AppConnector) : BottomBarAction
}
