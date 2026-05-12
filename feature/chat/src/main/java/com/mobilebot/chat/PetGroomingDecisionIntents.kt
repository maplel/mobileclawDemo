package com.mobilebot.chat

import com.mobilebot.domain.agent.AgentDecisionIntents
import com.mobilebot.domain.agent.AgentDecisionIntent

// 场景候选意图由场景层提供；核心 Agent 只负责归一化。
internal object PetGroomingDecisionIntents {
    val KeepCurrentWeek = AgentDecisionIntent(
        id = "pet_grooming.keep_current_week",
        displayLabel = "好的",
        meaning = "Y wants to keep Kylin's regular grooming appointment this week.",
    )

    val DeferCurrentWeek = AgentDecisionIntent(
        id = "pet_grooming.defer_current_week",
        displayLabel = "改天再说",
        meaning = "Y wants to skip or postpone this week's Kylin grooming run.",
    )

    val AcceptOpenSlot = AgentDecisionIntent(
        id = "pet_grooming.accept_14_open_slot",
        displayLabel = "可以",
        meaning = "Y accepts PetSmart's same-day 14:00 grooming plus de-shedding slot and authorizes routine downstream coordination.",
    )

    val KeepOriginalSlot = AgentDecisionIntent(
        id = "pet_grooming.keep_17_original_slot",
        displayLabel = "不改了",
        meaning = "Y wants to keep the original 17:00 bath-only PetSmart appointment and skip the 14:00 change.",
    )

    val BookNine = AgentDecisionIntent(
        id = "pet_grooming.book_0900",
        displayLabel = "约9点",
        meaning = "Y chooses the 9:00 PetSmart slot and authorizes routine downstream coordination.",
    )

    val AskAfternoon = AgentDecisionIntent(
        id = "pet_grooming.ask_afternoon",
        displayLabel = "问下午",
        meaning = "Y wants the agent to ask PetSmart about afternoon availability before deciding.",
        agentInstruction = """
            NEXT_OPERATION: The next assistant step must be a tool call, not user-facing prose. Call system_send_sms to PetSmart with this exact Chinese message: `请问明天下午5点以后可以只安排基础洗澡服务吗？` Then call system_wait_for_sms for PetSmart's reply. Do not repeat the previous options before the new PetSmart SMS is received. Do not write English words such as booking or bath-only in the SMS body.
        """.trimIndent(),
    )

    val BookAfternoonBathOnly = AgentDecisionIntent(
        id = "pet_grooming.book_afternoon_bath_only",
        displayLabel = "约下午5点",
        meaning = "Y accepts the afternoon bath-only PetSmart slot and authorizes routine downstream coordination.",
        agentInstruction = """
            FINAL_SELECTION: Y has accepted the afternoon bath-only PetSmart slot. This is not another availability question.
            NEXT_OPERATION: The next assistant step must be a tool call, not user-facing prose. Call system_send_sms to PetSmart with this exact confirmation message: `您好，确认明天下午5点后给 Kylin 预约洗澡，不做除毛。谢谢。` Then call system_wait_for_sms for PetSmart's booking confirmation. Do not ask PetSmart about availability again. Do not show another action prompt for the afternoon tradeoff. Do not ask Y to confirm the same option again.
        """.trimIndent(),
    )

    val FindAlternative = AgentDecisionIntent(
        id = "pet_grooming.find_alternative_shop",
        displayLabel = "换一家",
        meaning = "Y wants to look for another grooming shop.",
        agentInstruction = """
            NEXT_OPERATION: The next assistant step must be a tool call, not user-facing prose. Use device_system service_call with serviceId `pet_salon_search` to list nearby grooming shops, then choose a non-PetSmart shop that supports both extra-large dog bathing and de-shedding. Prefer Harbor Paws Salon if it is present because it supports both required services. Call get_pet_shop_detail for the chosen shop, then send SMS to that shop with this Chinese message: `请问明天下午2点可以给 Kylin 安排基础洗澡和去浮毛服务吗？她是30公斤以上的伯恩山犬。` Then call system_wait_for_sms for that shop's reply. Use the chosen shop name consistently in later Driver, payment, accounting, and summary steps.
        """.trimIndent(),
    )

    fun forScenario(scenarioId: String): List<AgentDecisionIntent> =
        if (scenarioId == "pet-grooming") {
            listOf(
                KeepCurrentWeek,
                DeferCurrentWeek,
                AcceptOpenSlot,
                KeepOriginalSlot,
                BookNine,
                AskAfternoon,
                BookAfternoonBathOnly,
                FindAlternative,
            ) + AgentDecisionIntents.defaults
        } else {
            AgentDecisionIntents.defaults
        }
}
