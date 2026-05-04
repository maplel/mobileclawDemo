package com.mobilebot.domain.skill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillMdParserTest {
    @Test
    fun parsesBlockScalarDescriptionNestedRequirementsAndReferences() {
        val content =
            """
            ---
            name: arrange-pet-store-service
            description: >-
              编排宠物店洗护预约。
              不用于泛泛宠物知识问答。
            category: life-service
            allowed-tools:
              - call_service
              - query_calendar
            requires:
              connectivity: true
              permissions:
                - android.permission.ACCESS_FINE_LOCATION
            references:
              - references/mock-pet-store-data.md
              - references/decision-nodes.md
            ---

            # Body
            """.trimIndent()

        val skill = SkillMdParser.parse(content, SkillSource.BUNDLED_ASSET)

        requireNotNull(skill)
        assertEquals("arrange-pet-store-service", skill.manifest.id)
        assertEquals("编排宠物店洗护预约。 不用于泛泛宠物知识问答。", skill.manifest.description)
        assertEquals(listOf("call_service", "query_calendar"), skill.manifest.allowedTools)
        assertTrue(skill.manifest.requires.connectivity)
        assertEquals(listOf("android.permission.ACCESS_FINE_LOCATION"), skill.manifest.requires.permissions)
        assertEquals(
            listOf("references/mock-pet-store-data.md", "references/decision-nodes.md"),
            skill.manifest.references,
        )
    }
}
