package com.mobilebot.domain.agent

import com.mobilebot.domain.skill.SkillRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPromptBuilderTest {
    @Test
    fun promptStatesAutonomyPolicy() {
        val prompt = SystemPromptBuilder.build(SkillRegistry())

        assertTrue(prompt.contains("low-interruption policy"))
        assertTrue(prompt.contains("Planning is visible but non-blocking"))
        assertTrue(prompt.contains("Pause for the user only when an active Skill declares a decision point"))
    }
}
