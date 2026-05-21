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
    const val PURPOSE_RESCHEDULE_SERVICE = "pet_grooming.reschedule_service"
    const val PURPOSE_CANCEL_SERVICE = "pet_grooming.cancel_service"

    fun openSlotSeed(messageBody: String): ScenarioTaskSeed =
        ScenarioTaskSeed(
            taskId = TASK_ID,
            title = "麒麟洗护",
            subtitle = "PetSmart 14:00 空档待确认",
            status = ScenarioSurfaceStatus.BLOCKED,
            conversations = emptyList(),
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
            conversations = emptyList(),
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
            conversations = emptyList(),
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
            conversations = emptyList(),
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
            conversations = emptyList(),
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

    fun rescheduleRequested(
        userText: String,
        targetWeekOffset: Int,
        unavailableWeekOffsets: List<Int>,
    ): ScenarioTaskUpdate {
        val targetLabel = PetGroomingUserTurnInterpreter.weekLabel(targetWeekOffset)
        val unavailableText = unavailableWeekOffsets
            .map { PetGroomingUserTurnInterpreter.weekLabel(it) }
            .takeIf { it.isNotEmpty() }
            ?.joinToString("、", prefix = "，避开 ", postfix = " 没空的时间")
            .orEmpty()
        return ScenarioTaskUpdate(
            taskId = TASK_ID,
            status = ScenarioSurfaceStatus.DONE,
            subtitle = "已请求改到$targetLabel",
            conversations = listOf(
                ScenarioConversation(ScenarioSurfaceRole.USER, userText),
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "我已请 PetSmart 取消当前 14:00 洗护并改到$targetLabel$unavailableText；当前接送和洗护链路会停止，后续等门店确认新档期。",
                ),
            ),
            logs = listOf(
                ScenarioLog("结构化场景输入：$PURPOSE_RESCHEDULE_SERVICE，target_week_offset=$targetWeekOffset。"),
                ScenarioLog("发送短信给 PetSmart：取消当前 14:00 洗护并改到$targetLabel$unavailableText。"),
                ScenarioLog("当前系统 runtime 后续 PetSmart / Driver 固定事件已挂起，避免和改期结果冲突。"),
            ),
            participantsToAdd = listOf(PETSMART, DRIVER),
            progress = ScenarioProgress(
                label = "已改期",
                detail = "等待新档期确认",
                completed = 1,
                total = 1,
            ),
            finalSummary = "已请求改到$targetLabel，当前 14:00 洗护链路停止。",
        )
    }

    fun cancelRequested(
        userText: String,
        stageText: String,
    ): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            status = ScenarioSurfaceStatus.DONE,
            subtitle = "已取消本次洗护",
            conversations = listOf(
                ScenarioConversation(ScenarioSurfaceRole.USER, userText),
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "我已请 PetSmart 取消当前 14:00 洗护，并通知接送方停止本次行程。当前洗护链路会停止。",
                ),
            ),
            logs = listOf(
                ScenarioLog("结构化场景输入：$PURPOSE_CANCEL_SERVICE，current_stage=$stageText。"),
                ScenarioLog("发送短信给 PetSmart：取消当前 14:00 洗护。"),
                ScenarioLog("发送短信给 Driver：本次 Kylin 行程取消。"),
            ),
            participantsToAdd = listOf(PETSMART, DRIVER),
            progress = ScenarioProgress(
                label = "已取消",
                detail = "当前行程停止",
                completed = 1,
                total = 1,
            ),
            finalSummary = "已取消当前 14:00 洗护和接送。",
        )

    fun rescheduleConflict(
        userText: String,
        stageText: String,
    ): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            status = ScenarioSurfaceStatus.RUNNING,
            subtitle = "$stageText，不能直接改期",
            conversations = listOf(
                ScenarioConversation(ScenarioSurfaceRole.USER, userText),
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "Kylin 当前$stageText，不能直接把本次服务改到下周。我先不改当前安排；如果要取消本次并让老陈接回，请明确说“取消本次并接回”。",
                ),
            ),
            logs = listOf(
                ScenarioLog("结构化场景输入：$PURPOSE_RESCHEDULE_SERVICE，但当前阶段不允许直接改期。"),
                ScenarioLog("未发送改期短信，避免让固定 runtime 和用户临时改期产生冲突。"),
            ),
            participantsToAdd = listOf(PETSMART, DRIVER),
            progress = ScenarioProgress(
                label = "进行中",
                detail = stageText,
                completed = 6,
                total = 7,
            ),
        )

    fun statusAnswer(
        userText: String,
        stageText: String,
        etaText: String,
        completed: Int,
    ): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            status = ScenarioSurfaceStatus.RUNNING,
            subtitle = stageText,
            conversations = listOf(
                ScenarioConversation(ScenarioSurfaceRole.USER, userText),
                ScenarioConversation(ScenarioSurfaceRole.AGENT, "当前状态：$stageText。$etaText"),
            ),
            logs = listOf(
                ScenarioLog("结构化场景输入：pet_grooming.ask_status，直接读取场景状态机。"),
            ),
            participantsToAdd = listOf(PETSMART, DRIVER),
            progress = ScenarioProgress(
                label = "进行中",
                detail = stageText,
                completed = completed,
                total = 7,
            ),
        )

    fun clarificationNeeded(
        userText: String,
        reason: String,
    ): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            status = ScenarioSurfaceStatus.BLOCKED,
            subtitle = "需要确认你的具体安排",
            conversations = listOf(
                ScenarioConversation(ScenarioSurfaceRole.USER, userText),
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    if (reason == "reschedule_target") {
                        "你是想改 Kylin 的洗护时间。请明确目标时间，例如“改到下周”或“这周和下下周没空，改下下下周”。"
                    } else {
                        "这句话和 Kylin 洗护有关，但目标动作不够明确。请说明是改期、取消、查询进度，还是催 PetSmart 加快。"
                    },
                ),
            ),
            logs = listOf(
                ScenarioLog("结构化场景输入：needs_clarification，reason=$reason。"),
            ),
            participantsToAdd = listOf(PETSMART),
            progress = ScenarioProgress(
                label = "等待",
                detail = "等待用户补充",
                completed = 0,
                total = 1,
            ),
        )

    fun outOfScope(userText: String): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            status = ScenarioSurfaceStatus.RUNNING,
            subtitle = "未改动 Kylin 洗护安排",
            conversations = listOf(
                ScenarioConversation(ScenarioSurfaceRole.USER, userText),
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "这句话和当前 Kylin 洗护场景无关，我没有改动 PetSmart、Driver 或后续 runtime 状态。",
                ),
            ),
            logs = listOf(
                ScenarioLog("结构化场景输入：unknown/out_of_scope，未产生外部副作用。"),
            ),
            progress = ScenarioProgress(
                label = "进行中",
                detail = "原安排保持不变",
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
                    effectiveMessage,
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
            "Kylin 毛量比上次多，但他们已经按你的提醒优先处理吹干和去浮毛，预计仍在 16:00 左右好。"
        } else {
            "Kylin 毛量比上次多，去浮毛会多 15 分钟，预计 16:15 左右好。"
        }
        val expectedFinish = if (expediteRequested) "16:00" else "16:15"
        return ScenarioTaskUpdate(
            taskId = TASK_ID,
            status = ScenarioSurfaceStatus.RUNNING,
            subtitle = "预计 $expectedFinish 左右完成",
            conversations = listOf(
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "$effectiveMessage 我会继续等完成通知。",
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
