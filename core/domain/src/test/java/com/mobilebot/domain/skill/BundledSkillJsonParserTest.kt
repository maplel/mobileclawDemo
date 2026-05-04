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
              "id": "file.demo_skill",
              "name": "DemoSkill",
              "description": "Example bundled skill",
              "applicableTools": ["open_url"]
            }
            """.trimIndent()

        val skill = BundledSkillJsonParser.parse(json)
        assertNotNull(skill)
        assertEquals("file.demo_skill", skill!!.id)
        assertEquals("DemoSkill", skill.name)
        assertEquals(listOf("open_url"), skill.manifest.allowedTools)
        assertEquals(SkillSource.BUNDLED_ASSET, skill.manifest.source)
    }

    @Test
    fun parsesOptionalPromptFields() {
        val json =
            """
            {
              "id": "file.demo_skill",
              "name": "DemoSkill",
              "description": "Example bundled skill",
              "version": "2",
              "always": true,
              "promptSummary": "Summarize the demo intent",
              "promptBody": "Use this skill when the user mentions demo.",
              "applicableTools": ["open_url"]
            }
            """.trimIndent()

        val skill = BundledSkillJsonParser.parse(json)
        assertNotNull(skill)
        assertEquals("2", skill!!.manifest.version)
        assertTrue(skill.manifest.always)
        assertEquals("Summarize the demo intent", skill.manifest.promptSummary)
        assertEquals("Use this skill when the user mentions demo.", skill.manifest.promptBody)
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
}
