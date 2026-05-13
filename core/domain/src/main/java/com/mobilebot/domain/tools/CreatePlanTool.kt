package com.mobilebot.domain.tools

import com.mobilebot.domain.agent.PlanManager
import com.mobilebot.domain.agent.CurrentSessionKeyProvider
import com.mobilebot.domain.todo.PlanTodo
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

/**
 * Tool the LLM calls to propose a structured plan for complex tasks.
 * The plan is stored in [PlanManager] and emitted to the UI timeline.
 *
 * Autonomy policy: planning is visible but non-blocking. Scenario skills own the
 * exact decision points where the agent must pause for the user.
 */
class CreatePlanTool @Inject constructor(
    private val planManager: PlanManager,
    private val sessionKeyProvider: CurrentSessionKeyProvider,
) : Tool {

    override val name: String = NAME

    override val definition: ToolDefinition = ToolDefinition(
        name = NAME,
        description = "Create a step-by-step plan for a complex task. " +
            "Use when the user's request involves 3+ steps, multiple skills, or high-risk actions. " +
            "The plan is shown to the user, but the agent should continue unless a declared decision point requires input.",
        parametersSchema = SCHEMA,
    )

    override val risk: ToolRisk = ToolRisk.LOW

    override val executionPolicy: ToolExecutionPolicy = ToolExecutionPolicy(
        requiresConnectivity = false,
        requiresUserApproval = false,
        hasSideEffects = false,
    )

    override suspend fun execute(argumentsJson: String): ToolResult {
        return try {
            val obj = JSONObject(argumentsJson)
            val title = obj.optString("title", "").trim()
            if (title.isEmpty()) {
                return ToolResult(ok = false, message = "Missing required parameter: title")
            }

            val stepsArr = obj.optJSONArray("steps")
            if (stepsArr == null || stepsArr.length() == 0) {
                return ToolResult(ok = false, message = "Missing or empty required parameter: steps")
            }

            val steps = mutableListOf<PlanTodo>()
            for (i in 0 until stepsArr.length()) {
                val stepObj = stepsArr.optJSONObject(i) ?: continue
                val id = stepObj.optString("id", "").trim().ifEmpty { "step_${i + 1}" }
                val text = stepObj.optString("text", "").trim()
                if (text.isNotEmpty()) {
                    steps.add(PlanTodo(id = id, text = text))
                }
            }

            if (steps.isEmpty()) {
                return ToolResult(ok = false, message = "No valid steps found in plan")
            }

            val chatId = sessionKeyProvider.getChatId()
            planManager.storePlan(chatId, title, steps, emptyList())

            ToolResult(ok = true, message = "Plan stored for the UI timeline. Continue unless a declared decision point requires user input.")
        } catch (e: Exception) {
            ToolResult(ok = false, message = "Failed to create plan: ${e.message}")
        }
    }

    companion object {
        const val NAME = "create_plan"

        private val SCHEMA = """
        {
          "type": "object",
          "properties": {
            "title": {
              "type": "string",
              "description": "A concise title for the plan"
            },
            "steps": {
              "type": "array",
              "description": "Ordered list of steps to execute",
              "items": {
                "type": "object",
                "properties": {
                  "id":   { "type": "string", "description": "Unique step identifier" },
                  "text": { "type": "string", "description": "Description of what this step does" }
                },
                "required": ["id", "text"]
              }
            }
          },
          "required": ["title", "steps"]
        }
        """.trimIndent()
    }
}
