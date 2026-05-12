package com.mobilebot.scenarios.petgrooming

enum class PetGroomingSurfaceStatus {
    RUNNING,
    DONE,
    BLOCKED,
}

enum class PetGroomingSurfaceRole {
    AGENT,
    USER,
}

data class PetGroomingSurfaceConversation(
    val role: PetGroomingSurfaceRole,
    val text: String,
)

data class PetGroomingSurfaceLog(
    val text: String,
)

data class PetGroomingSurfaceParticipant(
    val id: String,
    val label: String,
    val displayName: String,
    val role: String,
)

data class PetGroomingSurfaceProgress(
    val label: String,
    val detail: String,
    val completed: Int,
    val total: Int,
)

data class PetGroomingSurfaceDecision(
    val text: String,
    val actions: List<PetGroomingScriptAction>,
)

data class PetGroomingScriptAction(
    val label: String,
    val key: String,
)

data class PetGroomingSurfaceTimeline(
    val title: String,
    val detail: String,
    val status: PetGroomingSurfaceStatus,
)

data class PetGroomingTaskSeed(
    val title: String,
    val subtitle: String,
    val status: PetGroomingSurfaceStatus,
    val conversations: List<PetGroomingSurfaceConversation>,
    val logs: List<PetGroomingSurfaceLog>,
    val participants: List<PetGroomingSurfaceParticipant>,
    val progress: PetGroomingSurfaceProgress,
    val decision: PetGroomingSurfaceDecision?,
    val timeline: List<PetGroomingSurfaceTimeline>,
)

data class PetGroomingTaskUpdate(
    val subtitle: String,
    val status: PetGroomingSurfaceStatus,
    val conversations: List<PetGroomingSurfaceConversation> = emptyList(),
    val logs: List<PetGroomingSurfaceLog> = emptyList(),
    val participants: List<PetGroomingSurfaceParticipant>? = null,
    val progress: PetGroomingSurfaceProgress,
    val decision: PetGroomingSurfaceDecision? = null,
    val activeActionValue: String? = null,
    val timeline: List<PetGroomingSurfaceTimeline> = emptyList(),
    val finalSummary: String? = null,
)

object PetGroomingTaskSurface {
    const val TASK_ID = "pet-grooming-live"
    const val ACTION_ACCEPT_14 = "pet.accept_14"
    const val ACTION_KEEP_17 = "pet.keep_17"

    fun openSlotSeed(messageBody: String): PetGroomingTaskSeed =
        PetGroomingTaskSeed(
            title = "麒麟洗护",
            subtitle = "PetSmart 14:00 空档待确认",
            status = PetGroomingSurfaceStatus.BLOCKED,
            conversations = listOf(
                PetGroomingSurfaceConversation(
                    role = PetGroomingSurfaceRole.AGENT,
                    text = "PetSmart 来信息说 14:00 空出来了，可以安排 Kylin 洗澡和去浮毛。要把原来 17:00 只洗澡改到 14:00 吗？",
                ),
            ),
            logs = listOf(
                PetGroomingSurfaceLog("收到 PetSmart 的短信：$messageBody"),
            ),
            participants = listOf(PETSMART),
            progress = PetGroomingSurfaceProgress(
                label = "等待",
                detail = "等待用户决策",
                completed = 0,
                total = 7,
            ),
            decision = openSlotDecision(),
            timeline = listOf(
                PetGroomingSurfaceTimeline(
                    title = "PetSmart 来信",
                    detail = messageBody,
                    status = PetGroomingSurfaceStatus.BLOCKED,
                ),
            ),
        )

    fun openSlotDecision(): PetGroomingSurfaceDecision =
        PetGroomingSurfaceDecision(
            text = "要把 Kylin 改到 14:00 洗澡和去浮毛吗？",
            actions = listOf(
                PetGroomingScriptAction("可以", ACTION_ACCEPT_14),
                PetGroomingScriptAction("不改了", ACTION_KEEP_17),
            ),
        )

    fun openSlotClarification(userText: String): Pair<List<PetGroomingSurfaceConversation>, PetGroomingSurfaceDecision> =
        listOf(
            PetGroomingSurfaceConversation(PetGroomingSurfaceRole.USER, userText),
            PetGroomingSurfaceConversation(PetGroomingSurfaceRole.AGENT, "你是想改到 14:00，还是保留原来的 17:00？"),
        ) to openSlotDecision().copy(text = "你是想改到 14:00，还是保留原来的 17:00？")

    fun acceptOpenSlot(userLabel: String): PetGroomingTaskUpdate =
        PetGroomingTaskUpdate(
            status = PetGroomingSurfaceStatus.RUNNING,
            subtitle = "已改约 14:00，等待司机确认",
            conversations = listOf(
                PetGroomingSurfaceConversation(PetGroomingSurfaceRole.USER, userLabel),
                PetGroomingSurfaceConversation(
                    PetGroomingSurfaceRole.AGENT,
                    "我已经回复 PetSmart 同意改到 14:00，并联系了司机老陈，正在等他确认。",
                ),
            ),
            logs = listOf(
                PetGroomingSurfaceLog("发送短信给 PetSmart：好的，14:00 准时到。"),
                PetGroomingSurfaceLog("添加 Driver 到参与方。"),
                PetGroomingSurfaceLog("发送短信给 Driver：老陈，麻烦 13:20 来楼下接 Kylin，14:00 前送到 PetSmart 洗澡和去浮毛。"),
            ),
            participants = listOf(PETSMART, DRIVER),
            progress = PetGroomingSurfaceProgress(
                label = "进行中",
                detail = "等待司机确认",
                completed = 2,
                total = 7,
            ),
            timeline = listOf(
                PetGroomingSurfaceTimeline(
                    title = "已改约",
                    detail = "PetSmart 和 Driver 已通知。",
                    status = PetGroomingSurfaceStatus.RUNNING,
                ),
            ),
        )

    fun keepOriginalSlot(userLabel: String): PetGroomingTaskUpdate =
        PetGroomingTaskUpdate(
            status = PetGroomingSurfaceStatus.DONE,
            subtitle = "保留 17:00 只洗澡",
            conversations = listOf(
                PetGroomingSurfaceConversation(PetGroomingSurfaceRole.USER, userLabel),
                PetGroomingSurfaceConversation(PetGroomingSurfaceRole.AGENT, "好的，保留原来的 17:00 只洗澡安排。"),
            ),
            logs = listOf(
                PetGroomingSurfaceLog("保留 PetSmart 17:00 只洗澡安排。"),
            ),
            progress = PetGroomingSurfaceProgress(
                label = "完成",
                detail = "已保留原安排",
                completed = 1,
                total = 1,
            ),
            finalSummary = "已保留原来的 17:00 只洗澡安排。",
        )

    fun driverPickupConfirmation(messageBody: String): PetGroomingTaskUpdate =
        PetGroomingTaskUpdate(
            status = PetGroomingSurfaceStatus.RUNNING,
            subtitle = "司机 13:20 到楼下",
            conversations = listOf(
                PetGroomingSurfaceConversation(
                    PetGroomingSurfaceRole.AGENT,
                    "司机老陈已经回复 OK，我给你定了 13:20 送 Kylin 下楼的提醒。",
                ),
            ),
            logs = listOf(
                PetGroomingSurfaceLog("收到 Driver 的短信：$messageBody"),
                PetGroomingSurfaceLog("创建提醒：送 Kylin 下楼（13:20）。"),
            ),
            progress = PetGroomingSurfaceProgress(
                label = "进行中",
                detail = "等待 13:20 提醒",
                completed = 3,
                total = 7,
            ),
        )

    fun driverPickedUpKylin(messageBody: String): PetGroomingTaskUpdate =
        PetGroomingTaskUpdate(
            status = PetGroomingSurfaceStatus.RUNNING,
            subtitle = "Kylin 已上车",
            conversations = listOf(
                PetGroomingSurfaceConversation(
                    PetGroomingSurfaceRole.AGENT,
                    "老陈已经接到 Kylin，正在去 PetSmart 的路上。",
                ),
            ),
            logs = listOf(
                PetGroomingSurfaceLog("收到 Driver 的短信：$messageBody"),
                PetGroomingSurfaceLog("更新状态：Kylin 已上车，前往 PetSmart。"),
            ),
            progress = PetGroomingSurfaceProgress(
                label = "进行中",
                detail = "前往 PetSmart",
                completed = 5,
                total = 7,
            ),
        )

    fun driverArrivedPetSmart(messageBody: String): PetGroomingTaskUpdate =
        PetGroomingTaskUpdate(
            status = PetGroomingSurfaceStatus.RUNNING,
            subtitle = "Kylin 已到 PetSmart",
            conversations = listOf(
                PetGroomingSurfaceConversation(
                    PetGroomingSurfaceRole.AGENT,
                    "老陈已经把 Kylin 送到 PetSmart，店员已接走。",
                ),
            ),
            logs = listOf(
                PetGroomingSurfaceLog("收到 Driver 的短信：$messageBody"),
                PetGroomingSurfaceLog("更新状态：Kylin 已到店，等待 PetSmart 服务进度。"),
            ),
            progress = PetGroomingSurfaceProgress(
                label = "进行中",
                detail = "等待 PetSmart 进度",
                completed = 6,
                total = 7,
            ),
        )

    fun serviceStarted(messageBody: String): PetGroomingTaskUpdate =
        PetGroomingTaskUpdate(
            status = PetGroomingSurfaceStatus.RUNNING,
            subtitle = "洗澡和去浮毛进行中",
            conversations = listOf(
                PetGroomingSurfaceConversation(
                    PetGroomingSurfaceRole.AGENT,
                    "PetSmart 确认 Kylin 已开始洗澡和去浮毛，预计 14:45 完成。",
                ),
            ),
            logs = listOf(
                PetGroomingSurfaceLog("收到 PetSmart 的短信：$messageBody"),
                PetGroomingSurfaceLog("更新状态：服务已开始，继续等待完成通知。"),
            ),
            progress = PetGroomingSurfaceProgress(
                label = "进行中",
                detail = "等待完成通知",
                completed = 6,
                total = 7,
            ),
        )

    fun serviceProgress(messageBody: String): PetGroomingTaskUpdate =
        PetGroomingTaskUpdate(
            status = PetGroomingSurfaceStatus.RUNNING,
            subtitle = "完成时间调整到 15:00",
            conversations = listOf(
                PetGroomingSurfaceConversation(
                    PetGroomingSurfaceRole.AGENT,
                    "PetSmart 更新了进度：Kylin 去浮毛会多 15 分钟，预计 15:00 左右完成。我会继续等完成通知。",
                ),
            ),
            logs = listOf(
                PetGroomingSurfaceLog("收到 PetSmart 的短信：$messageBody"),
                PetGroomingSurfaceLog("更新预计完成时间：15:00 左右。"),
            ),
            progress = PetGroomingSurfaceProgress(
                label = "进行中",
                detail = "继续监听完成通知",
                completed = 6,
                total = 7,
            ),
        )

    fun departureReminderFired(): PetGroomingTaskUpdate =
        PetGroomingTaskUpdate(
            status = PetGroomingSurfaceStatus.RUNNING,
            subtitle = "提醒已触发",
            logs = listOf(
                PetGroomingSurfaceLog("触发提醒：送 Kylin 下楼。"),
            ),
            progress = PetGroomingSurfaceProgress(
                label = "进行中",
                detail = "等待司机接到 Kylin",
                completed = 4,
                total = 7,
            ),
        )

    private val PETSMART = PetGroomingSurfaceParticipant(
        id = "petsmart",
        label = "PS",
        displayName = "PetSmart",
        role = "grooming_shop",
    )

    private val DRIVER = PetGroomingSurfaceParticipant(
        id = "driver",
        label = "陈",
        displayName = "老陈",
        role = "driver",
    )
}
