package com.mobilebot.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobilebot.domain.permissions.CapabilityApprovalResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatOverlayContent(
    lines: List<ChatLine>,
    busy: Boolean,
    runtimeState: String,
    onSend: (String) -> Unit,
    onActionSelected: (ChatLine.ActionPrompt, ActionButton) -> Unit
) {
    ChatContent(
        lines = lines,
        busy = busy,
        runtimeState = runtimeState,
        onSend = onSend,
        onActionSelected = onActionSelected
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val lines by viewModel.lines.collectAsState()
    val currentChatId by viewModel.currentChatId.collectAsState()
    val sessionsList by viewModel.sessionsList.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val runtimeState by viewModel.runtimeState.collectAsState()
    val pendingCapRequest by viewModel.pendingCapabilityRequest.collectAsState()
    var sessionMenuOpen by remember { mutableStateOf(false) }

    pendingCapRequest?.let { request ->
        PermissionChoiceDialog(
            capabilityNames = request.capabilityNames,
            onResult = { viewModel.respondToCapabilityRequest(it) },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("MobileBot", color = MaterialTheme.colorScheme.onBackground) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    IconButton(
                        onClick = { viewModel.startNewChat() },
                        enabled = !busy,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New chat", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    Box {
                        IconButton(
                            onClick = {
                                viewModel.refreshChatSessions()
                                sessionMenuOpen = true
                            },
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Chat history", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        DropdownMenu(
                            expanded = sessionMenuOpen,
                            onDismissRequest = { sessionMenuOpen = false },
                        ) {
                            if (sessionsList.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No saved chats") },
                                    onClick = { sessionMenuOpen = false },
                                )
                            } else {
                                sessionsList.forEach { session ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = session.title,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = formatSessionTime(session.updatedAt),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        },
                                        onClick = {
                                            viewModel.switchChat(session.chatId)
                                            sessionMenuOpen = false
                                        },
                                        leadingIcon = {
                                            if (session.chatId == currentChatId) {
                                                Text("•", color = MaterialTheme.colorScheme.primary)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            ChatContent(
                lines = lines,
                busy = busy,
                runtimeState = runtimeState,
                onSend = { viewModel.send(it) },
                onActionSelected = { prompt, action -> viewModel.onActionSelected(prompt, action) }
            )
        }
    }
}

@Composable
fun PopupChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onDismiss: () -> Unit
) {
    val lines by viewModel.lines.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val runtimeState by viewModel.runtimeState.collectAsState()
    val pendingCapRequest by viewModel.pendingCapabilityRequest.collectAsState()

    pendingCapRequest?.let { request ->
        PermissionChoiceDialog(
            capabilityNames = request.capabilityNames,
            onResult = { viewModel.respondToCapabilityRequest(it) },
        )
    }

    // A translucent overlay that dismisses when clicking background
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Separate background layer to avoid click propagation
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        )

        // Floating dialog layout - intercepting all internal clicks
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.72f)
                .padding(bottom = 20.dp) // Leave space for system bar
                .clickable(enabled = true, onClick = {}), // Hard intercept
            color = Color(0xFF0A0A0A),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Color(0xFF2A2A2A)),
            shadowElevation = 8.dp
        ) {
            ChatContent(
                lines = lines,
                busy = busy,
                runtimeState = runtimeState,
                onSend = { viewModel.send(it) },
                onActionSelected = { prompt, action -> viewModel.onActionSelected(prompt, action) }
            )
        }
    }
}

@Composable
fun ChatContent(
    lines: List<ChatLine>,
    busy: Boolean,
    runtimeState: String,
    onSend: (String) -> Unit,
    onActionSelected: (ChatLine.ActionPrompt, ActionButton) -> Unit
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(bottom = 12.dp)
    ) {
        // --- AGENT WORKSPACE (Top Grey Area) ---
        val progressHistory = lines.filterIsInstance<ChatLine.Progress>()
            .takeLast(3)
            .map { formatToNaturalEnglish(it.text) }
            .distinct()

        val latestPlan = lines.filterIsInstance<ChatLine.TodoList>().lastOrNull()
        val latestSubtask = lines.filterIsInstance<ChatLine.SubtaskPanel>().lastOrNull()
        
        val currentStatus = if (busy) {
            when (runtimeState.uppercase()) {
                "RESPONDING" -> "Preparing the final response..."
                "THINKING" -> "Analyzing your request..."
                "EXECUTING" -> "Processing tasks..."
                else -> runtimeState.ifBlank { "Agent is active..." }
            }
        } else null

        if (progressHistory.isNotEmpty() || latestPlan != null || latestSubtask != null || currentStatus != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .padding(12.dp),
                color = Color(0xFF1A1A1A),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF333333))
            ) {
                Column(modifier = Modifier.padding(12.dp).animateContentSize()) {
                    Text(
                        text = "AGENT WORKSPACE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6F6F6F),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        progressHistory.forEach { milestone ->
                            Text(
                                text = "• $milestone",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                        if (currentStatus != null && !progressHistory.contains(currentStatus)) {
                            Text(
                                text = "• $currentStatus",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF2FE8C8),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    if (latestPlan != null || latestSubtask != null) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFF2A2A2A))
                        latestPlan?.let { TopTodoList(it) }
                        latestSubtask?.let { 
                            if (latestPlan == null || it.status == SubtaskDisplayStatus.RUNNING) {
                                 TopSubtaskPanel(it) 
                            }
                        }
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val displayLines = lines.filter { 
                it is ChatLine.User || it is ChatLine.Assistant || it is ChatLine.ActionPrompt || it is ChatLine.SystemNote
            }
            
            itemsIndexed(displayLines) { _, line ->
                when (line) {
                    is ChatLine.User -> UserMessageBubble(line.text)
                    is ChatLine.Assistant -> BotMessageBubble(line.text)
                    is ChatLine.ActionPrompt -> ActionPromptCard(line, { action -> onActionSelected(line, action) })
                    is ChatLine.SystemNote ->
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = Color(0xFF6F6F6F),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            textAlign = TextAlign.Center,
                        )
                    else -> {}
                }
            }
        }

        // Minimalist Input Area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask anything...", color = Color(0xFF6F6F6F)) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF0A0A0A),
                    unfocusedContainerColor = Color(0xFF0A0A0A),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                ),
                shape = RoundedCornerShape(24.dp),
                minLines = 1,
                maxLines = 4
            )
            Spacer(modifier = Modifier.width(10.dp))
            IconButton(
                onClick = {
                    if (input.isNotBlank() && !busy) {
                        onSend(input)
                        input = ""
                    }
                },
                enabled = !busy && input.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .background(if (!busy && input.isNotBlank()) Color(0xFF2FE8C8) else Color(0xFF2A2A2A), RoundedCornerShape(24.dp))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (!busy && input.isNotBlank()) Color.Black else Color(0xFF6F6F6F)
                )
            }
        }
    }
}

@Composable
private fun UserMessageBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = Color(0xFF1A1A1A),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 2.dp),
            modifier = Modifier.widthIn(max = 260.dp)
        ) {
            Text(text = text, modifier = Modifier.padding(12.dp), color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun BotMessageBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp),
            modifier = Modifier.widthIn(max = 260.dp),
            border = BorderStroke(1.dp, Color(0xFF2A2A2A))
        ) {
            Text(text = text, modifier = Modifier.padding(12.dp), color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TopTodoList(todo: ChatLine.TodoList) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = todo.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF2FE8C8))
        Spacer(modifier = Modifier.height(4.dp))
        todo.items.forEach { item ->
            val prefix = when (item.status) {
                TodoDisplayStatus.PENDING -> "○"
                TodoDisplayStatus.RUNNING -> "●"
                TodoDisplayStatus.COMPLETED -> "✓"
                TodoDisplayStatus.FAILED -> "✕"
            }
            Text(
                text = "$prefix ${item.text}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                textDecoration = if (item.status == TodoDisplayStatus.COMPLETED) TextDecoration.LineThrough else null,
                modifier = Modifier.padding(vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun TopSubtaskPanel(panel: ChatLine.SubtaskPanel) {
    val statusIcon = when (panel.status) {
        SubtaskDisplayStatus.SPAWNED -> "⏳"
        SubtaskDisplayStatus.RUNNING -> "🔄"
        SubtaskDisplayStatus.COMPLETED -> "✅"
        SubtaskDisplayStatus.FAILED -> "❌"
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = "$statusIcon ${panel.label}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
        if (panel.entries.isNotEmpty()) {
            Text(text = panel.entries.last(), style = MaterialTheme.typography.bodySmall, color = Color(0xFF9A9A9A), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ActionPromptCard(
    prompt: ChatLine.ActionPrompt,
    onAction: (ActionButton) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
        border = BorderStroke(1.dp, Color(0xFF2A2A2A)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = prompt.text, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                for (action in prompt.actions) {
                    val isSelected = prompt.answered && prompt.selectedAction == action.label
                    if (isSelected) {
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = Color(0xFF2FE8C8).copy(alpha = 0.2f),
                                disabledContentColor = Color(0xFF2FE8C8),
                            ),
                        ) {
                            Text(action.label)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onAction(action) },
                            enabled = !prompt.answered,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, Color(0xFF2A2A2A))
                        ) {
                            Text(action.label, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionChoiceDialog(
    capabilityNames: List<String>,
    onResult: (CapabilityApprovalResult) -> Unit,
) {
    Dialog(onDismissRequest = { onResult(CapabilityApprovalResult.DENY) }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0A0A0A),
            modifier = Modifier.widthIn(min = 280.dp, max = 360.dp),
            border = BorderStroke(1.dp, Color(0xFF2A2A2A))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(24.dp))
                Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = Color(0xFF2FE8C8), modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Allow MobileBot to access\n${capabilityNames.joinToString("、")}?", style = MaterialTheme.typography.titleMedium, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = Color(0xFF2A2A2A))
                TextButton(onClick = { onResult(CapabilityApprovalResult.ALWAYS) }, modifier = Modifier.fillMaxWidth()) { Text("Always", color = Color.White) }
                HorizontalDivider(color = Color(0xFF2A2A2A))
                TextButton(onClick = { onResult(CapabilityApprovalResult.WHILE_USING_APP) }, modifier = Modifier.fillMaxWidth()) { Text("While using app", color = Color.White) }
                HorizontalDivider(color = Color(0xFF2A2A2A))
                TextButton(onClick = { onResult(CapabilityApprovalResult.ASK_EVERY_TIME) }, modifier = Modifier.fillMaxWidth()) { Text("Ask every time", color = Color.White) }
                HorizontalDivider(color = Color(0xFF2A2A2A))
                TextButton(onClick = { onResult(CapabilityApprovalResult.DENY) }, modifier = Modifier.fillMaxWidth()) { Text("Don't allow", color = Color(0xFF6F6F6F)) }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

private fun formatToNaturalEnglish(text: String): String {
    val clean = text.replace(Regex("^Tool\\s+\\w+:"), "").trim()
    return when {
        clean.contains("Plan presented", ignoreCase = true) -> "Synthesizing a structured execution plan..."
        clean.contains("Searching", ignoreCase = true) -> "Gathering relevant information from external sources..."
        clean.contains("thinking", ignoreCase = true) -> "Refining the strategy based on your request..."
        clean.length > 60 -> clean.take(57) + "..."
        else -> clean.ifBlank { "Processing..." }.replaceFirstChar { it.uppercase() }
    }
}

private fun formatSessionTime(updatedAt: Long): String {
    val formatter = SimpleDateFormat("MM-dd HH:mm", Locale.US)
    return formatter.format(Date(updatedAt))
}
