package com.mobilebot.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.window.Dialog
import com.mobilebot.domain.permissions.CapabilityApprovalResult
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val lines by viewModel.lines.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val chatSessions by viewModel.chatSessions.collectAsState()
    val currentChatId by viewModel.currentChatId.collectAsState()
    val pendingCapRequest by viewModel.pendingCapabilityRequest.collectAsState()
    var input by remember { mutableStateOf("") }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    pendingCapRequest?.let { request ->
        PermissionChoiceDialog(
            capabilityNames = request.capabilityNames,
            onResult = { viewModel.respondToCapabilityRequest(it) },
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatHistoryDrawer(
                sessions = chatSessions,
                currentChatId = currentChatId,
                onNewChat = {
                    viewModel.newChat()
                    scope.launch { drawerState.close() }
                },
                onSelectChat = { chatId ->
                    viewModel.switchChat(chatId)
                    scope.launch { drawerState.close() }
                },
                onDeleteChat = viewModel::deleteChat,
            )
        },
    ) {
        Scaffold(
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            topBar = {
                TopAppBar(
                    title = { Text("MobileBot") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Chat history")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            viewModel.newChat()
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "New chat")
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("settings_button"),
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .imePadding()
                        .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LazyColumn(
                    modifier =
                        Modifier
                            .weight(1f)
                            .testTag("message_list"),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(
                        lines,
                        key = { index, line ->
                            when (line) {
                                is ChatLine.SubtaskGroup -> "subtask-group"
                                is ChatLine.SubtaskPanel -> "st-${line.taskId}"
                                is ChatLine.ActionPrompt -> "action-prompt-$index"
                                is ChatLine.TodoList -> "todo-${line.listId}"
                                else -> index
                            }
                        },
                    ) { _, line ->
                        when (line) {
                            is ChatLine.User ->
                                Text(
                                    text = "You: ${line.text}",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            is ChatLine.Assistant ->
                                Text(
                                    text = "Bot: ${line.text}",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            is ChatLine.Progress ->
                                Text(
                                    text = "… ${line.text}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            is ChatLine.SystemNote ->
                                Text(
                                    text = line.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    textAlign = TextAlign.Center,
                                )
                            is ChatLine.SubtaskGroup -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "— ${line.panels.size} parallel subtasks —",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    line.panels.values
                                        .sortedBy { it.taskId }
                                        .forEach { panel ->
                                            key(panel.taskId) {
                                                SubtaskCard(panel = panel)
                                            }
                                        }
                                }
                            }
                            is ChatLine.ActionPrompt ->
                                ActionPromptCard(
                                    prompt = line,
                                    onAction = { action -> viewModel.onActionSelected(line, action) },
                                )
                            is ChatLine.TodoList ->
                                TodoListCard(line)
                            is ChatLine.SubtaskPanel ->
                                SubtaskCard(panel = line)
                        }
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("chat_input"),
                    label = { Text("Message") },
                    minLines = 2,
                )
                Button(
                    onClick = {
                        viewModel.send(input)
                        input = ""
                    },
                    enabled = !busy && input.isNotBlank(),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("send_button"),
                ) {
                    Text(if (busy) "Sending…" else "Send")
                }
            }
        }
    }
}

@Composable
private fun TodoListCard(todo: ChatLine.TodoList) {
    val running = todo.items.any { it.status == TodoDisplayStatus.RUNNING }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Plan",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
            if (running) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            todo.items.forEachIndexed { index, item ->
                val prefix =
                    when (item.status) {
                        TodoDisplayStatus.PENDING -> "○"
                        TodoDisplayStatus.RUNNING -> "◔"
                        TodoDisplayStatus.COMPLETED -> "✓"
                        TodoDisplayStatus.FAILED -> "✕"
                    }
                val color =
                    when (item.status) {
                        TodoDisplayStatus.PENDING -> MaterialTheme.colorScheme.onSurface
                        TodoDisplayStatus.RUNNING -> MaterialTheme.colorScheme.secondary
                        TodoDisplayStatus.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant
                        TodoDisplayStatus.FAILED -> MaterialTheme.colorScheme.error
                    }
                Text(
                    text = "$prefix ${item.text}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                    textDecoration = if (item.status == TodoDisplayStatus.COMPLETED) TextDecoration.LineThrough else null,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
                if (index != todo.items.lastIndex) {
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
private fun ChatHistoryDrawer(
    sessions: List<ChatSessionInfo>,
    currentChatId: String,
    onNewChat: () -> Unit,
    onSelectChat: (String) -> Unit,
    onDeleteChat: (String) -> Unit,
) {
    ModalDrawerSheet {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Chat History",
            modifier = Modifier.padding(horizontal = 24.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onNewChat,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Chat")
        }
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()

        if (sessions.isEmpty()) {
            Text(
                "No chat history yet",
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn {
                items(sessions, key = { it.chatId }) { session ->
                    val selected = session.chatId == currentChatId
                    ListItem(
                        headlineContent = {
                            Text(
                                session.preview,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        supportingContent = {
                            Text(
                                relativeTime(session.updatedAt),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { onDeleteChat(session.chatId) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete chat",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        colors =
                            ListItemDefaults.colors(
                                containerColor =
                                    if (selected) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                            ),
                        modifier =
                            Modifier.clickable { onSelectChat(session.chatId) },
                    )
                }
            }
        }
    }
}

/**
 * Mimics the standard Android runtime-permission dialog (Android 11+).
 *
 * Layout:  permission icon  →  title  →  divider  →  four stacked choices.
 *
 * Labels use the exact wording from Android's Chinese locale:
 * 始终 · 仅在使用该应用时允许 · 每次都询问 · 不允许
 */
@Composable
private fun PermissionChoiceDialog(
    capabilityNames: List<String>,
    onResult: (CapabilityApprovalResult) -> Unit,
) {
    Dialog(onDismissRequest = { onResult(CapabilityApprovalResult.DENY) }) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(min = 280.dp, max = 360.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(24.dp))

                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "允许 MobileBot 访问\n${capabilityNames.joinToString("、")}？",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                TextButton(
                    onClick = { onResult(CapabilityApprovalResult.ALWAYS) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("始终")
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                TextButton(
                    onClick = { onResult(CapabilityApprovalResult.WHILE_USING_APP) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("仅在使用该应用时允许")
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                TextButton(
                    onClick = { onResult(CapabilityApprovalResult.ASK_EVERY_TIME) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("每次都询问")
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                TextButton(
                    onClick = { onResult(CapabilityApprovalResult.DENY) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("不允许")
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SubtaskCard(panel: ChatLine.SubtaskPanel) {
    val (borderColor, containerColor) = when (panel.status) {
        SubtaskDisplayStatus.SPAWNED,
        SubtaskDisplayStatus.RUNNING ->
            MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        SubtaskDisplayStatus.COMPLETED ->
            MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        SubtaskDisplayStatus.FAILED ->
            MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
    }
    val statusIcon = when (panel.status) {
        SubtaskDisplayStatus.SPAWNED -> "⏳"
        SubtaskDisplayStatus.RUNNING -> "🔄"
        SubtaskDisplayStatus.COMPLETED -> "✅"
        SubtaskDisplayStatus.FAILED -> "❌"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "$statusIcon ${panel.label}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = borderColor,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = panel.status.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = borderColor,
                )
            }
            if (panel.status == SubtaskDisplayStatus.RUNNING) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = borderColor,
                    trackColor = borderColor.copy(alpha = 0.2f),
                )
            }
            if (panel.entries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                val displayEntries = if (panel.entries.size > MAX_VISIBLE_ENTRIES) {
                    panel.entries.takeLast(MAX_VISIBLE_ENTRIES)
                } else {
                    panel.entries
                }
                if (panel.entries.size > MAX_VISIBLE_ENTRIES) {
                    Text(
                        text = "… ${panel.entries.size - MAX_VISIBLE_ENTRIES} earlier entries hidden",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                for (entry in displayEntries) {
                    Text(
                        text = entry,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionPromptCard(
    prompt: ChatLine.ActionPrompt,
    onAction: (ActionButton) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = prompt.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                for (action in prompt.actions) {
                    val isSelected = prompt.answered && prompt.selectedAction == action.label
                    if (isSelected) {
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                disabledContentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text(action.label)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onAction(action) },
                            enabled = !prompt.answered,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(action.label)
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_VISIBLE_ENTRIES = 4

private fun relativeTime(epochMs: Long): String {
    val diff = System.currentTimeMillis() - epochMs
    val min = diff / 60_000
    val hr = min / 60
    val day = hr / 24
    return when {
        min < 1 -> "Just now"
        min < 60 -> "${min}m ago"
        hr < 24 -> "${hr}h ago"
        day < 7 -> "${day}d ago"
        else -> {
            val fmt = java.text.SimpleDateFormat("MM/dd", java.util.Locale.getDefault())
            fmt.format(java.util.Date(epochMs))
        }
    }
}
