package com.mobilebot.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AgentExperienceScreen(
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: AgentExperienceViewModel = hiltViewModel(),
) {
    val frame by viewModel.frame.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var blueprintOpen by rememberSaveable { mutableStateOf(false) }
    var autoOpenedTaskIds by rememberSaveable { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(frame.activeTaskId, frame.taskLogs.size) {
        val taskId = frame.activeTaskId
        if (taskId != null && frame.taskLogs.isNotEmpty() && taskId !in autoOpenedTaskIds) {
            blueprintOpen = true
            autoOpenedTaskIds = autoOpenedTaskIds + taskId
        }
    }

    LaunchedEffect(frame.systemNotification?.id) {
        val notificationId = frame.systemNotification?.id ?: return@LaunchedEffect
        // 提醒层无人处理时只关闭浮层，不触发按钮动作。
        delay(SYSTEM_NOTIFICATION_AUTO_DISMISS_MS)
        viewModel.expireSystemNotification(notificationId)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            TaskSidebar(
                tasks = frame.taskCards,
                onSelectTask = { taskId ->
                    viewModel.selectTask(taskId)
                    scope.launch { drawerState.close() }
                },
                onToggleTaskPinned = viewModel::toggleTaskPinned,
            )
        },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(AgentBlack)
                .testTag("agent_surface"),
            color = AgentBlack,
        ) {
            PhoneFlowCanvas(
                frame = frame,
                blueprintOpen = blueprintOpen,
                onOpenBlueprint = { blueprintOpen = true },
                onCollapseBlueprint = { blueprintOpen = false },
                onOpenTaskSidebar = { scope.launch { drawerState.open() } },
                onOpenSettings = onOpenSettings,
                onOpenChat = onOpenChat,
                onAccelerateClock = viewModel::accelerateClockUntilNextEvent,
                onSelectTask = viewModel::selectTask,
                onStart = viewModel::startScenario,
                onAction = viewModel::chooseDecision,
                onSubmitText = viewModel::submitDecisionText,
                onDismissNotification = viewModel::dismissSystemNotification,
            )
        }
    }
}

@Composable
private fun PhoneFlowCanvas(
    frame: AgentExperienceFrame,
    blueprintOpen: Boolean,
    onOpenBlueprint: () -> Unit,
    onCollapseBlueprint: () -> Unit,
    onOpenTaskSidebar: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChat: () -> Unit,
    onAccelerateClock: () -> Unit,
    onSelectTask: (String) -> Unit,
    onStart: () -> Unit,
    onAction: (ActionButton) -> Unit,
    onSubmitText: (String) -> Unit,
    onDismissNotification: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AgentBlack)
            .imePadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!blueprintOpen) {
                TimeHeader(
                    frame = frame,
                    onAccelerateClock = onAccelerateClock,
                    onOpenTaskSidebar = onOpenTaskSidebar,
                    onOpenSettings = onOpenSettings,
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (frame.activeTaskId == null) {
                        WorkbenchArea(
                            frame = frame,
                            onSelectTask = onSelectTask,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 28.dp)
                                .padding(top = 14.dp),
                        )
                    } else {
                        SessionArea(
                            frame = frame,
                            blueprintOpen = blueprintOpen,
                            onCollapseBlueprint = onCollapseBlueprint,
                            onStart = onStart,
                            onAction = onAction,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 28.dp)
                                .padding(top = 14.dp),
                        )
                    }
                }
                if (blueprintOpen) {
                    BlueprintDeck(
                        frame = frame,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .zIndex(20f),
                    )
                }
                frame.activeCall?.let { activeCall ->
                    ActiveCallOverlay(
                        call = activeCall,
                        currentTimeText = frame.clockTimeText,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (frame.activeTaskId != null) {
                TaskProgressStrip(
                    frame = frame,
                    onOpenBlueprint = onOpenBlueprint,
                    modifier = Modifier
                        .padding(horizontal = 28.dp)
                        .padding(top = 4.dp, bottom = 2.dp),
                )
            }
            InteractionDock(
                active = frame.decisionPrompt != null && !frame.busy,
                onOpenChat = onOpenChat,
                onSubmitText = onSubmitText,
            )
        }

        frame.systemNotification?.let { notification ->
            SystemNotificationOverlay(
                notification = notification,
                onDismiss = onDismissNotification,
            )
        }
        if (blueprintOpen) {
            FloatingTaskMenuButton(
                onClick = onOpenTaskSidebar,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 20.dp, top = 24.dp)
                    .zIndex(100f),
            )
        }
    }
}

@Composable
private fun FloatingTaskMenuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        color = AgentPanel.copy(alpha = 0.9f),
        contentColor = AgentWhite,
        shape = CircleShape,
        border = BorderStroke(1.dp, AgentWhite.copy(alpha = 0.16f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Menu, contentDescription = "任务", tint = AgentWhite)
        }
    }
}

@Composable
private fun TimeHeader(
    frame: AgentExperienceFrame,
    onOpenTaskSidebar: () -> Unit,
    onOpenSettings: () -> Unit,
    onAccelerateClock: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(126.dp),
        color = AgentBlack,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 18.dp, top = 22.dp, end = 18.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenTaskSidebar) {
                Icon(Icons.Default.Menu, contentDescription = "任务", tint = AgentWhite)
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Column(
                    modifier = Modifier.clickable(onClick = onAccelerateClock),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = frame.clockTimeText,
                        color = AgentWhite,
                        fontSize = 32.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                    Text(
                        text = frame.clockDateText,
                        color = AgentMuted,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = AgentWhite)
            }
        }
    }
}

@Composable
private fun AiWorkFloatingButton(
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ai_work")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
        ),
        label = "ai_work_phase",
    )
    Surface(
        modifier = modifier
            .size(36.dp)
            .clickable(onClick = onClick)
            .drawWithContent {
                drawContent()
                val center = Offset(size.width / 2f, size.height / 2f)
                val orbitRadius = size.minDimension * 0.38f
                val baseColor = if (active) Color(0xFF68C8FF) else AgentMuted
                drawCircle(
                    color = baseColor.copy(alpha = if (active) 0.18f else 0.06f),
                    radius = size.minDimension * (0.42f + 0.08f * phase),
                    center = center,
                )
                drawCircle(
                    color = baseColor.copy(alpha = if (active) 0.36f else 0.14f),
                    radius = orbitRadius,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
                val angle = phase * 2f * PI.toFloat()
                val dot = Offset(
                    x = center.x + cos(angle) * orbitRadius,
                    y = center.y + sin(angle) * orbitRadius,
                )
                drawCircle(
                    color = baseColor.copy(alpha = if (active) 0.96f else 0.54f),
                    radius = 2.2.dp.toPx(),
                    center = dot,
                )
                drawCircle(
                    color = AgentWhite.copy(alpha = if (active) 0.58f else 0.24f),
                    radius = 1.dp.toPx(),
                    center = center,
                )
            },
        color = AgentPanel.copy(alpha = 0.92f),
        contentColor = AgentWhite,
        shape = CircleShape,
        border = BorderStroke(1.dp, Color(0xFF68C8FF).copy(alpha = if (active) 0.58f else 0.2f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "AI",
                color = AgentWhite,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BlueprintDeck(
    frame: AgentExperienceFrame,
    modifier: Modifier = Modifier,
) {
    val latestLogIndex = frame.taskLogs.lastIndex.coerceAtLeast(0)
    val latestLogKey = frame.taskLogs.lastOrNull()?.let { row ->
        "${row.id}:${row.timeText}:${row.text}"
    }
    val logListState = rememberLazyListState(initialFirstVisibleItemIndex = latestLogIndex)

    LaunchedEffect(latestLogKey) {
        val lastIndex = frame.taskLogs.lastIndex
        if (lastIndex >= 0) {
            logListState.animateScrollToItem(lastIndex)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(430.dp),
        color = BlueprintGray,
        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 26.dp, top = 24.dp, end = 26.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = frame.activeTaskTitle,
                        color = AgentWhite,
                        fontSize = 22.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                    Text(
                        text = "任务蓝图",
                        color = AgentWhite.copy(alpha = 0.72f),
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    BlueprintPartyAvatar("NT")
                    frame.participants.forEach { participant ->
                        BlueprintPartyAvatar(participant.label)
                    }
                }
            }
            if (frame.taskLogs.isEmpty()) {
                Text(
                    text = "等待任务推进。",
                    color = AgentWhite.copy(alpha = 0.62f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = logListState,
                    contentPadding = PaddingValues(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    items(frame.taskLogs, key = { it.id }) { row ->
                        TaskLogRow(row)
                    }
                }
            }
        }
    }
}

@Composable
private fun BlueprintPartyAvatar(label: String) {
    Surface(
        modifier = Modifier.size(42.dp),
        color = AgentPanel,
        contentColor = AgentWhite,
        shape = CircleShape,
        border = BorderStroke(1.dp, AgentWhite.copy(alpha = 0.24f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun WorkbenchArea(
    frame: AgentExperienceFrame,
    onSelectTask: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (frame.taskCards.isEmpty() && frame.recentSystemEvents.isEmpty()) {
            item {
                Text(
                    text = "等待系统事件。",
                    color = AgentMuted,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
        if (frame.taskCards.isNotEmpty()) {
            item {
                Text(
                    text = "任务",
                    color = AgentWhite,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            items(frame.taskCards, key = { it.id }) { task ->
                TaskCardRow(task = task, onClick = { onSelectTask(task.id) })
            }
        }
        if (frame.recentSystemEvents.isNotEmpty()) {
            item {
                Text(
                    text = "系统事件",
                    color = AgentWhite,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(frame.recentSystemEvents, key = { it.id }) { event ->
                SystemEventRow(event)
            }
        }
    }
}

@Composable
private fun TaskCardRow(
    task: AgentTaskCard,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (task.isActive) AgentPanelActive else AgentPanel,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = task.title,
                    color = AgentWhite,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = task.subtitle,
                    color = AgentMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = task.updatedTimeText,
                color = AgentMuted,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
private fun SystemEventRow(event: AgentSystemEvent) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AgentPanel,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = event.timeText,
                    color = AgentMuted,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                )
                Text(
                    text = event.source,
                    color = AgentWhite.copy(alpha = 0.78f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                )
            }
            Text(
                text = event.title,
                color = AgentWhite,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = event.body,
                color = AgentMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun SessionArea(
    frame: AgentExperienceFrame,
    blueprintOpen: Boolean,
    onCollapseBlueprint: () -> Unit,
    onStart: () -> Unit,
    onAction: (ActionButton) -> Unit,
    modifier: Modifier = Modifier,
) {
    val messages = frame.conversationItems
    val actions = remember(frame.decisionPrompt, frame.hasStarted, frame.finalSummary, frame.error) {
        conversationActions(frame)
    }
    val activeDecision = frame.decisionPrompt != null
    val listState = rememberLazyListState()
    val lastItemIndex = messages.lastIndex + if (actions.isNotEmpty()) 1 else 0
    val latestMessageKey = messages.lastOrNull()?.let { message ->
        "${message.id}:${message.role}:${message.text}"
    }.orEmpty()
    val actionsKey = actions.joinToString("|") { action -> "${action.label}:${action.value}" }

    LaunchedEffect(frame.activeTaskId, latestMessageKey, actionsKey, lastItemIndex, blueprintOpen) {
        if (lastItemIndex >= 0) {
            // 蓝图区展开会压缩会话区高度，需要重新锚定到最新消息。
            withFrameNanos { }
            listState.scrollToItem(lastItemIndex)
        }
    }

    val areaModifier =
        if (blueprintOpen) {
            modifier.clickable(onClick = onCollapseBlueprint)
        } else {
            modifier
        }

    Column(
        modifier = areaModifier
            .fillMaxWidth()
            .testTag("session_area"),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            state = listState,
            contentPadding = PaddingValues(top = 8.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(messages) { message ->
                ConversationBubble(message)
            }
            if (actions.isNotEmpty()) {
                item {
                    ConversationActionRow(
                        actions = actions,
                        activeActionValue = frame.activeActionValue,
                        enabled = !frame.busy,
                        onAction = { action ->
                            if (activeDecision) onAction(action) else onStart()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationActionRow(
    actions: List<ActionButton>,
    activeActionValue: String?,
    enabled: Boolean,
    onAction: (ActionButton) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("session_actions"),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(actions, key = { it.value }) { action ->
            ActionOptionBubble(
                label = action.label,
                selected = action.value == activeActionValue,
                enabled = enabled,
                onClick = { onAction(action) },
            )
        }
    }
}
@Composable
private fun ActionOptionBubble(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "action_loading")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1450, easing = LinearEasing),
        ),
        label = "action_loading_phase",
    )
    Surface(
        onClick = onClick,
        modifier = Modifier
            .height(67.dp)
            .widthIn(max = 180.dp)
            .loadingActionBorder(selected = selected, phase = phase),
        enabled = enabled,
        color = when {
            selected -> AgentPanelActive
            enabled -> AgentPanel
            else -> AgentPanel.copy(alpha = 0.58f)
        },
        contentColor = if (enabled || selected) AgentWhite else AgentMuted,
        shape = RoundedCornerShape(24.dp),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun Modifier.loadingActionBorder(
    selected: Boolean,
    phase: Float,
): Modifier =
    if (!selected) {
        this
    } else {
        drawWithContent {
            drawContent()
            val strokeWidth = 1.35.dp.toPx()
            val radius = 24.dp.toPx()
            val blue = Color(0xFF68C8FF)
            val paleBlue = Color(0xFFAADFFF)
            val borderInset = 1.8.dp.toPx()
            val borderRadius = (radius - borderInset).coerceAtLeast(0f)
            val borderSize = Size(
                width = (size.width - borderInset * 2f).coerceAtLeast(0f),
                height = (size.height - borderInset * 2f).coerceAtLeast(0f),
            )
            drawRoundRect(
                color = blue.copy(alpha = 0.08f),
                topLeft = Offset(borderInset, borderInset),
                size = borderSize,
                cornerRadius = CornerRadius(borderRadius, borderRadius),
                style = Stroke(width = 2.4.dp.toPx()),
            )
            drawRoundRect(
                color = paleBlue.copy(alpha = 0.48f),
                topLeft = Offset(borderInset, borderInset),
                size = borderSize,
                cornerRadius = CornerRadius(borderRadius, borderRadius),
                style = Stroke(width = strokeWidth),
            )
            drawOrbitingBorderLight(
                phase = phase,
                cornerRadius = radius,
                strokeWidth = strokeWidth,
                borderInset = borderInset,
                color = blue,
                highlight = AgentWhite,
            )
        }
    }

private fun DrawScope.drawOrbitingBorderLight(
    phase: Float,
    cornerRadius: Float,
    strokeWidth: Float,
    borderInset: Float,
    color: Color,
    highlight: Color,
) {
    val inset = borderInset + strokeWidth * 1.65f
    val perimeter = roundedRectPerimeter(
        width = size.width,
        height = size.height,
        radius = cornerRadius,
        inset = inset,
    )
    if (perimeter <= 1f) return

    val headDistance = perimeter * phase
    val trailLength = perimeter * 0.13f
    val samples = 34
    var previousPoint: Offset? = null
    repeat(samples) { index ->
        val progress = index / (samples - 1).toFloat()
        val distance = positiveModulo(headDistance - trailLength * (1f - progress), perimeter)
        val point = roundedRectPointAt(
            distance = distance,
            width = size.width,
            height = size.height,
            radius = cornerRadius,
            inset = inset,
        )
        previousPoint?.let { previous ->
            if (isNearby(previous, point)) {
                drawLine(
                    color = color.copy(alpha = 0.03f + progress * 0.08f),
                    start = previous,
                    end = point,
                    strokeWidth = strokeWidth * 2.1f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color.copy(alpha = 0.16f + progress * 0.42f),
                    start = previous,
                    end = point,
                    strokeWidth = strokeWidth * 0.9f,
                    cap = StrokeCap.Round,
                )
                if (progress > 0.66f) {
                    drawLine(
                        color = highlight.copy(alpha = (progress - 0.66f) * 0.85f),
                        start = previous,
                        end = point,
                        strokeWidth = strokeWidth * 0.32f,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        previousPoint = point
    }
    val head = roundedRectPointAt(
        distance = positiveModulo(headDistance, perimeter),
        width = size.width,
        height = size.height,
        radius = cornerRadius,
        inset = inset,
    )
    drawCircle(
        color = color.copy(alpha = 0.10f),
        radius = strokeWidth * 1.55f,
        center = head,
    )
    drawCircle(
        color = highlight.copy(alpha = 0.55f),
        radius = strokeWidth * 0.38f,
        center = head,
    )
}

private fun DrawScope.isNearby(start: Offset, end: Offset): Boolean {
    val dx = start.x - end.x
    val dy = start.y - end.y
    val maxStep = size.maxDimension * 0.18f
    return dx * dx + dy * dy < maxStep * maxStep
}

private fun roundedRectPerimeter(
    width: Float,
    height: Float,
    radius: Float,
    inset: Float,
): Float {
    val left = inset
    val top = inset
    val right = width - inset
    val bottom = height - inset
    val r = (radius - inset)
        .coerceAtMost((right - left) / 2f)
        .coerceAtMost((bottom - top) / 2f)
        .coerceAtLeast(0f)
    val horizontal = ((right - left) - 2f * r).coerceAtLeast(0f)
    val vertical = ((bottom - top) - 2f * r).coerceAtLeast(0f)
    return (2f * horizontal + 2f * vertical + 2f * PI.toFloat() * r).coerceAtLeast(0f)
}

private fun roundedRectPointAt(
    distance: Float,
    width: Float,
    height: Float,
    radius: Float,
    inset: Float,
): Offset {
    val left = inset
    val top = inset
    val right = width - inset
    val bottom = height - inset
    val r = (radius - inset)
        .coerceAtMost((right - left) / 2f)
        .coerceAtMost((bottom - top) / 2f)
        .coerceAtLeast(0f)
    val horizontal = ((right - left) - 2f * r).coerceAtLeast(0f)
    val vertical = ((bottom - top) - 2f * r).coerceAtLeast(0f)
    val arc = (PI.toFloat() * r / 2f).coerceAtLeast(0.0001f)
    val perimeter = 2f * horizontal + 2f * vertical + 4f * arc
    var remaining = positiveModulo(distance, perimeter)

    if (remaining <= horizontal) return Offset(left + r + remaining, top)
    remaining -= horizontal
    if (remaining <= arc) return roundedCornerPoint(right - r, top + r, r, -PI.toFloat() / 2f, remaining / arc)
    remaining -= arc

    if (remaining <= vertical) return Offset(right, top + r + remaining)
    remaining -= vertical
    if (remaining <= arc) return roundedCornerPoint(right - r, bottom - r, r, 0f, remaining / arc)
    remaining -= arc

    if (remaining <= horizontal) return Offset(right - r - remaining, bottom)
    remaining -= horizontal
    if (remaining <= arc) return roundedCornerPoint(left + r, bottom - r, r, PI.toFloat() / 2f, remaining / arc)
    remaining -= arc

    if (remaining <= vertical) return Offset(left, bottom - r - remaining)
    remaining -= vertical
    return roundedCornerPoint(left + r, top + r, r, PI.toFloat(), remaining / arc)
}

private fun roundedCornerPoint(
    centerX: Float,
    centerY: Float,
    radius: Float,
    startAngle: Float,
    progress: Float,
): Offset {
    val angle = startAngle + progress.coerceIn(0f, 1f) * PI.toFloat() / 2f
    return Offset(
        x = centerX + cos(angle) * radius,
        y = centerY + sin(angle) * radius,
    )
}

private fun positiveModulo(value: Float, modulus: Float): Float =
    ((value % modulus) + modulus) % modulus
@Composable
private fun TaskProgressStrip(
    frame: AgentExperienceFrame,
    onOpenBlueprint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = frame.progressLine
    val label = progressLabelText(progress.label)
    val detail = progressDetailText(progress.detail)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("task_progress"),
        color = AgentPanel,
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = AgentWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            Text(
                text = detail,
                modifier = Modifier.weight(1f),
                color = AgentMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AiWorkFloatingButton(
                active = frame.busy || frame.taskCards.isNotEmpty() || frame.recentSystemEvents.isNotEmpty(),
                onClick = onOpenBlueprint,
            )
        }
    }
}

private fun progressLabelText(value: String): String =
    when (value.trim()) {
        "Ready" -> "就绪"
        "Starting" -> "启动中"
        "Understanding" -> "处理中"
        "Resuming" -> "继续中"
        "Running" -> "进行中"
        "Continuing" -> "继续中"
        "Planning" -> "规划中"
        "Executing", "EXECUTING_TOOL" -> "执行中"
        "THINKING" -> "思考中"
        "OBSERVING" -> "更新中"
        "RESPONDING" -> "回复中"
        "Waiting" -> "等待"
        "Updated" -> "已更新"
        "Complete" -> "已完成"
        "Error", "Failed", "Needs attention" -> "需处理"
        "Needs API key" -> "需配置"
        else -> value
    }

private fun progressDetailText(value: String): String {
    if (value.any { it in '\u4e00'..'\u9fff' }) return value
    return when (value.trim()) {
        "Ready" -> "就绪"
        "Waiting for confirmation" -> "等待触发"
        "Preparing the coordination flow" -> "正在准备协调流程"
        "User decision required" -> "等待用户决策"
        "Advancing to the next missing task milestone." -> "正在推进下一个任务节点"
        "Waiting for matching SMS" -> "等待匹配短信"
        "Loading coordination rules" -> "正在载入任务规则"
        "Preparing the task plan" -> "正在生成任务计划"
        "Using phone capability" -> "正在调用系统能力"
        "Resolving contacts" -> "正在查找联系人"
        "Sending SMS and starting listener" -> "正在发送短信并监听回复"
        "Reading saved preferences" -> "正在读取偏好"
        "Continuing the workflow" -> "正在继续任务"
        "Continuing workflow" -> "正在继续任务"
        "User profile loaded." -> "已读取用户资料"
        "Service preferences loaded." -> "已读取服务偏好"
        "Home and frequent places loaded." -> "已读取常用地点"
        "Trusted service relationships loaded." -> "已读取服务联系人"
        "Relevant contacts checked." -> "已确认相关联系人"
        "Message sent successfully." -> "消息已发送"
        "Incoming service update received." -> "已收到服务更新"
        "Location context resolved." -> "已确认位置信息"
        "Service status retrieved." -> "已读取服务信息"
        "Service request recorded." -> "服务请求已记录"
        "Quiet progress update prepared." -> "提醒已准备"
        "Phone call connected." -> "电话已接通"
        "Call history checked." -> "通话记录已检查"
        "Device state checked." -> "设备状态已检查"
        "Payment completed." -> "支付已完成"
        "Expense recorded." -> "账务已记录"
        "The provider response could not be completed." -> "服务响应未完成"
        "The run stopped after too many internal action rounds." -> "内部动作轮次过多，任务已暂停"
        "The workflow needs manual review before continuing." -> "继续前需要人工检查"
        "Configure the provider key in Settings before running." -> "请先在设置中配置 API Key"
        else -> value
    }
}

@Composable
private fun InteractionDock(
    active: Boolean,
    onOpenChat: () -> Unit,
    onSubmitText: (String) -> Unit,
) {
    var composing by rememberSaveable(active) { mutableStateOf(false) }
    var input by rememberSaveable(active) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(active, composing) {
        if (active && composing) {
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBlack)
            .padding(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 28.dp)
            .testTag("interaction_area"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (active && composing) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            val value = input.trim()
                            if (value.isNotEmpty()) {
                                onSubmitText(value)
                                input = ""
                                composing = false
                            }
                        },
                    ),
                    placeholder = { Text("Type") },
                )
                Button(
                    onClick = {
                        val value = input.trim()
                        if (value.isNotEmpty()) {
                            onSubmitText(value)
                            input = ""
                            composing = false
                        }
                    },
                    enabled = input.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AgentWhite,
                        contentColor = AgentBlack,
                        disabledContainerColor = AgentPanel,
                        disabledContentColor = AgentMuted,
                    ),
                ) {
                    Text("Send")
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleToolButton(enabled = active, onClick = onOpenChat) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add",
                        tint = if (active) AgentWhite else AgentMuted,
                    )
                }
                Surface(
                    modifier = Modifier
                        .height(48.dp)
                        .weight(1f)
                        .clickable(enabled = active) { composing = true },
                    color = if (active) AgentPanel else AgentPanel.copy(alpha = 0.42f),
                    shape = RoundedCornerShape(24.dp),
                    border = if (active) BorderStroke(1.dp, AgentWhite.copy(alpha = 0.64f)) else null,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Type",
                            color = if (active) AgentWhite else AgentMuted,
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
                PillToolButton("Talk", enabled = active, onClick = onOpenChat)
                PillToolButton("Live", enabled = active, onClick = onOpenChat)
            }
        }
    }
}

@Composable
private fun ConversationBubble(message: AgentConversationItem) {
    val isUser = message.role == AgentConversationRole.USER
    val bubbleModifier = if (isUser) {
        Modifier
            .widthIn(max = 280.dp)
            .heightIn(min = 67.dp)
    } else {
        Modifier.fillMaxWidth(0.86f)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = bubbleModifier,
            color = if (isUser) AgentPanel else Color.Transparent,
            contentColor = AgentWhite,
            shape = RoundedCornerShape(24.dp),
            border = if (isUser) null else BorderStroke(1.dp, AgentWhite),
        ) {
            if (isUser) {
                Box(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 17.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = message.text,
                        color = AgentWhite,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            } else {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 17.dp),
                    color = AgentWhite,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun TaskLogRow(row: AgentTaskLog) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        row.timeText?.let { time ->
            Text(
                text = time,
                modifier = Modifier.width(72.dp),
                color = AgentWhite.copy(alpha = 0.66f),
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
        }
        Text(
            text = row.text,
            modifier = Modifier.weight(1f),
            color = AgentWhite,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SystemNotificationOverlay(
    notification: AgentSystemNotification,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AgentBlack.copy(alpha = 0.84f))
            .testTag("system_notification"),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 34.dp)
                .heightIn(min = 292.dp),
            color = Color.Transparent,
            contentColor = AgentWhite,
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, AgentWhite.copy(alpha = 0.56f)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    color = Color.Transparent,
                    contentColor = AgentWhite,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, AgentWhite.copy(alpha = 0.62f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "!",
                            fontSize = 22.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
                Text(
                    text = notification.title,
                    color = AgentWhite,
                    fontSize = 21.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = notification.timeText,
                    color = AgentWhite.copy(alpha = 0.68f),
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = notification.body,
                    color = AgentWhite.copy(alpha = 0.84f),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable(onClick = onDismiss),
                    color = AgentWhite,
                    contentColor = AgentBlack,
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = notification.actionLabel,
                            fontSize = 15.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveCallOverlay(
    call: AgentActiveCall,
    currentTimeText: String,
    modifier: Modifier = Modifier,
) {
    val pulse by rememberInfiniteTransition(label = "call_pulse").animateFloat(
        initialValue = 0.42f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
        ),
        label = "call_pulse_alpha",
    )
    Box(
        modifier = modifier
            .background(AgentBlack.copy(alpha = 0.93f))
            .padding(horizontal = 28.dp)
            .testTag("active_call"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Surface(
                modifier = Modifier.size(96.dp),
                color = AgentPanelActive,
                contentColor = AgentWhite,
                shape = CircleShape,
                border = BorderStroke(1.dp, Color(0xFF68C8FF).copy(alpha = pulse)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = call.caller.take(1),
                        color = AgentWhite,
                        fontSize = 36.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            Text(
                text = call.caller,
                color = AgentWhite,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Text(
                text = call.statusText,
                color = AgentWhite.copy(alpha = 0.74f),
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "${call.startedTimeText} 接通 · 当前 $currentTimeText",
                color = AgentMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
            )
            Surface(
                color = AgentPanel,
                contentColor = AgentWhite,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, AgentWhite.copy(alpha = 0.16f)),
            ) {
                Text(
                    text = call.transcriptText,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                    color = AgentWhite.copy(alpha = 0.88f),
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CallControlChip("静音")
                CallControlChip("转写")
                CallControlChip("免提")
            }
        }
    }
}

@Composable
private fun CallControlChip(text: String) {
    Surface(
        modifier = Modifier
            .width(72.dp)
            .height(44.dp),
        color = AgentPanel,
        contentColor = AgentWhite,
        shape = RoundedCornerShape(22.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = AgentWhite.copy(alpha = 0.78f),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CircleToolButton(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(48.dp)
            .clickable(enabled = enabled, onClick = onClick),
        color = if (enabled) AgentPanel else AgentPanel.copy(alpha = 0.42f),
        shape = CircleShape,
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun PillToolButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(64.dp)
            .height(48.dp)
            .clickable(enabled = enabled, onClick = onClick),
        color = if (enabled) AgentPanel else AgentPanel.copy(alpha = 0.42f),
        shape = RoundedCornerShape(24.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (enabled) AgentWhite else AgentMuted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun TaskSidebar(
    tasks: List<AgentTaskCard>,
    onSelectTask: (String) -> Unit,
    onToggleTaskPinned: (String) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.46f),
        color = AgentPanel,
        contentColor = AgentWhite,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "任务",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                color = AgentWhite,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            HorizontalDivider(color = AgentWhite.copy(alpha = 0.18f))
            if (tasks.isEmpty()) {
                Text(
                    text = "暂无任务",
                    modifier = Modifier.padding(20.dp),
                    color = AgentMuted,
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(tasks, key = { it.id }) { task ->
                        SidebarTaskCard(
                            task = task,
                            onSelectTask = { onSelectTask(task.id) },
                            onTogglePinned = { onToggleTaskPinned(task.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarTaskCard(
    task: AgentTaskCard,
    onSelectTask: () -> Unit,
    onTogglePinned: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(task.id, task.isPinned) {
                detectTapGestures(
                    onTap = { onSelectTask() },
                    onLongPress = { onTogglePinned() },
                )
            },
        color = if (task.isActive) AgentPanelActive else AgentBlack.copy(alpha = 0.46f),
        contentColor = AgentWhite,
        shape = RoundedCornerShape(8.dp),
        border = if (task.isActive) BorderStroke(1.dp, AgentWhite.copy(alpha = 0.36f)) else null,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = task.title,
                    color = AgentWhite,
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (task.isPinned) {
                    PinIndicator(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(16.dp),
                    )
                }
            }
            Text(
                text = task.subtitle,
                color = AgentMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = task.updatedTimeText,
                color = AgentWhite.copy(alpha = 0.56f),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PinIndicator(
    modifier: Modifier = Modifier,
) {
    val color = Color(0xFF68C8FF)
    Canvas(modifier = modifier) {
        val strokeWidth = 1.6.dp.toPx()
        val headRadius = 2.6.dp.toPx()
        val head = Offset(size.width * 0.36f, size.height * 0.26f)
        val shoulder = Offset(size.width * 0.68f, size.height * 0.56f)
        val point = Offset(size.width * 0.30f, size.height * 0.86f)
        drawCircle(color = color, radius = headRadius, center = head)
        drawLine(
            color = color,
            start = Offset(size.width * 0.46f, size.height * 0.34f),
            end = shoulder,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.50f, size.height * 0.52f),
            end = Offset(size.width * 0.26f, size.height * 0.66f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.54f, size.height * 0.60f),
            end = point,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

private fun conversationActions(frame: AgentExperienceFrame): List<ActionButton> =
    when {
        frame.decisionPrompt != null -> frame.decisionPrompt.actions
        frame.error != null -> listOf(ActionButton("重试", "Retry"))
        else -> emptyList()
    }

private val AgentBlack = Color(0xFF050505)
private val AgentPanel = Color(0xFF1A1A1A)
private val AgentPanelActive = Color(0xFF2A2A2A)
private val BlueprintGray = Color(0xFF545454)
private val AgentMuted = Color(0xFFA8A8A8)
private val AgentWhite = Color.White
private const val SYSTEM_NOTIFICATION_AUTO_DISMISS_MS = 20_000L
