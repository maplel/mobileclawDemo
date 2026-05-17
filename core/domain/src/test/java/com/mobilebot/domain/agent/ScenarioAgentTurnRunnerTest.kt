package com.mobilebot.domain.agent

import com.mobilebot.domain.LlmConfigurator
import com.mobilebot.domain.repository.SessionMeta
import com.mobilebot.domain.repository.SessionRepository
import com.mobilebot.domain.tools.EmitScenarioCommandsTool
import com.mobilebot.model.ChatMessage
import com.mobilebot.model.ChatRole
import com.mobilebot.model.StreamEvent
import com.mobilebot.model.ToolDefinition
import com.mobilebot.network.LlmClient
import com.mobilebot.network.LlmMessage
import com.mobilebot.network.LlmResponse
import com.mobilebot.network.LlmToolCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenarioAgentTurnRunnerTest {
    @Test
    fun parsesCommandsFromToolCallAndPersistsMessages() = runBlocking {
        val sessions = MemorySessionRepository()
        val llm = StubLlmClient(
            LlmResponse(
                content = "",
                toolCalls = listOf(
                    LlmToolCall(
                        id = "call-1",
                        name = EmitScenarioCommandsTool.NAME,
                        argumentsJson = """
                            {
                              "commands": [
                                {
                                  "type": "ask_user",
                                  "taskId": "pet-task",
                                  "decision": {
                                    "text": "是否改到 14:00？",
                                    "actions": [{"label": "可以", "key": "accept"}]
                                  }
                                }
                              ]
                            }
                        """.trimIndent(),
                    ),
                ),
                finishReason = "tool_calls",
            ),
        )
        val runner = runner(llm, sessions)

        val result = runner.run(baseInput())

        assertTrue(result.isOk)
        assertEquals(1, result.commands.size)
        assertEquals("pet-task", result.commands.single().taskId)
        val persisted = sessions.getMessages("mobile:session-1")
        assertEquals(ChatRole.User, persisted[0].role)
        assertEquals(ChatRole.Assistant, persisted[1].role)
        assertEquals(ChatRole.Tool, persisted[2].role)
    }

    @Test
    fun rejectsInvalidToolArguments() = runBlocking {
        val result = runner(
            StubLlmClient(
                LlmResponse(
                    content = "",
                    toolCalls = listOf(
                        LlmToolCall(
                            id = "call-1",
                            name = EmitScenarioCommandsTool.NAME,
                            argumentsJson = """{"commands":[{"type":"send_sms","taskId":"pet-task","to":"Driver"}]}""",
                        ),
                    ),
                    finishReason = "tool_calls",
                ),
            ),
        ).run(baseInput())

        assertFalse(result.isOk)
        assertTrue(result.error.orEmpty().contains("message"))
    }

    @Test
    fun rejectsDuplicateCommandsInOneTurn() = runBlocking {
        val command = """{"type":"switch_task","taskId":"pet-task"}"""
        val result = runner(
            StubLlmClient(
                LlmResponse(
                    content = "",
                    toolCalls = listOf(
                        LlmToolCall(
                            id = "call-1",
                            name = EmitScenarioCommandsTool.NAME,
                            argumentsJson = """{"commands":[$command,$command]}""",
                        ),
                    ),
                    finishReason = "tool_calls",
                ),
            ),
        ).run(baseInput())

        assertFalse(result.isOk)
        assertTrue(result.error.orEmpty().contains("重复"))
    }

    @Test
    fun acceptsEmptyCommandBatchFromToolCall() = runBlocking {
        val result = runner(
            StubLlmClient(
                LlmResponse(
                    content = "",
                    toolCalls = listOf(
                        LlmToolCall(
                            id = "call-1",
                            name = EmitScenarioCommandsTool.NAME,
                            argumentsJson = """{"commands":[]}""",
                        ),
                    ),
                    finishReason = "tool_calls",
                ),
            ),
        ).run(baseInput())

        assertTrue(result.isOk)
        assertTrue(result.commands.isEmpty())
    }

    @Test
    fun rejectsMultipleOutputToolCallsInOneTurn() = runBlocking {
        val command = """{"commands":[{"type":"switch_task","taskId":"pet-task"}]}"""
        val result = runner(
            StubLlmClient(
                LlmResponse(
                    content = "",
                    toolCalls = listOf(
                        LlmToolCall(
                            id = "call-1",
                            name = EmitScenarioCommandsTool.NAME,
                            argumentsJson = command,
                        ),
                        LlmToolCall(
                            id = "call-2",
                            name = EmitScenarioCommandsTool.NAME,
                            argumentsJson = command,
                        ),
                    ),
                    finishReason = "tool_calls",
                ),
            ),
        ).run(baseInput())

        assertFalse(result.isOk)
        assertTrue(result.error.orEmpty().contains("只能调用一次"))
    }

    @Test
    fun parsesJsonFromAssistantContentWhenToolCallIsMissing() = runBlocking {
        val result = runner(
            StubLlmClient(
                LlmResponse(
                    content = """{"commands":[{"type":"complete_task","taskId":"pet-task","summary":"已完成。"}]}""",
                    toolCalls = emptyList(),
                    finishReason = "stop",
                ),
            ),
        ).run(baseInput())

        assertTrue(result.isOk)
        assertEquals("pet-task", result.commands.single().taskId)
    }

    @Test
    fun includesRawUserDecisionPromptAndVisibleActionsWithoutNormalizedIntent() = runBlocking {
        val llm = StubLlmClient(
            LlmResponse(
                content = "",
                toolCalls = listOf(
                    LlmToolCall(
                        id = "call-1",
                        name = EmitScenarioCommandsTool.NAME,
                        argumentsJson = """{"commands":[{"type":"switch_task","taskId":"pet-task"}]}""",
                    ),
                ),
                finishReason = "tool_calls",
            ),
        )

        runner(llm).run(
            baseInput(
                turnType = "user_decision",
                userInput = "行，就这么办",
                presentedActions = listOf(AgentDecisionAction("可以", ACCEPT.command)),
            ),
        )

        val prompt = llm.requests.single().last { it.role == "user" }.content.orEmpty()
        assertTrue(prompt.contains("normalizedIntent:"))
        assertTrue(prompt.contains("(none)"))
        assertTrue(prompt.contains("userInput:\n行，就这么办"))
        assertTrue(prompt.contains("- 可以: USER_INTENT:pet.accept"))
    }

    @Test
    fun excludesTimelineQueueFromPrompt() = runBlocking {
        val llm = StubLlmClient(
            LlmResponse(
                content = "",
                toolCalls = listOf(
                    LlmToolCall(
                        id = "call-1",
                        name = EmitScenarioCommandsTool.NAME,
                        argumentsJson = """{"commands":[{"type":"switch_task","taskId":"pet-task"}]}""",
                    ),
                ),
                finishReason = "tool_calls",
            ),
        )

        runner(llm).run(
            baseInput(
                allTaskSnapshots = "id: task-a\nstatus: RUNNING",
                recentToolResults = "pet-task: 13:05 sent sms",
            ),
        )

        val prompt = llm.requests.single().last { it.role == "user" }.content.orEmpty()
        assertTrue(prompt.contains("allTasks:"))
        assertTrue(prompt.contains("id: task-a"))
        assertFalse(prompt.contains("timelineQueue:"))
        assertFalse(prompt.contains("upcoming:"))
        assertFalse(prompt.contains("currentEvent: ella-call"))
        assertFalse(prompt.contains("ella-call-ended"))
        assertTrue(prompt.contains("recentToolResults:"))
        assertTrue(prompt.contains("pet-task: 13:05 sent sms"))
    }

    @Test
    fun keepsSessionHistoriesIsolatedBySessionId() = runBlocking {
        val sessions = MemorySessionRepository()
        val llm = StubLlmClient(
            LlmResponse(
                content = "",
                toolCalls = listOf(
                    LlmToolCall(
                        id = "call-1",
                        name = EmitScenarioCommandsTool.NAME,
                        argumentsJson = """{"commands":[{"type":"switch_task","taskId":"task"}]}""",
                    ),
                ),
                finishReason = "tool_calls",
            ),
        )
        val runner = runner(llm, sessions)

        runner.run(baseInput(sessionId = "session-a", taskId = "task-a"))
        runner.run(baseInput(sessionId = "session-b", taskId = "task-b"))

        val sessionA = sessions.getMessages("mobile:session-a")
        val sessionB = sessions.getMessages("mobile:session-b")
        assertEquals(3, sessionA.size)
        assertEquals(3, sessionB.size)
        assertTrue(sessionA.first().content.contains("task-a"))
        assertFalse(sessionA.first().content.contains("task-b"))
        assertTrue(sessionB.first().content.contains("task-b"))
        assertFalse(sessionB.first().content.contains("task-a"))
    }

    private fun runner(
        llm: LlmClient,
        sessions: SessionRepository = MemorySessionRepository(),
    ): ScenarioAgentTurnRunner =
        ScenarioAgentTurnRunner(
            llm = llm,
            llmConfigurator = LlmConfigurator {},
            sessions = sessions,
            outputTool = EmitScenarioCommandsTool(),
        )

    private fun baseInput(
        sessionId: String = "session-1",
        taskId: String? = "pet-task",
        turnType: String = "system_event",
        userInput: String = "",
        normalizedIntent: AgentDecisionIntent? = null,
        presentedActions: List<AgentDecisionAction> = emptyList(),
        allTaskSnapshots: String = "",
        recentToolResults: String = "",
    ): ScenarioAgentTurnInput =
        ScenarioAgentTurnInput(
            sessionId = sessionId,
            scenarioId = "one-hour",
            skillName = "pet-grooming",
            turnType = turnType,
            taskId = taskId,
            eventFact = "收到宠物店短信。",
            currentTaskSnapshot = "尚未创建任务。",
            allTaskSnapshots = allTaskSnapshots,
            recentToolResults = recentToolResults,
            userInput = userInput,
            normalizedIntent = normalizedIntent,
            presentedActions = presentedActions,
            memoryDigest = "Y 周末上午不喜被打扰。",
            skillInstruction = "必要时询问用户。",
        )

    private companion object {
        val ACCEPT = AgentDecisionIntent(
            id = "pet.accept",
            displayLabel = "可以",
            meaning = "User accepts the proposed slot.",
        )
    }
}

private class StubLlmClient(
    private val response: LlmResponse,
) : LlmClient {
    val requests = mutableListOf<List<LlmMessage>>()
    override var defaultModel: String = "stub"

    override suspend fun chat(
        messages: List<LlmMessage>,
        tools: List<ToolDefinition>?,
        model: String?,
        maxTokens: Int,
    ): LlmResponse {
        requests += messages
        return response
    }

    override fun chatStream(
        messages: List<LlmMessage>,
        tools: List<ToolDefinition>?,
        model: String?,
        maxTokens: Int,
    ): Flow<StreamEvent> = emptyFlow()
}

private class MemorySessionRepository : SessionRepository {
    private val messages = linkedMapOf<String, MutableList<ChatMessage>>()

    override suspend fun listSessionKeys(): List<String> = messages.keys.toList()

    override suspend fun listSessionMetas(): List<SessionMeta> =
        messages.keys.map { SessionMeta(it, 0L, 0L) }

    override suspend fun getMessages(sessionKey: String): List<ChatMessage> =
        messages[sessionKey].orEmpty()

    override suspend fun getFirstUserContent(sessionKey: String): String? =
        messages[sessionKey]?.firstOrNull { it.role == ChatRole.User }?.content

    override suspend fun replaceMessages(
        sessionKey: String,
        messages: List<ChatMessage>,
    ) {
        this.messages[sessionKey] = messages.toMutableList()
    }

    override suspend fun appendUserMessage(
        sessionKey: String,
        content: String,
    ): List<ChatMessage> {
        val list = messages.getOrPut(sessionKey) { mutableListOf() }
        list += ChatMessage(ChatRole.User, content)
        return list.toList()
    }

    override suspend fun appendAssistantMessage(
        sessionKey: String,
        content: String,
        toolCallId: String?,
        toolName: String?,
        toolCalls: String?,
    ) {
        messages.getOrPut(sessionKey) { mutableListOf() } +=
            ChatMessage(ChatRole.Assistant, content, toolCallId, toolName, toolCalls)
    }

    override suspend fun appendToolMessage(
        sessionKey: String,
        content: String,
        toolCallId: String,
        toolName: String,
    ) {
        messages.getOrPut(sessionKey) { mutableListOf() } +=
            ChatMessage(ChatRole.Tool, content, toolCallId, toolName)
    }

    override suspend fun ensureSession(sessionKey: String) {
        messages.getOrPut(sessionKey) { mutableListOf() }
    }

    override suspend fun deleteSession(sessionKey: String) {
        messages.remove(sessionKey)
    }
}
