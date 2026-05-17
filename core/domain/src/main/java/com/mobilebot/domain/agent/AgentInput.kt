package com.mobilebot.domain.agent

enum class AgentInputSource {
    USER_TEXT,
    SYSTEM_RUNTIME_EVENT,
    HEARTBEAT,
}

data class AgentInput(
    val chatId: String,
    val source: AgentInputSource,
    val text: String,
    val eventFact: String = "",
    val bootContext: String = "",
    val memoryHint: String = "",
) {
    fun toPromptText(): String =
        when (source) {
            AgentInputSource.USER_TEXT -> text
            AgentInputSource.SYSTEM_RUNTIME_EVENT -> buildString {
                appendLine("inputSource: SYSTEM_RUNTIME_EVENT")
                appendLine("eventFact:")
                appendLine(eventFact.ifBlank { text })
            }.trim()
            AgentInputSource.HEARTBEAT -> buildString {
                appendLine("inputSource: HEARTBEAT")
                appendLine("trigger:")
                appendLine(text)
                if (bootContext.isNotBlank()) {
                    appendLine("bootContext:")
                    appendLine(bootContext)
                }
                if (memoryHint.isNotBlank()) {
                    appendLine("memoryHint:")
                    appendLine(memoryHint)
                }
            }.trim()
        }
}
