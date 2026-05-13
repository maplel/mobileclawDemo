package com.mobilebot.domain.skill

import com.mobilebot.domain.agent.SubAgentRunner
import com.mobilebot.domain.tools.ToolRegistry
import com.mobilebot.model.ToolResult
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Executes a skill after SkillTool routes to it.
 *
 * - **inline**: Injects the skill prompt into the current agent context
 *   and returns the guidance for the model to follow.
 * - **fork**: Delegates to SubAgentRunner for independent execution
 *   with its own tool_calls loop and token budget.
 */
@Singleton
class SkillExecutor @Inject constructor(
    private val skillRegistry: SkillRegistry,
    private val contentLoader: SkillContentLoader,
    private val eligibilityChecker: SkillEligibilityChecker,
    private val subAgentRunner: SubAgentRunner,
    private val toolRegistryProvider: Provider<ToolRegistry>,
) {

    suspend fun execute(skillName: String, task: String, depth: Int = 0): ToolResult {
        val entry = skillRegistry.findById(skillName)
            ?: return ToolResult(ok = false, message = "Skill '$skillName' not found. Check <available_skills> for valid skill names.")

        if (entry.manifest.disableModelInvocation) {
            return ToolResult(ok = false, message = "Skill '$skillName' cannot be invoked by the model.")
        }

        val eligResult = eligibilityChecker.check(entry)
        if (!eligResult.eligible) {
            return ToolResult(ok = false, message = "Skill '$skillName' is not available: ${eligResult.reason}")
        }

        val content = contentLoader.loadContent(entry)
        val body = if (!content.isNullOrBlank()) {
            val parsed = SkillMdParser.splitFrontmatterAndBody(content)
            parsed?.body ?: content
        } else {
            entry.manifest.promptBody ?: entry.manifest.description
        }

        return when (entry.manifest.context) {
            SkillContext.FORK -> executeFork(entry, body, task, depth)
            SkillContext.INLINE -> executeInline(entry, body, task)
        }
    }

    private fun executeInline(entry: SkillEntry, body: String, task: String): ToolResult {
        val guidance = buildSkillPrompt(entry.manifest, body, task)

        val allowedToolsHint = entry.manifest.effectiveAllowedTools.let { tools ->
            if (tools.isNotEmpty()) "\n\nAllowed tools for this skill: ${tools.joinToString(", ")}"
            else ""
        }

        val confirmHint = entry.manifest.userConfirmationPoints.let { points ->
            if (points.isNotEmpty()) "\n\nUser confirmation required before: ${points.joinToString(", ")}"
            else ""
        }

        return ToolResult(
            ok = true,
            message = guidance + allowedToolsHint + confirmHint,
            dataJson = buildSkillMetadata(entry.manifest),
        )
    }

    private suspend fun executeFork(
        entry: SkillEntry,
        body: String,
        task: String,
        depth: Int,
    ): ToolResult {
        val prompt = buildSkillPrompt(entry.manifest, body, task)

        val registry = toolRegistryProvider.get()
        val subTools = entry.manifest.effectiveAllowedTools.let { allowed ->
            if (allowed.isNotEmpty()) {
                registry.definitionsForSkill(allowed.toSet())
            } else {
                registry.definitionsForLlm()
            }
        }

        val maxTurns = when (entry.manifest.effort) {
            SkillEffort.LOW -> 5
            SkillEffort.HIGH -> 20
            SkillEffort.MEDIUM -> 15
        }

        val result = subAgentRunner.run(
            systemPrompt = prompt,
            tools = subTools,
            maxTurns = maxTurns,
            effort = entry.manifest.effort.name.lowercase(),
            depth = depth,
        )

        return ToolResult(
            ok = result.success,
            message = result.summary,
            dataJson = buildSkillMetadata(entry.manifest),
        )
    }

    private fun buildSkillMetadata(manifest: SkillManifest): String =
        JSONObject()
            .put("skillId", manifest.id)
            .put("skillName", manifest.name)
            .put("context", manifest.context.name.lowercase())
            .put("allowedTools", JSONArray(manifest.effectiveAllowedTools))
            .put("allowSkillSwitching", manifest.composesSkills.isNotEmpty())
            .toString()

    private fun buildSkillPrompt(manifest: SkillManifest, body: String, task: String): String {
        return buildString {
            appendLine("=== Skill: ${manifest.name} ===")
            appendLine("Task: $task")
            appendLine()
            appendLine(body)

            if (manifest.composesSkills.isNotEmpty()) {
                appendLine()
                appendLine("This skill can orchestrate sub-skills: ${manifest.composesSkills.joinToString(", ")}")
                appendLine("Use the `use_skill` tool to invoke each sub-skill as needed.")
            }

            manifest.scenario?.let { scenario ->
                appendLine()
                appendLine("Scenario skill contract:")
                appendLine("- scenarioId: ${scenario.scenarioId}")
                appendLine("- displayMode: ${scenario.displayMode.value}")
                if (scenario.systemCapabilities.isNotEmpty()) {
                    appendLine("- systemCapabilities: ${scenario.systemCapabilities.joinToString(", ")}")
                }
                if (scenario.timelineHints.isNotEmpty()) {
                    appendLine("- timelineHints: ${scenario.timelineHints.joinToString(", ")}")
                }
                if (scenario.decisionPoints.isNotEmpty()) {
                    appendLine("- decisionPoints: ${scenario.decisionPoints.joinToString(", ")}")
                    appendLine("Pause for the user only at those declared decision points.")
                } else {
                    appendLine("- decisionPoints: none")
                    appendLine("Continue autonomously and avoid user interruption unless the task cannot proceed.")
                }
                appendLine("Use `system_search_contacts` for contact lookup. Use `system_send_sms` followed by `system_wait_for_sms` for SMS conversations. Use `device_system` for phone, sensor, call, location, social, memory, and service capabilities that do not have a dedicated tool.")
            }

            if (manifest.userConfirmationPoints.isNotEmpty()) {
                appendLine()
                appendLine("IMPORTANT: You MUST ask the user for confirmation before: ${manifest.userConfirmationPoints.joinToString(", ")}")
            }
        }
    }
}
