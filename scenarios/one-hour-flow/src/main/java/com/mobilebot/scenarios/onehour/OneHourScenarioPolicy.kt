package com.mobilebot.scenarios.onehour

import com.mobilebot.domain.agent.AgentDecisionIntent
import com.mobilebot.scenarios.petgrooming.PetGroomingContacts
import com.mobilebot.scenarios.petgrooming.PetGroomingConversationRules
import com.mobilebot.scenarios.petgrooming.PetGroomingDecisionIntents
import com.mobilebot.scenarios.petgrooming.PetGroomingMilestone
import com.mobilebot.scenarios.petgrooming.PetGroomingMilestoneDetector
import com.mobilebot.scenarios.petgrooming.PetGroomingScenarioSpec
import com.mobilebot.scenarios.runtime.ScenarioDecision
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

data class OneHourScenarioConfig(
    val scenarioId: String,
    val title: String,
    val skillName: String,
    val expectedSignals: List<String>,
    val triggerText: String,
)

data class OneHourActionCandidate(
    val label: String,
    val value: String,
)

object OneHourScenarioPolicy {
    fun config(): OneHourScenarioConfig =
        PetGroomingScenarioSpec.config().let {
            OneHourScenarioConfig(
                scenarioId = it.scenarioId,
                title = it.title,
                skillName = it.skillName,
                expectedSignals = it.expectedSignals,
                triggerText = it.triggerText,
            )
        }

    fun matches(scenarioId: String): Boolean =
        PetGroomingScenarioSpec.matches(scenarioId)

    fun precheckDecision(): ScenarioDecision =
        PetGroomingScenarioSpec.precheckDecision()

    fun initialTaskLogText(): String =
        PetGroomingScenarioSpec.initialTaskLogText()

    fun triggerText(clock: LocalDateTime): String =
        PetGroomingScenarioSpec.triggerText(clock)

    fun initialDecisionInstruction(agentText: String): String =
        PetGroomingScenarioSpec.initialDecisionInstruction(agentText)

    fun deferredCompletionMessage(): String =
        PetGroomingScenarioSpec.deferredCompletionMessage()

    fun workflowStoppedError(): String =
        PetGroomingScenarioSpec.workflowStoppedError()

    fun nextMilestoneDetail(): String =
        PetGroomingScenarioSpec.nextMilestoneDetail()

    fun closureRequiredDetail(): String =
        PetGroomingScenarioSpec.closureRequiredDetail()

    fun continuationTrace(): String =
        PetGroomingScenarioSpec.continuationTrace()

    fun continuationPrompt(
        date: LocalDate,
        useAlternativeService: Boolean,
    ): String =
        PetGroomingScenarioSpec.continuationPrompt(
            groomingDate = date,
            selectedShop = PetGroomingContacts.selectedShopName(useAlternativeService),
        )

    fun decisionIntents(scenarioId: String): List<AgentDecisionIntent> =
        PetGroomingDecisionIntents.forScenario(scenarioId)

    fun isKeepCurrentWeek(intent: AgentDecisionIntent?): Boolean =
        intent == PetGroomingDecisionIntents.KeepCurrentWeek

    fun isDeferCurrentWeek(intent: AgentDecisionIntent?): Boolean =
        intent == PetGroomingDecisionIntents.DeferCurrentWeek

    fun isAcceptOpenSlot(intent: AgentDecisionIntent?): Boolean =
        intent == PetGroomingDecisionIntents.AcceptOpenSlot

    fun isKeepOriginalSlot(intent: AgentDecisionIntent?): Boolean =
        intent == PetGroomingDecisionIntents.KeepOriginalSlot

    fun isAfternoonBathOnly(intent: AgentDecisionIntent?): Boolean =
        intent == PetGroomingDecisionIntents.BookAfternoonBathOnly

    fun isAlternativeService(intent: AgentDecisionIntent?): Boolean =
        intent == PetGroomingDecisionIntents.FindAlternative

    fun actionCandidates(promptText: String): List<OneHourActionCandidate> =
        PetGroomingConversationRules.actionCandidates(promptText)
            .map { OneHourActionCandidate(it.label, it.value) }

    fun shouldSuppressResolvedPrompt(
        text: String,
        latestIntent: AgentDecisionIntent?,
    ): Boolean =
        PetGroomingConversationRules.shouldSuppressResolvedPrompt(text, latestIntent)

    fun compactActionLabel(
        label: String,
        value: String,
    ): String =
        PetGroomingConversationRules.compactActionLabel(label, value)

    fun compactDecisionPromptText(text: String): String? =
        PetGroomingConversationRules.compactDecisionPromptText(text)

    fun isTransientNarration(text: String): Boolean =
        PetGroomingConversationRules.isTransientNarration(text)

    fun isRoutineReminderQuestion(
        text: String,
        looksLikeDecisionRequest: Boolean,
    ): Boolean =
        PetGroomingConversationRules.isRoutineReminderQuestion(text, looksLikeDecisionRequest)

    fun isCompletionText(text: String): Boolean =
        PetGroomingConversationRules.isCompletionText(text)

    fun compactCompletionText(
        text: String,
        amount: String?,
    ): String =
        PetGroomingConversationRules.compactCompletionText(text, amount)

    fun normalizeAmountText(value: String): String =
        PetGroomingConversationRules.normalizeAmountText(value)

    fun isNonActionFact(value: String): Boolean =
        PetGroomingConversationRules.isNonActionFact(value)

    fun defaultServiceName(): String =
        PetGroomingContacts.defaultShopName()

    fun displayContactName(contact: String): String =
        PetGroomingContacts.displayContactName(contact)

    fun roleForContact(contact: String): String =
        PetGroomingContacts.roleForContact(contact)

    fun participantRoleForContact(contact: String): String =
        when (PetGroomingContacts.roleForContact(contact)) {
            "private_driver" -> "private_driver"
            "grooming_service" -> "service_provider"
            else -> "service"
        }

    fun labelForContact(contact: String): String =
        PetGroomingContacts.labelForContact(contact)

    fun isServiceContact(contact: String): Boolean =
        PetGroomingContacts.isGroomingShopContact(contact)

    fun displayReminderBody(raw: String): String =
        PetGroomingContacts.displayDriverReminderBody(raw)

    fun serviceTaskLogText(
        serviceId: String,
        action: String,
        namedEntity: String,
    ): String? =
        when {
            serviceId == "pet_salon_search" && action.lowercase().contains("detail") -> {
                val serviceName = namedEntity.ifBlank { PetGroomingContacts.defaultShopName() }
                "添加 $serviceName 到参与方。"
            }
            serviceId == "pet_salon_search" -> "查询附近服务方。"
            else -> null
        }

    fun orchestrationInstruction(
        plannerPolicyJson: String,
    ): String =
        """
            你需要根据系统事件事实、当前任务状态和用户记忆，规划这一轮需要执行的受控命令。
            plannerPolicy 是运行时边界和授权，不是参考答案；不要假设存在本地兜底业务结果。
            任务更新、追问、完成动作都通过 emit_scenario_commands 输出。
            如果需要追问用户，ask_user 必须包含 decision.text 和 decision.actions，action 必须包含 label/key。
            短信和提醒属于副作用，只能在 plannerPolicy 授权相同目标或时间时输出。
            如果 plannerPolicy 针对当前事实提供 protocol block，必须按其中的命令顺序和约束执行。
            不要输出解释文本，只调用 emit_scenario_commands。

            plannerPolicy:
            $plannerPolicyJson
        """.trimIndent()

    fun userDecisionInstruction(
        userText: String,
        plannerPolicyJson: String,
    ): String =
        """
            用户刚刚回复：$userText。
            你需要先理解用户真实意图，再输出受控任务命令。
            plannerPolicy 是边界和授权，不是参考答案；不要把所有未知回复都强行拉回现有按钮。
            如果用户表达了任务终止、对象不存在、无需继续或条件变化，应更新任务为停止/完成并清空决策，而不是继续追问原二选一。
            如果用户明确同意改到 14:00，通常需要更新任务、通知 PetSmart 和 Driver，并监听 Driver 确认；这些副作用只能在 plannerPolicy 授权时输出。
            如果 plannerPolicy 提供协议块，例如 petSlotAcceptanceProtocol，按协议块规划命令顺序和关键时间，不要自行编造接送时间。
            只有在用户确实仍围绕排期但表达不清时，才使用 ask_user 追问。
            如果需要追问用户，ask_user 必须包含 decision.text 和 decision.actions，action 必须包含 label/key。
            短信和提醒属于副作用，只能在 plannerPolicy 授权相同目标或时间时输出。
            不要输出解释文本，只调用 emit_scenario_commands。

            plannerPolicy:
            $plannerPolicyJson
        """.trimIndent()
}

class OneHourScenarioRunTracker {
    private val milestones = mutableSetOf<PetGroomingMilestone>()
    var paymentAmount: String? = null
        private set

    fun clear() {
        milestones.clear()
        paymentAmount = null
    }

    fun recordSystemRuntimeData(data: JSONObject) {
        val update = PetGroomingMilestoneDetector.fromSystemRuntimeData(data)
        milestones += update.milestones
        paymentAmount = update.paymentAmount ?: paymentAmount
    }

    fun closureSatisfied(): Boolean =
        milestones.containsAll(
            setOf(
                PetGroomingMilestone.HOME_CONFIRMED,
                PetGroomingMilestone.PAYMENT_COMPLETED,
                PetGroomingMilestone.EXPENSE_RECORDED,
            ),
        )
}
