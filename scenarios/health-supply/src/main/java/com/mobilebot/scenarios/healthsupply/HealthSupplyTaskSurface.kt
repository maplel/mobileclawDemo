package com.mobilebot.scenarios.healthsupply

import com.mobilebot.scenarios.runtime.ScenarioConversation
import com.mobilebot.scenarios.runtime.ScenarioLog
import com.mobilebot.scenarios.runtime.ScenarioParticipant
import com.mobilebot.scenarios.runtime.ScenarioProgress
import com.mobilebot.scenarios.runtime.ScenarioSurfaceRole
import com.mobilebot.scenarios.runtime.ScenarioSurfaceStatus
import com.mobilebot.scenarios.runtime.ScenarioTaskSeed
import com.mobilebot.scenarios.runtime.ScenarioTaskUpdate

object HealthSupplyTaskSurface {
    const val TASK_ID = "health-supply-live"

    fun pharmacyRestock(messageBody: String): ScenarioTaskSeed =
        ScenarioTaskSeed(
            taskId = TASK_ID,
            title = "健康补给",
            subtitle = "常用品补货候选",
            status = ScenarioSurfaceStatus.RUNNING,
            conversations = listOf(
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "常买的益生菌补货了，我先放进健康补给任务里，不打断你。",
                ),
            ),
            logs = listOf(
                ScenarioLog("收到美团买药通知：$messageBody"),
                ScenarioLog("新建健康补给任务：益生菌补货候选。"),
            ),
            participants = listOf(PHARMACY),
            progress = ScenarioProgress(
                label = "进行中",
                detail = "等待是否合并配送",
                completed = 1,
                total = 3,
            ),
        )

    fun deliveryCandidate(messageBody: String): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            subtitle = "可与维生素合并配送",
            conversations = listOf(
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "健康补给可以和维生素 D 一起配送，我先保留候选，等你有空再确认是否下单。",
                ),
            ),
            logs = listOf(
                ScenarioLog("收到美团买药通知：$messageBody"),
                ScenarioLog("更新健康补给候选：益生菌和维生素 D 可合并配送。"),
            ),
            progress = ScenarioProgress(
                label = "等待",
                detail = "低优先级，稍后再问",
                completed = 2,
                total = 3,
            ),
        )

    private val PHARMACY = ScenarioParticipant(
        id = "pharmacy-service",
        label = "药",
        displayName = "美团买药",
        role = "pharmacy_service",
    )
}
