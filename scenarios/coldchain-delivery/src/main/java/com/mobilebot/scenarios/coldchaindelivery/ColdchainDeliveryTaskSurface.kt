package com.mobilebot.scenarios.coldchaindelivery

import com.mobilebot.scenarios.runtime.ScenarioConversation
import com.mobilebot.scenarios.runtime.ScenarioLog
import com.mobilebot.scenarios.runtime.ScenarioParticipant
import com.mobilebot.scenarios.runtime.ScenarioProgress
import com.mobilebot.scenarios.runtime.ScenarioSurfaceRole
import com.mobilebot.scenarios.runtime.ScenarioSurfaceStatus
import com.mobilebot.scenarios.runtime.ScenarioTaskSeed
import com.mobilebot.scenarios.runtime.ScenarioTaskUpdate

object ColdchainDeliveryTaskSurface {
    const val TASK_ID = "coldchain-delivery-live"

    fun arriving(messageBody: String): ScenarioTaskSeed =
        ScenarioTaskSeed(
            taskId = TASK_ID,
            title = "冷链收货",
            subtitle = "13:45 到达小区",
            status = ScenarioSurfaceStatus.RUNNING,
            conversations = listOf(
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "顺丰冷链预计 13:45 到小区，我会跟进是否需要物业代收。",
                ),
            ),
            logs = listOf(
                ScenarioLog("收到顺丰冷链通知：$messageBody"),
                ScenarioLog("新建冷链收货任务，关注是否需要及时入冰柜。"),
            ),
            participants = listOf(COURIER),
            progress = ScenarioProgress(
                label = "进行中",
                detail = "等待包裹到达",
                completed = 1,
                total = 3,
            ),
        )

    fun delivered(messageBody: String): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            subtitle = "已放入前台冰柜",
            conversations = listOf(
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "冷链包裹已经到前台冰柜了，我继续看物业是否能帮忙保管到你方便再取。",
                ),
            ),
            logs = listOf(
                ScenarioLog("收到顺丰冷链通知：$messageBody"),
            ),
            progress = ScenarioProgress(
                label = "进行中",
                detail = "等待物业确认保管",
                completed = 2,
                total = 3,
            ),
        )

    fun propertyHelp(messageBody: String): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            status = ScenarioSurfaceStatus.DONE,
            subtitle = "物业已协助保管",
            conversations = listOf(
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "物业可以先把冷链包裹放进前台冰柜，这条线我已经处理好了。",
                ),
            ),
            logs = listOf(
                ScenarioLog("收到物业管家的短信：$messageBody"),
                ScenarioLog("更新状态：冷链包裹由物业前台冰柜保管。"),
            ),
            participantsToAdd = listOf(PROPERTY),
            progress = ScenarioProgress(
                label = "完成",
                detail = "物业已协助保管",
                completed = 3,
                total = 3,
            ),
        )

    private val COURIER = ScenarioParticipant(
        id = "courier-coldchain",
        label = "顺",
        displayName = "顺丰冷链",
        role = "delivery_service",
    )

    private val PROPERTY = ScenarioParticipant(
        id = "property-service",
        label = "物",
        displayName = "物业管家",
        role = "property_service",
    )
}
