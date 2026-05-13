package com.mobilebot.domain.skill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledSkillJsonParserTest {
    @Test
    fun parsesValidBundledSkill() {
        val json =
            """
            {
              "id": "file.reference_skill",
              "name": "ReferenceSkill",
              "description": "Example bundled skill",
              "applicableTools": ["open_url"]
            }
            """.trimIndent()

        val skill = BundledSkillJsonParser.parse(json)
        assertNotNull(skill)
        assertEquals("file.reference_skill", skill!!.id)
        assertEquals("ReferenceSkill", skill.name)
        assertEquals(listOf("open_url"), skill.manifest.allowedTools)
        assertEquals(SkillSource.BUNDLED_ASSET, skill.manifest.source)
    }

    @Test
    fun parsesOptionalPromptFields() {
        val json =
            """
            {
              "id": "file.reference_skill",
              "name": "ReferenceSkill",
              "description": "Example bundled skill",
              "version": "2",
              "always": true,
              "promptSummary": "Summarize the task intent",
              "promptBody": "Use this skill when the user mentions a reference workflow.",
              "applicableTools": ["open_url"]
            }
            """.trimIndent()

        val skill = BundledSkillJsonParser.parse(json)
        assertNotNull(skill)
        assertEquals("2", skill!!.manifest.version)
        assertTrue(skill.manifest.always)
        assertEquals("Summarize the task intent", skill.manifest.promptSummary)
        assertEquals("Use this skill when the user mentions a reference workflow.", skill.manifest.promptBody)
    }

    @Test
    fun parsesAllowedToolsField() {
        val json =
            """
            {
              "id": "file.test",
              "name": "Test",
              "description": "test",
              "allowedTools": ["open_url", "send_sms"]
            }
            """.trimIndent()

        val skill = BundledSkillJsonParser.parse(json)
        assertNotNull(skill)
        assertEquals(listOf("open_url", "send_sms"), skill!!.manifest.allowedTools)
    }

    @Test
    fun parsesScenarioMetadata() {
        val json =
            """
            {
              "id": "scenario.pet_grooming",
              "name": "Pet grooming",
              "description": "Run the pet grooming scenario",
              "scenario": {
                "id": "pet-grooming",
                "displayMode": "fullscreen",
                "systemCapabilities": ["sms", "location"],
                "decisionPoints": ["confirm_schedule_change"],
                "timelineHints": ["pickup_confirmed", "home_confirmed"]
              }
            }
            """.trimIndent()

        val skill = BundledSkillJsonParser.parse(json)

        assertNotNull(skill)
        val scenario = skill!!.manifest.scenario
        assertNotNull(scenario)
        assertEquals("pet-grooming", scenario!!.scenarioId)
        assertEquals(ScenarioDisplayMode.FULLSCREEN, scenario.displayMode)
        assertEquals(listOf("sms", "location"), scenario.systemCapabilities)
        assertEquals(listOf("confirm_schedule_change"), scenario.decisionPoints)
        assertEquals(listOf("pickup_confirmed", "home_confirmed"), scenario.timelineHints)
    }
}
