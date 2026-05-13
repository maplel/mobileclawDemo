package com.mobilebot.domain.skill

import com.mobilebot.domain.tools.ToolRisk

/**
 * Parses SKILL.md files with YAML frontmatter + Markdown body.
 *
 * Format:
 * ```
 * ---
 * name: dining
 * description: Search restaurants...
 * category: food
 * allowed-tools:
 *   - call_service
 *   - get_current_location
 * context: inline
 * risk: medium
 * ---
 * ## Markdown body (promptBody)
 * ```
 */
object SkillMdParser {

    data class ParseResult(
        val frontmatter: Map<String, Any>,
        val body: String,
    )

    fun parse(content: String, source: SkillSource): FileBackedSkill? {
        val result = splitFrontmatterAndBody(content) ?: return null
        return buildSkill(result.frontmatter, result.body, source)
    }

    fun splitFrontmatterAndBody(content: String): ParseResult? {
        val trimmed = content.trimStart()
        if (!trimmed.startsWith("---")) return null

        val endIndex = trimmed.indexOf("---", 3)
        if (endIndex < 0) return null

        val yamlBlock = trimmed.substring(3, endIndex).trim()
        val body = trimmed.substring(endIndex + 3).trim()
        val frontmatter = parseYaml(yamlBlock)

        return ParseResult(frontmatter, body)
    }

    private fun parseYaml(yaml: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        var currentKey: String? = null
        val currentList = mutableListOf<String>()

        for (line in yaml.lines()) {
            val stripped = line.trimEnd()
            if (stripped.isBlank()) continue

            if (stripped.startsWith("  - ") || stripped.startsWith("    - ")) {
                val value = stripped.trimStart().removePrefix("- ").trim()
                if (value.isNotEmpty()) currentList.add(value)
                continue
            }

            if (currentKey != null && currentList.isNotEmpty()) {
                result[currentKey] = currentList.toList()
                currentList.clear()
            }
            currentKey = null

            val colonIdx = stripped.indexOf(':')
            if (colonIdx < 0) continue

            val key = stripped.substring(0, colonIdx).trim()
            val value = stripped.substring(colonIdx + 1).trim()

            if (value.isEmpty()) {
                currentKey = key
            } else {
                result[key] = value.removeSurrounding("\"").removeSurrounding("'")
            }
        }

        if (currentKey != null && currentList.isNotEmpty()) {
            result[currentKey] = currentList.toList()
        }

        return result
    }

    private fun buildSkill(
        fm: Map<String, Any>,
        body: String,
        source: SkillSource,
    ): FileBackedSkill? {
        val name = fm.getString("name") ?: return null
        val description = fm.getString("description") ?: return null
        val id = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

        return FileBackedSkill(
            manifest = SkillManifest(
                id = id,
                name = name,
                description = description,
                version = fm.getString("version") ?: "1",
                source = source,
                category = fm.getString("category"),
                allowedTools = fm.getStringList("allowed-tools"),
                context = SkillContext.fromString(fm.getString("context")),
                effort = SkillEffort.fromString(fm.getString("effort")),
                risk = parseRisk(fm.getString("risk")),
                requires = parseRequirements(fm),
                conditions = parseConditions(fm),
                composesSkills = fm.getStringList("composes-skills"),
                userConfirmationPoints = fm.getStringList("user-confirmation-points"),
                requiredServices = fm.getStringList("required-services"),
                scenario = parseScenario(fm),
                always = fm.getString("always")?.toBooleanStrictOrNull() ?: false,
                disableModelInvocation = fm.getString("disable-model-invocation")?.toBooleanStrictOrNull() ?: false,
                promptSummary = fm.getString("prompt-summary") ?: description,
                promptBody = body.ifBlank { null },
            ),
        )
    }

    private fun parseRisk(value: String?): ToolRisk =
        when (value?.uppercase()?.trim()) {
            "MEDIUM" -> ToolRisk.MEDIUM
            "HIGH" -> ToolRisk.HIGH
            "CRITICAL" -> ToolRisk.CRITICAL
            else -> ToolRisk.LOW
        }

    private fun parseRequirements(fm: Map<String, Any>): SkillRequirements {
        val reqMap = fm.getNestedMap("requires")
        return SkillRequirements(
            permissions = reqMap.getStringList("permissions"),
            connectivity = reqMap.getString("connectivity")?.toBooleanStrictOrNull() ?: false,
            apps = reqMap.getStringList("apps"),
            minApi = reqMap.getString("min-api")?.toIntOrNull() ?: 0,
        )
    }

    private fun parseConditions(fm: Map<String, Any>): SkillConditions? {
        val condMap = fm.getNestedMap("conditions")
        if (condMap.isEmpty()) return null
        val time = condMap.getString("time")
        val locationType = condMap.getString("location-type")
        val deviceState = condMap.getString("device-state")
        if (time == null && locationType == null && deviceState == null) return null
        return SkillConditions(time = time, locationType = locationType, deviceState = deviceState)
    }

    private fun parseScenario(fm: Map<String, Any>): ScenarioSkillSpec? {
        val scenarioId = fm.getString("scenario-id")
            ?: fm.getString("scenarioId")
            ?: return null
        return ScenarioSkillSpec(
            scenarioId = scenarioId,
            displayMode = ScenarioDisplayMode.fromString(fm.getString("display-mode") ?: fm.getString("displayMode")),
            systemCapabilities = fm.getStringList("system-capabilities").ifEmpty {
                fm.getStringList("systemCapabilities")
            },
            decisionPoints = fm.getStringList("decision-points").ifEmpty {
                fm.getStringList("decisionPoints")
            },
            timelineHints = fm.getStringList("timeline-hints").ifEmpty {
                fm.getStringList("timelineHints")
            },
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.getString(key: String): String? =
        (this[key] as? String)?.ifBlank { null }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.getStringList(key: String): List<String> =
        (this[key] as? List<*>)?.mapNotNull { (it as? String)?.ifBlank { null } } ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.getNestedMap(key: String): Map<String, Any> =
        (this[key] as? Map<String, Any>) ?: emptyMap()
}
