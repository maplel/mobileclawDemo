package com.mobilebot.domain.tools

import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult

interface Tool {
    val name: String
    val definition: ToolDefinition
    val requiresUserConfirmation: Boolean
        get() = executionPolicy.requiresUserApproval

    val risk: ToolRisk
        get() = ToolRisk.LOW

    val requiredCapabilities: Set<String>
        get() = emptySet()

    val executionPolicy: ToolExecutionPolicy
        get() = ToolExecutionPolicy()

    val hasSideEffects: Boolean
        get() = executionPolicy.hasSideEffects

    suspend fun execute(argumentsJson: String): ToolResult
}
