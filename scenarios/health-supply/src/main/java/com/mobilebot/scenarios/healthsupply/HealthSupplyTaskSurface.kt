package com.mobilebot.scenarios.healthsupply

enum class HealthSupplySurfaceStatus {
    RUNNING,
    DONE,
    BLOCKED,
}

enum class HealthSupplySurfaceRole {
    AGENT,
    USER,
}

data class HealthSupplySurfaceConversation(
    val role: HealthSupplySurfaceRole,
    val text: String,
)

data class HealthSupplySurfaceLog(
    val text: String,
)

data class HealthSupplySurfaceParticipant(
    val id: String,
    val label: String,
    val displayName: String,
    val role: String,
)

data class HealthSupplySurfaceProgress(
    val label: String,
    val detail: String,
    val completed: Int,
    val total: Int,
)

data class HealthSupplyTaskSeed(
    val title: String,
    val subtitle: String,
    val status: HealthSupplySurfaceStatus,
    val conversations: List<HealthSupplySurfaceConversation>,
    val logs: List<HealthSupplySurfaceLog>,
    val participants: List<HealthSupplySurfaceParticipant>,
    val progress: HealthSupplySurfaceProgress,
)

data class HealthSupplyTaskUpdate(
    val status: HealthSupplySurfaceStatus = HealthSupplySurfaceStatus.RUNNING,
    val subtitle: String,
    val conversations: List<HealthSupplySurfaceConversation> = emptyList(),
    val logs: List<HealthSupplySurfaceLog> = emptyList(),
    val participants: List<HealthSupplySurfaceParticipant>? = null,
    val progress: HealthSupplySurfaceProgress,
)

object HealthSupplyTaskSurface {
    const val TASK_ID = "health-supply-live"

    fun pharmacyRestock(messageBody: String): HealthSupplyTaskSeed =
        HealthSupplyTaskSeed(
            title = "健康补给",
            subtitle = "常用品补货候选",
            status = HealthSupplySurfaceStatus.RUNNING,
            conversations = listOf(
                HealthSupplySurfaceConversation(
                    HealthSupplySurfaceRole.AGENT,
                    "常买的益生菌补货了，我先放进健康补给任务里，不打断你。",
                ),
            ),
            logs = listOf(
                HealthSupplySurfaceLog("收到美团买药通知：$messageBody"),
                HealthSupplySurfaceLog("新建健康补给任务：益生菌补货候选。"),
            ),
            participants = listOf(PHARMACY),
            progress = HealthSupplySurfaceProgress(
                label = "进行中",
                detail = "等待是否合并配送",
                completed = 1,
                total = 3,
            ),
        )

    fun deliveryCandidate(messageBody: String): HealthSupplyTaskUpdate =
        HealthSupplyTaskUpdate(
            subtitle = "可与维生素合并配送",
            conversations = listOf(
                HealthSupplySurfaceConversation(
                    HealthSupplySurfaceRole.AGENT,
                    "健康补给可以和维生素 D 一起配送，我先保留候选，等你有空再确认是否下单。",
                ),
            ),
            logs = listOf(
                HealthSupplySurfaceLog("收到美团买药通知：$messageBody"),
                HealthSupplySurfaceLog("更新健康补给候选：益生菌和维生素 D 可合并配送。"),
            ),
            progress = HealthSupplySurfaceProgress(
                label = "等待",
                detail = "低优先级，稍后再问",
                completed = 2,
                total = 3,
            ),
        )

    private val PHARMACY = HealthSupplySurfaceParticipant(
        id = "pharmacy-service",
        label = "药",
        displayName = "美团买药",
        role = "pharmacy_service",
    )
}
