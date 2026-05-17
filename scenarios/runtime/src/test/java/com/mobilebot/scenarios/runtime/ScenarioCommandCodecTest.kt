package com.mobilebot.scenarios.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenarioCommandCodecTest {
    @Test
    fun parsesValidCommandBatch() {
        val result = ScenarioCommandCodec.parse(
            """
            {
              "commands": [
                {
                  "type": "create_task",
                  "taskId": "task-1",
                  "title": "麒麟洗护",
                  "subtitle": "等待用户确认",
                  "status": "BLOCKED",
                  "conversations": [{"role": "AGENT", "text": "要改到 14:00 吗？"}],
                  "logs": [{"text": "收到宠物店短信。"}],
                  "participants": [{"id": "nt", "label": "NT", "displayName": "NT", "role": "agent"}],
                  "progress": {"label": "等待", "detail": "需要用户确认", "completed": 0, "total": 3},
                  "decision": {
                    "text": "是否改到 14:00？",
                    "actions": [{"label": "可以", "key": "accept"}, {"label": "不改了", "key": "decline"}]
                  }
                },
                {
                  "type": "send_sms",
                  "taskId": "task-1",
                  "to": "PetSmart",
                  "displayName": "PetSmart",
                  "message": "好的，14:00 准时到。"
                }
              ]
            }
            """.trimIndent(),
        )

        assertTrue(result.isOk)
        val commands = result.batch?.commands.orEmpty()
        assertEquals(2, commands.size)
        val create = commands[0] as ScenarioAgentCommand.CreateTask
        assertEquals("task-1", create.taskId)
        assertEquals(ScenarioSurfaceStatus.BLOCKED, create.seed.status)
        assertEquals(listOf("可以", "不改了"), create.seed.decision?.actions?.map { it.label })
        val sms = commands[1] as ScenarioAgentCommand.SendSms
        assertEquals("PetSmart", sms.to)
        assertEquals("好的，14:00 准时到。", sms.message)
    }

    @Test
    fun rejectsUnknownCommandType() {
        val result = ScenarioCommandCodec.parse(
            """{"commands":[{"type":"unknown","taskId":"task-1"}]}""",
        )

        assertFalse(result.isOk)
        assertTrue(result.error.orEmpty().contains("不支持"))
    }

    @Test
    fun rejectsMissingRequiredField() {
        val result = ScenarioCommandCodec.parse(
            """{"commands":[{"type":"send_sms","taskId":"task-1","to":"PetSmart"}]}""",
        )

        assertFalse(result.isOk)
        assertTrue(result.error.orEmpty().contains("message"))
    }

    @Test
    fun toolSchemaRequiresCreateTaskTitle() {
        val schema = ScenarioCommandCodec.toolParametersSchema()

        assertTrue(schema.contains("\"create_task\""))
        assertTrue(schema.contains("\"title\""))
    }

    @Test
    fun toolSchemaRequiresCompleteTaskSummary() {
        val schema = ScenarioCommandCodec.toolParametersSchema()

        assertTrue(schema.contains("\"complete_task\""))
        assertTrue(schema.contains("\"summary\""))
    }

    @Test
    fun toolSchemaRequiresStructuredParticipants() {
        val schema = ScenarioCommandCodec.toolParametersSchema()

        assertTrue(schema.contains("\"participants\""))
        assertTrue(schema.contains("\"participantsToAdd\""))
        assertTrue(schema.contains("\"displayName\""))
    }

    @Test
    fun rejectsStringParticipants() {
        val result = ScenarioCommandCodec.parse(
            """
            {
              "commands": [
                {
                  "type": "create_task",
                  "taskId": "task-1",
                  "title": "Coldchain",
                  "participants": ["courier-coldchain"]
                }
              ]
            }
            """.trimIndent(),
        )

        assertFalse(result.isOk)
        assertTrue(result.error.orEmpty().contains("participants"))
    }

    @Test
    fun createTaskSummaryHydratesVisibleSurface() {
        val result = ScenarioCommandCodec.parse(
            """
            {
              "commands": [
                {
                  "type": "create_task",
                  "taskId": "task-1",
                  "title": "Shopping",
                  "summary": "Call transcript produced a shopping task."
                }
              ]
            }
            """.trimIndent(),
        )

        assertTrue(result.isOk)
        val create = result.batch?.commands.orEmpty().single() as ScenarioAgentCommand.CreateTask
        assertEquals("Call transcript produced a shopping task.", create.seed.conversations.single().text)
        assertEquals("Call transcript produced a shopping task.", create.seed.logs.single().text)
    }

    @Test
    fun createTaskSubtitleHydratesVisibleSurfaceWhenSummaryIsMissing() {
        val result = ScenarioCommandCodec.parse(
            """
            {
              "commands": [
                {
                  "type": "create_task",
                  "taskId": "task-1",
                  "title": "Shopping",
                  "subtitle": "Call transcript produced a shopping task."
                }
              ]
            }
            """.trimIndent(),
        )

        assertTrue(result.isOk)
        val create = result.batch?.commands.orEmpty().single() as ScenarioAgentCommand.CreateTask
        assertEquals("Call transcript produced a shopping task.", create.seed.conversations.single().text)
        assertEquals("Call transcript produced a shopping task.", create.seed.logs.single().text)
    }

    @Test
    fun updateTaskSummaryHydratesVisibleSurface() {
        val result = ScenarioCommandCodec.parse(
            """
            {
              "commands": [
                {
                  "type": "update_task",
                  "taskId": "task-1",
                  "status": "DONE",
                  "summary": "Task no longer needs action."
                }
              ]
            }
            """.trimIndent(),
        )

        assertTrue(result.isOk)
        val update = result.batch?.commands.orEmpty().single() as ScenarioAgentCommand.UpdateTask
        assertTrue(update.update.conversations.isEmpty())
        assertEquals("Task no longer needs action.", update.update.logs.single().text)
        assertEquals("Task no longer needs action.", update.update.finalSummary)
    }

    @Test
    fun parsesEmptyCommandBatchForNoopTurns() {
        val result = ScenarioCommandCodec.parse("""{"commands":[]}""")

        assertTrue(result.isOk)
        assertTrue(result.batch?.commands.orEmpty().isEmpty())
    }

    @Test
    fun encodesRoundTrippableBatch() {
        val batch = ScenarioCommandBatch(
            listOf(
                ScenarioAgentCommand.WaitSms(
                    taskId = "task-1",
                    contact = "Driver",
                    reason = "等待确认接送时间",
                ),
                ScenarioAgentCommand.CompleteTask(
                    taskId = "task-1",
                    summary = "已完成。",
                ),
            ),
        )

        val parsed = ScenarioCommandCodec.parse(ScenarioCommandCodec.toJson(batch))

        assertTrue(parsed.isOk)
        assertEquals(batch.commands, parsed.batch?.commands)
    }
}
