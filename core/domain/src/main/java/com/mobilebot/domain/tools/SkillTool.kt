package com.mobilebot.domain.tools

import com.mobilebot.domain.skill.SkillExecutor
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

/**
 * The bridge between the LLM and the Skills layer.
 *
 * Registered in ToolRegistry like any other tool. When the model sees
 * `<available_skills>` in the system prompt, it invokes this tool to
 * activate a skill. The system then reads the SKILL.md, checks
 * eligibility, applies execution strategy, and returns guidance.
 *
 * The model selects the appropriate skill from the `<available_skills>` catalog.
 */
class SkillTool @Inject constructor(
    private val skillExecutor: SkillExecutor,
) : Tool {

    override val name: String = NAME

    override val definition: ToolDefinition = ToolDefinition(
        name = name,
        description = "Invoke a specialized skill for complex tasks. " +
            "Check <available_skills> in the system prompt for available skill names and descriptions. " +
            "Use this when a user request matches a skill's description.",
        parametersSchema = """
        {
          "type": "object",
          "properties": {
            "skill_name": {
              "type": "string",
              "description": "The name (id) of the skill to invoke, as listed in <available_skills>"
            },
            "task": {
              "type": "string",
              "description": "A clear description of what the user wants to accomplish"
            }
          },
          "required": ["skill_name", "task"]
        }
        """.trimIndent(),
    )

    override val risk: ToolRisk = ToolRisk.LOW

    override val executionPolicy: ToolExecutionPolicy = ToolExecutionPolicy(
        requiresConnectivity = false,
        requiresUserApproval = false,
        hasSideEffects = false,
    )

    @Volatile
    var currentDepth: Int = 0

    override suspend fun execute(argumentsJson: String): ToolResult {
        return try {
            val obj = JSONObject(argumentsJson)
            val skillName = obj.optString("skill_name", "").trim()
            val task = obj.optString("task", "").trim()

            if (skillName.isEmpty()) {
                return ToolResult(ok = false, message = "Missing required parameter: skill_name")
            }
            if (task.isEmpty()) {
                return ToolResult(ok = false, message = "Missing required parameter: task")
            }

            skillExecutor.execute(skillName, task, depth = currentDepth)
        } catch (e: Exception) {
            ToolResult(ok = false, message = "Failed to invoke skill: ${e.message}")
        }
    }

    companion object {
        const val NAME = "use_skill"
    }
}
