package com.mobilebot.domain.skill

import com.mobilebot.domain.agent.SubAgentRunner
import com.mobilebot.domain.testdoubles.AllCapabilitiesProbe
import com.mobilebot.domain.tools.ToolRegistry
import com.mobilebot.model.StreamEvent
import com.mobilebot.model.ToolDefinition
import com.mobilebot.network.LlmClient
import com.mobilebot.network.LlmMessage
import com.mobilebot.network.LlmResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.inject.Provider

class SkillExecutorReferenceTest {
    @Test
    fun inlineExecutionAppendsReferenceMaterials() = runBlocking {
        val skillContent =
            """
            ---
            name: arrange-pet-store-service
            description: Pet store orchestration
            context: inline
            allowed-tools:
              - call_service
            references:
              - references/mock-pet-store-data.md
              - references/missing.md
            ---

            # Main Skill Body
            Use pet store services.
            """.trimIndent()
        val skill = SkillMdParser.parse(skillContent, SkillSource.BUNDLED_ASSET)!!
        val entry = SkillEntry(
            manifest = skill.manifest,
            contentPath = "assets://skills/md/arrange-pet-store-service/SKILL.md",
            source = SkillSource.BUNDLED_ASSET,
        )
        val registry = SkillRegistry().apply { register(entry) }
        val loader = MapSkillContentLoader(
            mapOf(
                entry.contentPath to skillContent,
                "assets://skills/md/arrange-pet-store-service/references/mock-pet-store-data.md" to "Mock locations and slots",
            ),
        )
        val executor = SkillExecutor(
            skillRegistry = registry,
            contentLoader = loader,
            eligibilityChecker = SkillEligibilityChecker(AllCapabilitiesProbe()),
            subAgentRunner = SubAgentRunner(FakeLlmClient(), Provider<ToolRegistry> { error("not used") }),
            toolRegistryProvider = Provider<ToolRegistry> { error("not used") },
        )

        val result = executor.execute("arrange-pet-store-service", "book grooming")

        assertTrue(result.ok)
        assertTrue(result.message.contains("# Main Skill Body"))
        assertTrue(result.message.contains("## Reference Materials"))
        assertTrue(result.message.contains("Mock locations and slots"))
        assertTrue(result.message.contains("Reference file could not be loaded"))
        assertTrue(result.message.contains("Allowed tools for this skill: call_service"))
    }
}

private class MapSkillContentLoader(
    private val content: Map<String, String>,
) : SkillContentLoader {
    override suspend fun loadContent(entry: SkillEntry): String? = loadContent(entry.contentPath)

    override suspend fun loadContent(path: String): String? = content[path]
}

private class FakeLlmClient : LlmClient {
    override var defaultModel: String = "fake"

    override suspend fun chat(
        messages: List<LlmMessage>,
        tools: List<ToolDefinition>?,
        model: String?,
        maxTokens: Int,
    ): LlmResponse = LlmResponse(content = "unused", toolCalls = emptyList(), finishReason = "stop")

    override fun chatStream(
        messages: List<LlmMessage>,
        tools: List<ToolDefinition>?,
        model: String?,
        maxTokens: Int,
    ): Flow<StreamEvent> = emptyFlow()
}
