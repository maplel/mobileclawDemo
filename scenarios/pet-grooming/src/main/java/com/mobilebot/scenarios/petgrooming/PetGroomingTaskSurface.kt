package com.mobilebot.scenarios.petgrooming

import com.mobilebot.scenarios.runtime.ScenarioAction
import com.mobilebot.scenarios.runtime.ScenarioConversation
import com.mobilebot.scenarios.runtime.ScenarioDecision
import com.mobilebot.scenarios.runtime.ScenarioLog
import com.mobilebot.scenarios.runtime.ScenarioParticipant
import com.mobilebot.scenarios.runtime.ScenarioProgress
import com.mobilebot.scenarios.runtime.ScenarioSurfaceRole
import com.mobilebot.scenarios.runtime.ScenarioSurfaceStatus
import com.mobilebot.scenarios.runtime.ScenarioTaskSeed
import com.mobilebot.scenarios.runtime.ScenarioTaskUpdate
import com.mobilebot.scenarios.runtime.ScenarioTimeline

object PetGroomingTaskSurface {
    const val TASK_ID = "pet-grooming-live"
    const val ACTION_ACCEPT_14 = "pet.accept_14"
    const val ACTION_KEEP_17 = "pet.keep_17"
    const val PURPOSE_EXPEDITE_SERVICE = "pet_grooming.expedite_service"

    fun openSlotSeed(messageBody: String): ScenarioTaskSeed =
        ScenarioTaskSeed(
            taskId = TASK_ID,
            title = "麒麟洗护",
            subtitle = "PetSmart 14:00 空档待确认",
            status = ScenarioSurfaceStatus.BLOCKED,
            conversations = listOf(
                ScenarioConversation(
                    role = ScenarioSurfaceRole.AGENT,
                    text = "PetSmart 来信息说 14:00 空出来了，可以安排 Kylin 洗澡和去浮毛。要把原来 17:00 只洗澡改到 14:00 吗？",
                ),
            ),
            logs = listOf(
                ScenarioLog("收到 PetSmart 的短信：$messageBody"),
            ),
            participants = listOf(PETSMART),
            progress = ScenarioProgress(
                label = "等待",
                detail = "等待用户决策",
                completed = 0,
                total = 7,
            ),
            decision = openSlotDecision(),
            timeline = listOf(
                ScenarioTimeline(
                    title = "PetSmart 来信",
                    detail = messageBody,
                    status = ScenarioSurfaceStatus.BLOCKED,
                ),
            ),
        )

    fun openSlotDecision(): ScenarioDecision =
        ScenarioDecision(
            text = "要把 Kylin 改到 14:00 洗澡和去浮毛吗？",
            actions = listOf(
                ScenarioAction("可以", ACTION_ACCEPT_14),
                ScenarioAction("不改了", ACTION_KEEP_17),
            ),
        )

    fun openSlotClarification(userText: String): Pair<List<ScenarioConversation>, ScenarioDecision> =
        listOf(
            ScenarioConversation(ScenarioSurfaceRole.USER, userText),
            ScenarioConversation(ScenarioSurfaceRole.AGENT, "你是想改到 14:00，还是保留原来的 17:00？"),
        ) to openSlotDecision().copy(text = "你是想改到 14:00，还是保留原来的 17:00？")

    fun acceptOpenSlot(userLabel: String): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            status = ScenarioSurfaceStatus.RUNNING,
            subtitle = "已改约 14:00，等待司机确认",
            conversations = listOf(
                ScenarioConversation(ScenarioSurfaceRole.USER, userLabel),
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "我已经回复 PetSmart 同意改到 14:00，并联系了司机老陈，正在等他确认。",
                ),
            ),
            logs = listOf(
                ScenarioLog("发送短信给 PetSmart：好的，14:00 准时到。"),
                ScenarioLog("添加 Driver 到参与方。"),
                ScenarioLog("发送短信给 Driver：老陈，麻烦 13:20 来楼下接 Kylin，14:00 前送到 PetSmart 洗澡和去浮毛。"),
            ),
            participants = listOf(PETSMART, DRIVER),
            progress = ScenarioProgress(
                label = "进行中",
                detail = "等待司机确认",
                completed = 2,
                total = 7,
            ),
            timeline = listOf(
                ScenarioTimeline(
                    title = "已改约",
                    detail = "PetSmart 和 Driver 已通知。",
                    status = ScenarioSurfaceStatus.RUNNING,
                ),
            ),
        )

    fun keepOriginalSlot(userLabel: String): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            status = ScenarioSurfaceStatus.DONE,
            subtitle = "保留 17:00 只洗澡",
            conversations = listOf(
                ScenarioConversation(ScenarioSurfaceRole.USER, userLabel),
                ScenarioConversation(ScenarioSurfaceRole.AGENT, "好的，保留原来的 17:00 只洗澡安排。"),
            ),
            logs = listOf(
                ScenarioLog("保留 PetSmart 17:00 只洗澡安排。"),
            ),
            progress = ScenarioProgress(
                label = "完成",
                detail = "已保留原安排",
                completed = 1,
                total = 1,
            ),
            finalSummary = "已保留原来的 17:00 只洗澡安排。",
        )

    fun driverPickupConfirmation(messageBody: String): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            status = ScenarioSurfaceStatus.RUNNING,
            subtitle = "司机 13:20 到楼下",
            conversations = listOf(
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "司机老陈已经回复 OK，我给你定了 13:20 送 Kylin 下楼的提醒。",
                ),
            ),
            logs = listOf(
                ScenarioLog("收到 Driver 的短信：$messageBody"),
                ScenarioLog("创建提醒：送 Kylin 下楼（13:20）。"),
            ),
            progress = ScenarioProgress(
                label = "进行中",
                detail = "等待 13:20 提醒",
                completed = 3,
                total = 7,
            ),
        )

    fun driverPickedUpKylin(messageBody: String): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            status = ScenarioSurfaceStatus.RUNNING,
            subtitle = "Kylin 已上车",
            conversations = listOf(
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "老陈已经接到 Kylin，正在去 PetSmart 的路上。",
                ),
            ),
            logs = listOf(
                ScenarioLog("收到 Driver 的短信：$messageBody"),
                ScenarioLog("更新状态：Kylin 已上车，前往 PetSmart。"),
            ),
            progress = ScenarioProgress(
                label = "进行中",
                detail = "前往 PetSmart",
                completed = 5,
                total = 7,
            ),
        )

    fun propertyParkingNotice(messageBody: String): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            status = ScenarioSurfaceStatus.RUNNING,
            subtitle = "司机改走东门接送",
            logs = listOf(
                ScenarioLog("收到物业通知：$messageBody"),
                ScenarioLog("更新司机路线：优先走东门，避开 B2 西侧临停区。"),
            ),
            participantsToAdd = listOf(PROPERTY),
            progress = ScenarioProgress(
                label = "进行中",
                detail = "接送路线已更新",
                completed = 5,
                total = 7,
            ),
        )

    fun driverArrivedPetSmart(messageBody: String): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            status = ScenarioSurfaceStatus.RUNNING,
            subtitle = "Kylin 已到 PetSmart",
            conversations = listOf(
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "老陈已经把 Kylin 送到 PetSmart，店员已接走。",
                ),
            ),
            logs = listOf(
                ScenarioLog("收到 Driver 的短信：$messageBody"),
                ScenarioLog("更新状态：Kylin 已到店，等待 PetSmart 服务进度。"),
            ),
            progress = ScenarioProgress(
                label = "进行中",
                detail = "等待 PetSmart 进度",
                completed = 6,
                total = 7,
            ),
        )

    fun expediteRequested(userText: String): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            status = ScenarioSurfaceStatus.RUNNING,
            subtitle = "已请 PetSmart 尽量加快",
            conversations = listOf(
                ScenarioConversation(ScenarioSurfaceRole.USER, userText),
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "我已发短信请 PetSmart 在不影响安全和效果的前提下尽量加快，后续以门店确认的进度为准。",
                ),
            ),
            logs = listOf(
                ScenarioLog("发送短信给 PetSmart：麻烦在不影响 Kylin 安全和洗护效果的前提下尽量加快，谢谢。"),
                ScenarioLog("结构化意图：$PURPOSE_EXPEDITE_SERVICE。"),
            ),
            participantsToAdd = listOf(PETSMART),
            progress = ScenarioProgress(
                label = "进行中",
                detail = "已催促门店加快",
                completed = 6,
                total = 7,
            ),
        )

    fun serviceStarted(
        messageBody: String,
        expediteRequested: Boolean = false,
    ): ScenarioTaskUpdate {
        val effectiveMessage = if (expediteRequested) {
            "Kylin 已到店，会按 14:00 开始洗澡和去浮毛；已备注尽量加快，预计 16:00 左右完成，后续以进度更新为准。"
        } else {
            "Kylin 已到店，会按 14:00 开始洗澡和去浮毛，预计 16:00 左右完成。"
        }
        return ScenarioTaskUpdate(
            taskId = TASK_ID,
            status = ScenarioSurfaceStatus.RUNNING,
            subtitle = "洗澡和去浮毛进行中",
            conversations = listOf(
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "PetSmart 确认 $effectiveMessage",
                ),
            ),
            logs = listOf(
                ScenarioLog("收到 PetSmart 的短信：$effectiveMessage"),
                ScenarioLog("更新预计完成时间：16:00 左右，继续等待完成通知。"),
            ),
            progress = ScenarioProgress(
                label = "进行中",
                detail = "预计 16:00 左右完成",
                completed = 6,
                total = 7,
            ),
        )
    }

    fun serviceProgress(
        messageBody: String,
        expediteRequested: Boolean = false,
    ): ScenarioTaskUpdate {
        val effectiveMessage = if (expediteRequested) {
            "我们已经按你的提醒优先处理 Kylin 的吹干和去浮毛，但不能压缩太多，预计 16:05 左右好。"
        } else {
            "Kylin 毛量比上次多，去浮毛会多 15 分钟，预计 16:15 左右好。"
        }
        val expectedFinish = if (expediteRequested) "16:05" else "16:15"
        return ScenarioTaskUpdate(
            taskId = TASK_ID,
            status = ScenarioSurfaceStatus.RUNNING,
            subtitle = "预计 $expectedFinish 左右完成",
            conversations = listOf(
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "PetSmart 更新了进度：$effectiveMessage 我会继续等完成通知。",
                ),
            ),
            logs = listOf(
                ScenarioLog("收到 PetSmart 的短信：$effectiveMessage"),
                ScenarioLog("更新预计完成时间：$expectedFinish 左右。"),
            ),
            progress = ScenarioProgress(
                label = "进行中",
                detail = "预计 $expectedFinish 左右完成",
                completed = 6,
                total = 7,
            ),
        )
    }

    fun departureReminderFired(): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            status = ScenarioSurfaceStatus.RUNNING,
            subtitle = "提醒已触发",
            logs = listOf(
                ScenarioLog("触发提醒：送 Kylin 下楼。"),
            ),
            progress = ScenarioProgress(
                label = "进行中",
                detail = "等待司机接到 Kylin",
                completed = 4,
                total = 7,
            ),
        )

    private val PETSMART = ScenarioParticipant(
        id = "petsmart",
        label = "PS",
        displayName = "PetSmart",
        role = "grooming_shop",
    )

    private val DRIVER = ScenarioParticipant(
        id = "driver",
        label = "DR",
        displayName = "老陈",
        role = "private_driver",
    )

    private val PROPERTY = ScenarioParticipant(
        id = "property-service",
        label = "物",
        displayName = "物业管家",
        role = "property_service",
    )
}
