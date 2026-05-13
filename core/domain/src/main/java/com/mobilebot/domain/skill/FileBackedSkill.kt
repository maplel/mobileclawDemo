package com.mobilebot.domain.skill

/**
 * Parser for bundled JSON skill definitions (legacy format).
 * Files under assets/skills/bundled and assets/skills/scenarios.
 */
object BundledSkillJsonParser {

    fun parse(json: String): FileBackedSkill? {
        return runCatching {
            val o = org.json.JSONObject(json)
            val id = o.getString("id").trim()
            val name = o.getString("name").trim()
            val description = o.getString("description").trim()
            if (id.isEmpty() || name.isEmpty()) return null
            val version = o.optString("version", "1").trim().ifEmpty { "1" }
            val always = o.optBoolean("always", false)
            val promptSummary = o.optString("promptSummary", description).trim().ifEmpty { description }
            val promptBody = o.optString("promptBody", "").trim().ifEmpty { null }

            val toolsArr = o.optJSONArray("applicableTools")
                ?: o.optJSONArray("allowedTools")
            val tools = toolsArr?.let { parseStringArray(it) } ?: emptyList()

            val extras = parseExtendedFields(o)

            FileBackedSkill(
                manifest = SkillManifest(
                    id = id,
                    name = name,
                    description = description.ifEmpty { name },
                    version = version,
                    source = SkillSource.BUNDLED_ASSET,
                    allowedTools = tools,
                    always = always,
                    promptSummary = promptSummary,
                    promptBody = promptBody,
                    category = extras.category,
                    requiredServices = extras.requiredServices,
                    composesSkills = extras.composesSkills,
                    userConfirmationPoints = extras.userConfirmationPoints,
                    scenario = extras.scenario,
                ),
            )
        }.getOrNull()
    }

    private data class ExtendedFields(
        val category: String? = null,
        val requiredServices: List<String> = emptyList(),
        val composesSkills: List<String> = emptyList(),
        val userConfirmationPoints: List<String> = emptyList(),
        val scenario: ScenarioSkillSpec? = null,
    )

    private fun parseExtendedFields(o: org.json.JSONObject): ExtendedFields {
        return try {
            ExtendedFields(
                category = o.optString("category", "").trim().ifEmpty { null },
                requiredServices = parseStringArrayFromObj(o, "requiredServices"),
                composesSkills = parseStringArrayFromObj(o, "composesSkills"),
                userConfirmationPoints = parseStringArrayFromObj(o, "userConfirmationPoints"),
                scenario = parseScenario(o),
            )
        } catch (_: Exception) {
            ExtendedFields()
        }
    }

    private fun parseScenario(o: org.json.JSONObject): ScenarioSkillSpec? {
        val scenario = o.optJSONObject("scenario")
        val scenarioId = o.optString("scenarioId", "").trim()
            .ifEmpty { scenario?.optString("id", "")?.trim().orEmpty() }
            .ifEmpty { return null }
        val displayMode = o.optString("displayMode", "").trim()
            .ifEmpty { scenario?.optString("displayMode", "")?.trim().orEmpty() }
        return ScenarioSkillSpec(
            scenarioId = scenarioId,
            displayMode = ScenarioDisplayMode.fromString(displayMode),
            systemCapabilities = parseStringArrayFromObj(o, "systemCapabilities").ifEmpty {
                scenario?.let { parseStringArrayFromObj(it, "systemCapabilities") } ?: emptyList()
            },
            decisionPoints = parseStringArrayFromObj(o, "decisionPoints").ifEmpty {
                scenario?.let { parseStringArrayFromObj(it, "decisionPoints") } ?: emptyList()
            },
            timelineHints = parseStringArrayFromObj(o, "timelineHints").ifEmpty {
                scenario?.let { parseStringArrayFromObj(it, "timelineHints") } ?: emptyList()
            },
        )
    }

    private fun parseStringArray(arr: org.json.JSONArray): List<String> = buildList {
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "").trim()
            if (s.isNotEmpty()) add(s)
        }
    }

    private fun parseStringArrayFromObj(obj: org.json.JSONObject, key: String): List<String> {
        val arr = obj.optJSONArray(key) ?: return emptyList()
        return parseStringArray(arr)
    }
}
