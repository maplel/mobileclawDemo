package com.mobilebot.systemruntime

import com.mobilebot.domain.tools.Tool
import com.mobilebot.domain.tools.ToolExecutionPolicy
import com.mobilebot.domain.tools.ToolRisk
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class SystemSearchContactsTool
    @Inject
    constructor(
        private val runtime: SystemRuntime,
    ) : Tool {
        override val name: String = NAME

        override val definition: ToolDefinition = ToolDefinition(
            name = name,
            description = "Search system contacts by name, role, or capability before messaging, calling, transport, service, or payment workflows.",
            parametersSchema = """
            {
              "type": "object",
              "properties": {
                "query": {"type": "string", "description": "Name, alias, role, or capability to search for."},
                "role": {"type": "string", "description": "Optional contact role, such as service provider, driver, family, or emergency contact."},
                "capability": {"type": "string", "description": "Optional capability, such as pickup, dropoff, payment, scheduling, or urgent care."}
              }
            }
            """.trimIndent(),
        )

        override val risk: ToolRisk = ToolRisk.LOW

        override val executionPolicy: ToolExecutionPolicy = ToolExecutionPolicy(
            requiresConnectivity = false,
            requiresUserApproval = false,
            hasSideEffects = false,
        )

        override suspend fun execute(argumentsJson: String): ToolResult {
            return try {
                val result = runtime.searchContactsFromTool(JSONObject(argumentsJson))
                ToolResult(result.ok, result.message, JSONObject(result.data).toString())
            } catch (e: Exception) {
                ToolResult(false, e.message ?: "$NAME failed")
            }
        }

        companion object {
            const val NAME = "system_search_contacts"
        }
    }
