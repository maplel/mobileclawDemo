package com.mobilebot.systemruntime

import com.mobilebot.domain.tools.Tool
import com.mobilebot.domain.tools.ToolExecutionPolicy
import com.mobilebot.domain.tools.ToolRisk
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class SystemSendEmailTool
    @Inject
    constructor(
        private val runtime: SystemRuntimeActions,
    ) : Tool {
        override val name: String = NAME

        override val definition: ToolDefinition = ToolDefinition(
            name = name,
            description = "Send an email through the system runtime.",
            parametersSchema = """
            {
              "type": "object",
              "properties": {
                "to": {"type": "string", "description": "Recipient email address or contact."},
                "subject": {"type": "string", "description": "Email subject."},
                "body": {"type": "string", "description": "Email body."}
              },
              "required": ["to", "subject", "body"]
            }
            """.trimIndent(),
        )

        override val risk: ToolRisk = ToolRisk.LOW

        override val executionPolicy: ToolExecutionPolicy = ToolExecutionPolicy(
            requiresConnectivity = false,
            requiresUserApproval = false,
            hasSideEffects = true,
        )

        override suspend fun execute(argumentsJson: String): ToolResult =
            runCatching {
                val args = JSONObject(argumentsJson)
                val to = args.optString("to").trim()
                val subject = args.optString("subject").trim()
                val body = args.optString("body").trim()
                if (to.isBlank()) return ToolResult(false, "Missing recipient")
                if (subject.isBlank()) return ToolResult(false, "Missing subject")
                if (body.isBlank()) return ToolResult(false, "Missing body")
                val result = runtime.execute("send_email", args)
                ToolResult(result.ok, result.message, JSONObject(result.data).toString())
            }.getOrElse { error ->
                ToolResult(false, error.message ?: "$NAME failed")
            }

        companion object {
            const val NAME = "system_send_email"
        }
    }

class SystemQueryWebTool
    @Inject
    constructor(
        private val runtime: SystemRuntimeActions,
    ) : Tool {
        override val name: String = NAME

        override val definition: ToolDefinition = ToolDefinition(
            name = name,
            description = "Query the simulated web surface through the system runtime.",
            parametersSchema = """
            {
              "type": "object",
              "properties": {
                "query": {"type": "string", "description": "Search query or page question."},
                "url": {"type": "string", "description": "Optional URL to query."}
              },
              "required": ["query"]
            }
            """.trimIndent(),
        )

        override val risk: ToolRisk = ToolRisk.LOW

        override val executionPolicy: ToolExecutionPolicy = ToolExecutionPolicy(
            requiresConnectivity = false,
            requiresUserApproval = false,
            hasSideEffects = false,
        )

        override suspend fun execute(argumentsJson: String): ToolResult =
            runCatching {
                val args = JSONObject(argumentsJson)
                val query = args.optString("query").trim()
                if (query.isBlank()) return ToolResult(false, "Missing query")
                val result = runtime.execute("query_web", args)
                ToolResult(result.ok, result.message, JSONObject(result.data).toString())
            }.getOrElse { error ->
                ToolResult(false, error.message ?: "$NAME failed")
            }

        companion object {
            const val NAME = "system_query_web"
        }
    }
