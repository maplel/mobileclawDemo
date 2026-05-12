package com.mobilebot.scenarios.familyshopping

import com.mobilebot.scenarios.runtime.ScenarioConversation
import com.mobilebot.scenarios.runtime.ScenarioLog
import com.mobilebot.scenarios.runtime.ScenarioParticipant
import com.mobilebot.scenarios.runtime.ScenarioProgress
import com.mobilebot.scenarios.runtime.ScenarioSurfaceRole
import com.mobilebot.scenarios.runtime.ScenarioSurfaceStatus
import com.mobilebot.scenarios.runtime.ScenarioTaskSeed
import com.mobilebot.scenarios.runtime.ScenarioTaskUpdate

object FamilyShoppingTaskSurface {
    const val TASK_ID = "family-shopping-live"

    fun fromEllaCall(): ScenarioTaskSeed =
        ScenarioTaskSeed(
            taskId = TASK_ID,
            title = "家庭采购",
            subtitle = "Ella 电话交代的待办",
            status = ScenarioSurfaceStatus.RUNNING,
            conversations = listOf(
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "刚才 Ella 电话里提到周末家庭采购，我已经帮你建立任务跟踪，会继续整理需要确认的事项。",
                ),
            ),
            logs = listOf(
                ScenarioLog("通话转写完成：识别到 Ella 交代的家庭采购待办。"),
                ScenarioLog("新建家庭采购任务。"),
            ),
            participants = listOf(ELLA),
            progress = ScenarioProgress(
                label = "进行中",
                detail = "整理待办事项",
                completed = 1,
                total = 4,
            ),
        )

    fun priorityFollowup(messageBody: String): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            subtitle = "采购优先级已更新",
            conversations = listOf(
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "Ella 又补充了采购优先级：低脂牛奶和洗衣液优先，水果顺路再买。我已经同步到任务里。",
                ),
            ),
            logs = listOf(
                ScenarioLog("收到 Ella 的短信：$messageBody"),
                ScenarioLog("更新采购优先级：低脂牛奶、洗衣液优先，水果可选。"),
            ),
            progress = ScenarioProgress(
                label = "进行中",
                detail = "匹配采购方案",
                completed = 2,
                total = 4,
            ),
        )

    fun marketDeliveryCandidate(messageBody: String): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            subtitle = "已找到可配送渠道",
            conversations = listOf(
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "附近超市有低脂牛奶和常用洗衣液，45 分钟内可送达。我先把它作为家庭采购候选，不打断你。",
                ),
            ),
            logs = listOf(
                ScenarioLog("收到 Ole 通知：$messageBody"),
                ScenarioLog("加入采购候选：低脂牛奶、常用洗衣液，预计 45 分钟内送达。"),
            ),
            participants = listOf(ELLA, OLE),
            progress = ScenarioProgress(
                label = "进行中",
                detail = "等待清单确认",
                completed = 3,
                total = 4,
            ),
        )

    fun clarifiedList(messageBody: String): ScenarioTaskUpdate =
        ScenarioTaskUpdate(
            taskId = TASK_ID,
            subtitle = "采购清单已收敛",
            conversations = listOf(
                ScenarioConversation(
                    ScenarioSurfaceRole.AGENT,
                    "Ella 又调整了清单：洗衣液买常用款，猫粮不用买。我已经把候选清单收敛好了。",
                ),
            ),
            logs = listOf(
                ScenarioLog("收到 Ella 的短信：$messageBody"),
                ScenarioLog("更新采购清单：保留低脂牛奶、常用洗衣液；移除猫粮。"),
            ),
            progress = ScenarioProgress(
                label = "进行中",
                detail = "清单已收敛",
                completed = 4,
                total = 4,
            ),
        )

    private val ELLA = ScenarioParticipant(
        id = "ella",
        label = "E",
        displayName = "Ella",
        role = "family",
    )

    private val OLE = ScenarioParticipant(
        id = "ole",
        label = "O",
        displayName = "Ole",
        role = "market",
    )
}
