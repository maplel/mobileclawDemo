package com.mobilebot.domain.agent

// Agent 核心层只关心输入路由和动作语义，不依赖 Android UI 类型。
data class AgentSessionRoute(
    val sessionId: String?,
    val taskId: String?,
)

sealed interface AgentSessionInput {
    val route: AgentSessionRoute

    data class ActionSelected(
        override val route: AgentSessionRoute,
        val action: AgentDecisionAction,
    ) : AgentSessionInput

    data class TextSubmitted(
        override val route: AgentSessionRoute,
        val text: String,
    ) : AgentSessionInput
}
