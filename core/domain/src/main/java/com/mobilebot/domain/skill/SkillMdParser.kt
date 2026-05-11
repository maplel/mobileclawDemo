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
        val lines = yaml.lines()
        var i = 0

        var pendingListKey: String? = null
        var pendingListParent: String? = null
        val currentList = mutableListOf<String>()
        var currentMapKey: String? = null

        fun nestedMap(key: String): MutableMap<String, Any> {
            @Suppress("UNCHECKED_CAST")
            return result.getOrPut(key) { mutableMapOf<String, Any>() } as MutableMap<String, Any>
        }

        fun flushList() {
            val key = pendingListKey ?: return
            if (currentList.isNotEmpty()) {
                val parent = pendingListParent
                if (parent == null) {
                    result[key] = currentList.toList()
                } else {
                    nestedMap(parent)[key] = currentList.toList()
                }
            }
            pendingListKey = null
            pendingListParent = null
            currentList.clear()
        }

        while (i < lines.size) {
            val raw = lines[i]
            val stripped = raw.trimEnd()
            if (stripped.isBlank()) {
                i++
                continue
            }

            val indent = raw.takeWhile { it == ' ' }.length
            val trimmed = stripped.trimStart()

            if (trimmed.startsWith("- ")) {
                val value = trimmed.removePrefix("- ").trim().cleanYamlValue()
                if (value.isNotEmpty()) currentList.add(value)
                i++
                continue
            }

            flushList()

            val colonIdx = trimmed.indexOf(':')
            if (colonIdx < 0) {
                i++
                continue
            }

            val key = trimmed.substring(0, colonIdx).trim()
            val value = trimmed.substring(colonIdx + 1).trim()
            val parent = currentMapKey.takeIf { indent > 0 }

            when {
                value.isBlockScalarMarker() -> {
                    val blockIndent = indent
                    val blockLines = mutableListOf<String>()
                    i++
                    while (i < lines.size) {
                        val nextRaw = lines[i]
                        val nextIndent = nextRaw.takeWhile { it == ' ' }.length
                        if (nextRaw.isNotBlank() && nextIndent <= blockIndent) break
                        blockLines.add(nextRaw.trim())
                        i++
                    }
                    val text = if (value.startsWith("|")) {
                        blockLines.joinToString("\n").trim()
                    } else {
                        blockLines.joinToString(" ").replace(Regex("\\s+"), " ").trim()
                    }
                    if (parent == null) {
                        result[key] = text
                    } else {
                        nestedMap(parent)[key] = text
                    }
                    continue
                }

                value.isEmpty() -> {
                    pendingListKey = key
                    pendingListParent = parent
                    if (indent == 0) currentMapKey = key
                }

                else -> {
                    val cleanValue = value.cleanYamlValue()
                    if (parent == null) {
                        result[key] = cleanValue
                        if (indent == 0) currentMapKey = null
                    } else {
                        nestedMap(parent)[key] = cleanValue
                    }
                }
            }

            i++
        }

        flushList()
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
                references = fm.getStringList("references"),
                runtimeContext = fm.getStringList("runtime-context"),
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

    private fun String.cleanYamlValue(): String =
        removeSurrounding("\"").removeSurrounding("'")

    private fun String.isBlockScalarMarker(): Boolean =
        this == ">" || this == ">-" || this == "|" || this == "|-"

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
