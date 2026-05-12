package com.mobilebot.scenarios.coldchaindelivery

enum class ColdchainDeliverySurfaceStatus {
    RUNNING,
    DONE,
    BLOCKED,
}

enum class ColdchainDeliverySurfaceRole {
    AGENT,
    USER,
}

data class ColdchainDeliverySurfaceConversation(
    val role: ColdchainDeliverySurfaceRole,
    val text: String,
)

data class ColdchainDeliverySurfaceLog(
    val text: String,
)

data class ColdchainDeliverySurfaceParticipant(
    val id: String,
    val label: String,
    val displayName: String,
    val role: String,
)

data class ColdchainDeliverySurfaceProgress(
    val label: String,
    val detail: String,
    val completed: Int,
    val total: Int,
)

data class ColdchainDeliveryTaskSeed(
    val title: String,
    val subtitle: String,
    val status: ColdchainDeliverySurfaceStatus,
    val conversations: List<ColdchainDeliverySurfaceConversation>,
    val logs: List<ColdchainDeliverySurfaceLog>,
    val participants: List<ColdchainDeliverySurfaceParticipant>,
    val progress: ColdchainDeliverySurfaceProgress,
)

data class ColdchainDeliveryTaskUpdate(
    val status: ColdchainDeliverySurfaceStatus = ColdchainDeliverySurfaceStatus.RUNNING,
    val subtitle: String,
    val conversations: List<ColdchainDeliverySurfaceConversation> = emptyList(),
    val logs: List<ColdchainDeliverySurfaceLog> = emptyList(),
    val participants: List<ColdchainDeliverySurfaceParticipant>? = null,
    val participantsToAdd: List<ColdchainDeliverySurfaceParticipant> = emptyList(),
    val progress: ColdchainDeliverySurfaceProgress,
)

object ColdchainDeliveryTaskSurface {
    const val TASK_ID = "coldchain-delivery-live"

    fun arriving(messageBody: String): ColdchainDeliveryTaskSeed =
        ColdchainDeliveryTaskSeed(
            title = "冷链收货",
            subtitle = "13:45 到达小区",
            status = ColdchainDeliverySurfaceStatus.RUNNING,
            conversations = listOf(
                ColdchainDeliverySurfaceConversation(
                    ColdchainDeliverySurfaceRole.AGENT,
                    "顺丰冷链预计 13:45 到小区，我会跟进是否需要物业代收。",
                ),
            ),
            logs = listOf(
                ColdchainDeliverySurfaceLog("收到顺丰冷链通知：$messageBody"),
                ColdchainDeliverySurfaceLog("新建冷链收货任务，关注是否需要及时入冰柜。"),
            ),
            participants = listOf(COURIER),
            progress = ColdchainDeliverySurfaceProgress(
                label = "进行中",
                detail = "等待包裹到达",
                completed = 1,
                total = 3,
            ),
        )

    fun delivered(messageBody: String): ColdchainDeliveryTaskUpdate =
        ColdchainDeliveryTaskUpdate(
            subtitle = "已放入前台冰柜",
            conversations = listOf(
                ColdchainDeliverySurfaceConversation(
                    ColdchainDeliverySurfaceRole.AGENT,
                    "冷链包裹已经到前台冰柜了，我继续看物业是否能帮忙保管到你方便再取。",
                ),
            ),
            logs = listOf(
                ColdchainDeliverySurfaceLog("收到顺丰冷链通知：$messageBody"),
            ),
            progress = ColdchainDeliverySurfaceProgress(
                label = "进行中",
                detail = "等待物业确认保管",
                completed = 2,
                total = 3,
            ),
        )

    fun propertyHelp(messageBody: String): ColdchainDeliveryTaskUpdate =
        ColdchainDeliveryTaskUpdate(
            status = ColdchainDeliverySurfaceStatus.DONE,
            subtitle = "物业已协助保管",
            conversations = listOf(
                ColdchainDeliverySurfaceConversation(
                    ColdchainDeliverySurfaceRole.AGENT,
                    "物业可以先把冷链包裹放进前台冰柜，这条线我已经处理好了。",
                ),
            ),
            logs = listOf(
                ColdchainDeliverySurfaceLog("收到物业管家的短信：$messageBody"),
                ColdchainDeliverySurfaceLog("更新状态：冷链包裹由物业前台冰柜保管。"),
            ),
            participantsToAdd = listOf(PROPERTY),
            progress = ColdchainDeliverySurfaceProgress(
                label = "完成",
                detail = "物业已协助保管",
                completed = 3,
                total = 3,
            ),
        )

    private val COURIER = ColdchainDeliverySurfaceParticipant(
        id = "courier-coldchain",
        label = "顺",
        displayName = "顺丰冷链",
        role = "delivery_service",
    )

    private val PROPERTY = ColdchainDeliverySurfaceParticipant(
        id = "property-service",
        label = "物",
        displayName = "物业管家",
        role = "property_service",
    )
}
