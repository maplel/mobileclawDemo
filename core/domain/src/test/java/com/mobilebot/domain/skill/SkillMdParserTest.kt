package com.mobilebot.domain.skill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SkillMdParserTest {
    @Test
    fun parsesScenarioSkillMetadata() {
        val markdown = """
            ---
            name: pet-grooming
            description: "Pet grooming scenario"
            category: scenario
            allowed-tools:
              - device_system
            scenario-id: pet-grooming
            display-mode: fullscreen
            system-capabilities:
              - sms
              - location
            decision-points:
              - confirm_schedule_change
            timeline-hints:
              - pickup_confirmed
              - home_confirmed
            ---

            ## Body
        """.trimIndent()

        val skill = SkillMdParser.parse(markdown, SkillSource.BUNDLED_ASSET)

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
