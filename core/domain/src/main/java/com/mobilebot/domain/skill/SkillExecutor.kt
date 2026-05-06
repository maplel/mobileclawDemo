package com.mobilebot.domain.skill

import com.mobilebot.domain.agent.SubAgentRunner
import com.mobilebot.domain.tools.ToolRegistry
import com.mobilebot.model.ToolResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
        val bodyWithReferences = body + loadReferenceMaterials(entry)
        val bodyWithRuntimeContext = bodyWithReferences + buildRuntimeContext(entry.manifest)

        return when (entry.manifest.context) {
            SkillContext.FORK -> executeFork(entry, bodyWithRuntimeContext, task, depth)
            SkillContext.INLINE -> executeInline(entry, bodyWithRuntimeContext, task)
        }
    }

    private suspend fun loadReferenceMaterials(entry: SkillEntry): String {
        val references = entry.manifest.references
        if (references.isEmpty()) return ""

        return buildString {
            appendLine()
            appendLine("## Reference Materials")
            for (reference in references) {
                val path = resolveReferencePath(entry.contentPath, reference)
                val content = contentLoader.loadContent(path)
                appendLine()
                appendLine("### $reference")
                if (content.isNullOrBlank()) {
                    appendLine("Reference file could not be loaded from $path.")
                } else {
                    appendLine(content.trim())
                }
            }
        }
    }

    private fun buildRuntimeContext(manifest: SkillManifest): String {
        if ("current-time" !in manifest.runtimeContext) return ""

        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US)
        val now = formatter.format(Date())
        val minBookingTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(System.currentTimeMillis() + 30 * 60 * 1000))
        return buildString {
            appendLine()
            appendLine("## Runtime Context")
            appendLine("Current device time: $now")
            appendLine("Minimum booking time (current + 30min): $minBookingTime")
            appendLine("Use this timestamp to interpret relative dates such as today, tomorrow, tonight, later, and ASAP.")
            appendLine("Never schedule appointments, pickups, reminders, or deliveries in the past.")
            appendLine("All appointments must be at least 30 minutes after current device time. Any slot before $minBookingTime MUST be filtered out and NOT shown to the user.")
        }
    }

    private fun resolveReferencePath(contentPath: String, reference: String): String {
        val normalizedReference = reference.replace('\\', '/').trimStart('/')
        if (contentPath.startsWith("assets://")) {
            val assetPath = contentPath.removePrefix("assets://")
            val base = assetPath.substringBeforeLast('/', "")
            return "assets://$base/$normalizedReference"
        }
        val base = contentPath.substringBeforeLast('/', "")
        return if (base.isBlank()) normalizedReference else "$base/$normalizedReference"
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
        )
    }

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

            if (manifest.userConfirmationPoints.isNotEmpty()) {
                appendLine()
                appendLine("IMPORTANT: You MUST ask the user for confirmation before: ${manifest.userConfirmationPoints.joinToString(", ")}")
            }
        }
    }
}
