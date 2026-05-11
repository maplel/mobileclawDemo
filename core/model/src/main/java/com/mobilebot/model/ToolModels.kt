package com.mobilebot.model

data class ToolDefinition(
    val name: String,
    val description: String,
    val parametersSchema: String,
)

/**
 * Structured tool execution outcome for the LLM (message is what the model sees as tool output).
 */
data class ToolResult(
    val ok: Boolean,
    val message: String,
    val dataJson: String? = null,
)
