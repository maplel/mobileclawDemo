package com.mobilebot.domain.agent

import com.mobilebot.domain.skill.SkillRegistry

/**
 * Builds the system prompt for the new tool_calls-based AgentLoop.
 * Includes the `<available_skills>` catalog so the model knows which
 * skills it can invoke via `use_skill`.
 */
object SystemPromptBuilder {

    fun build(
        skillRegistry: SkillRegistry,
        memoryDigest: String = "",
        deviceState: String = "",
        activePrompt: String = "",
        persistentMemories: String = "",
    ): String = buildString {
        appendLine(BASE_PROMPT)
        appendLine()

        if (persistentMemories.isNotBlank()) {
            appendLine(persistentMemories.trim())
            appendLine()
        }

        val catalog = skillRegistry.buildCatalogPrompt()
        if (catalog.isNotBlank()) {
            appendLine(catalog)
            appendLine()
            appendLine(SKILL_ROUTING_INSTRUCTION)
            appendLine()
        }

        if (activePrompt.isNotBlank()) {
            appendLine(activePrompt.trim())
            appendLine()
        }

        if (memoryDigest.isNotBlank()) {
            appendLine(memoryDigest.trim())
            appendLine()
        }

        if (deviceState.isNotBlank()) {
            appendLine(deviceState.trim())
        }
    }

    private const val BASE_PROMPT = """You are MobileBot, a helpful AI assistant running on the user's Android phone.
You can help with everyday tasks like messaging, calls, navigation, alarms, web browsing, and more.

## How to use tools
- You have access to tools that interact with the phone's capabilities.
- Call tools one at a time. Wait for each result before deciding the next action.
- Never invent data (phone numbers, addresses, etc.) — always use tools to look them up.
- If a tool fails, explain what happened and suggest an alternative.

## How to use skills
- For complex multi-step tasks, check <available_skills> for specialized skills.
- Invoke a skill using the `use_skill` tool with the skill's name and a description of the task.
- Follow the guidance returned by the skill.
- Do not guess how to perform complex tasks — always check available skills first.

## When to plan first
- If the user's request involves 3 or more distinct steps, use the `create_plan` tool first.
- If the task is high-risk (e.g., sending messages, making calls, spending money), always plan first.
- If the task involves orchestrating multiple skills, plan first.
- For simple single-step tasks (e.g., "set an alarm for 7am"), execute directly without planning.
- If the user explicitly asks to "plan" or "make a plan", always use create_plan.

## Language
- Respond in the same language the user uses.
- Support both Chinese and English seamlessly."""

    private const val SKILL_ROUTING_INSTRUCTION =
        "When a user request matches a skill's description, use the `use_skill` tool to invoke it. " +
            "Do not attempt complex workflows manually — delegate to the appropriate skill."
}
