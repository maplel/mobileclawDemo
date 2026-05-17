package com.mobilebot.scenarios.runtime

import org.json.JSONArray
import org.json.JSONObject

sealed interface ScenarioAgentCommand {
    val taskId: String

    data class CreateTask(
        val seed: ScenarioTaskSeed,
    ) : ScenarioAgentCommand {
        override val taskId: String = seed.taskId
    }

    data class UpdateTask(
        val update: ScenarioTaskUpdate,
    ) : ScenarioAgentCommand {
        override val taskId: String = update.taskId
    }

    data class SendSms(
        override val taskId: String,
        val to: String,
        val message: String,
        val displayName: String? = null,
    ) : ScenarioAgentCommand

    data class WaitSms(
        override val taskId: String,
        val contact: String,
        val reason: String,
    ) : ScenarioAgentCommand

    data class CreateReminder(
        override val taskId: String,
        val title: String,
        val body: String,
        val scheduledFor: String,
    ) : ScenarioAgentCommand

    data class AskUser(
        override val taskId: String,
        val decision: ScenarioDecision,
    ) : ScenarioAgentCommand

    data class SwitchTask(
        override val taskId: String,
    ) : ScenarioAgentCommand

    data class CompleteTask(
        override val taskId: String,
        val summary: String,
    ) : ScenarioAgentCommand
}

data class ScenarioCommandBatch(
    val commands: List<ScenarioAgentCommand>,
)

data class ScenarioCommandAuthorization(
    val taskIds: Set<String> = emptySet(),
    val sms: Set<ScenarioSmsAuthorization> = emptySet(),
    val reminders: Set<ScenarioReminderAuthorization> = emptySet(),
)

data class ScenarioSmsAuthorization(
    val taskId: String,
    val to: String,
)

data class ScenarioReminderAuthorization(
    val taskId: String,
    val scheduledFor: String,
)

data class ScenarioCommandParseResult(
    val batch: ScenarioCommandBatch?,
    val error: String? = null,
) {
    val isOk: Boolean = batch != null && error == null
}

object ScenarioCommandCodec {
    private val allowedTypes = setOf(
        "create_task",
        "update_task",
        "send_sms",
        "wait_sms",
        "create_reminder",
        "ask_user",
        "switch_task",
        "complete_task",
    )

    fun parse(raw: String): ScenarioCommandParseResult {
        val root = runCatching { extractJson(raw) }.getOrNull()
            ?: return ScenarioCommandParseResult(null, "没有找到可解析的命令 JSON。")
        val arr = when {
            root is JSONArray -> root
            root is JSONObject && root.has("commands") -> root.optJSONArray("commands")
            else -> null
        } ?: return ScenarioCommandParseResult(null, "命令 JSON 必须是数组或包含 commands 数组。")

        val commands = runCatching {
            buildList {
                for (index in 0 until arr.length()) {
                    val obj = arr.optJSONObject(index)
                        ?: return ScenarioCommandParseResult(null, "第 ${index + 1} 条命令不是对象。")
                    val type = obj.optString("type").trim()
                    if (type !in allowedTypes) {
                        return ScenarioCommandParseResult(null, "不支持的命令类型：$type。")
                    }
                    add(parseCommand(type, obj, index))
                }
            }
        }.getOrElse { return ScenarioCommandParseResult(null, it.message ?: "命令解析失败。") }
        return ScenarioCommandParseResult(ScenarioCommandBatch(commands))
    }

    fun toJson(batch: ScenarioCommandBatch): String =
        JSONObject()
            .put("commands", JSONArray().apply {
                batch.commands.forEach { put(commandToJson(it)) }
            })
            .toString()

    fun toolParametersSchema(): String =
        JSONObject()
            .put("type", "object")
            .put("additionalProperties", false)
            .put("required", JSONArray(listOf("commands")))
            .put(
                "properties",
                JSONObject().put(
                    "commands",
                    JSONObject()
                        .put("type", "array")
                        .put("minItems", 0)
                        .put(
                            "items",
                            JSONObject().put(
                                "oneOf",
                                JSONArray(
                                    listOf(
                                        commandSchema(
                                            type = "create_task",
                                            required = listOf("type", "taskId", "title"),
                                        ),
                                        commandSchema(
                                            type = "update_task",
                                            required = listOf("type", "taskId"),
                                        ),
                                        commandSchema(
                                            type = "send_sms",
                                            required = listOf("type", "taskId", "to", "message"),
                                        ),
                                        commandSchema(
                                            type = "wait_sms",
                                            required = listOf("type", "taskId", "contact", "reason"),
                                        ),
                                        commandSchema(
                                            type = "create_reminder",
                                            required = listOf("type", "taskId", "title", "body", "scheduledFor"),
                                        ),
                                        commandSchema(
                                            type = "ask_user",
                                            required = listOf("type", "taskId", "decision"),
                                        ),
                                        commandSchema(
                                            type = "switch_task",
                                            required = listOf("type", "taskId"),
                                        ),
                                        commandSchema(
                                            type = "complete_task",
                                            required = listOf("type", "taskId", "summary"),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                ),
            )
            .toString()

    private fun commandSchema(
        type: String,
        required: List<String>,
    ): JSONObject =
        JSONObject()
            .put("type", "object")
            .put("additionalProperties", true)
            .put("required", JSONArray(required))
            .put(
                "properties",
                JSONObject()
                    .put("type", JSONObject().put("type", "string").put("enum", JSONArray(listOf(type))))
                    .put("taskId", JSONObject().put("type", "string"))
                    .put("title", JSONObject().put("type", "string"))
                    .put("subtitle", JSONObject().put("type", "string"))
                    .put(
                        "status",
                        JSONObject()
                            .put("type", "string")
                            .put("enum", JSONArray(listOf("RUNNING", "BLOCKED", "DONE"))),
                    )
                    .put("to", JSONObject().put("type", "string"))
                    .put("message", JSONObject().put("type", "string"))
                    .put("displayName", JSONObject().put("type", "string"))
                    .put("contact", JSONObject().put("type", "string"))
                    .put("reason", JSONObject().put("type", "string"))
                    .put("body", JSONObject().put("type", "string"))
                    .put("scheduledFor", JSONObject().put("type", "string"))
                    .put("summary", JSONObject().put("type", "string"))
                    .put("finalSummary", JSONObject().put("type", "string"))
                    .put("participants", participantsSchema())
                    .put("participantsToAdd", participantsSchema())
                    .put(
                        "participantsToRemove",
                        JSONObject()
                            .put("type", "array")
                            .put("items", JSONObject().put("type", "string")),
                    )
                    .put("decision", decisionSchema()),
            )

    private fun participantsSchema(): JSONObject =
        JSONObject()
            .put("type", "array")
            .put("items", participantSchema())

    private fun participantSchema(): JSONObject =
        JSONObject()
            .put("type", "object")
            .put("additionalProperties", false)
            .put("required", JSONArray(listOf("id", "label", "displayName")))
            .put(
                "properties",
                JSONObject()
                    .put("id", JSONObject().put("type", "string"))
                    .put("label", JSONObject().put("type", "string"))
                    .put("displayName", JSONObject().put("type", "string"))
                    .put("role", JSONObject().put("type", "string")),
            )

    private fun decisionSchema(): JSONObject =
        JSONObject()
            .put("type", "object")
            .put("additionalProperties", false)
            .put("required", JSONArray(listOf("text", "actions")))
            .put(
                "properties",
                JSONObject()
                    .put("text", JSONObject().put("type", "string"))
                    .put(
                        "actions",
                        JSONObject()
                            .put("type", "array")
                            .put("minItems", 1)
                            .put("items", actionSchema()),
                    ),
            )

    private fun actionSchema(): JSONObject =
        JSONObject()
            .put("type", "object")
            .put("additionalProperties", false)
            .put("required", JSONArray(listOf("label", "key")))
            .put(
                "properties",
                JSONObject()
                    .put("label", JSONObject().put("type", "string"))
                    .put("key", JSONObject().put("type", "string")),
            )

    private fun parseCommand(
        type: String,
        obj: JSONObject,
        index: Int,
    ): ScenarioAgentCommand =
        when (type) {
            "create_task" -> ScenarioAgentCommand.CreateTask(parseSeed(obj, index))
            "update_task" -> ScenarioAgentCommand.UpdateTask(parseUpdate(obj, index))
            "send_sms" -> ScenarioAgentCommand.SendSms(
                taskId = obj.requiredString("taskId", index),
                to = obj.requiredString("to", index),
                message = obj.requiredString("message", index),
                displayName = obj.optString("displayName").ifBlank { null },
            )
            "wait_sms" -> ScenarioAgentCommand.WaitSms(
                taskId = obj.requiredString("taskId", index),
                contact = obj.requiredString("contact", index),
                reason = obj.requiredString("reason", index),
            )
            "create_reminder" -> ScenarioAgentCommand.CreateReminder(
                taskId = obj.requiredString("taskId", index),
                title = obj.requiredString("title", index),
                body = obj.requiredString("body", index),
                scheduledFor = obj.requiredString("scheduledFor", index),
            )
            "ask_user" -> ScenarioAgentCommand.AskUser(
                taskId = obj.requiredString("taskId", index),
                decision = parseDecision(obj.optJSONObject("decision") ?: obj, index),
            )
            "switch_task" -> ScenarioAgentCommand.SwitchTask(
                taskId = obj.requiredString("taskId", index),
            )
            "complete_task" -> ScenarioAgentCommand.CompleteTask(
                taskId = obj.requiredString("taskId", index),
                summary = obj.requiredString("summary", index),
            )
            else -> error("Unsupported command type: $type")
        }

    private fun parseSeed(
        obj: JSONObject,
        index: Int,
    ): ScenarioTaskSeed {
        val progress = obj.optJSONObject("progress") ?: defaultProgress()
        val summary = visibleSummary(obj)
        val conversations = parseConversations(obj.optJSONArray("conversations"))
            .ifEmpty { summary?.let { listOf(ScenarioConversation(ScenarioSurfaceRole.AGENT, it)) }.orEmpty() }
        val logs = parseLogs(obj.optJSONArray("logs"))
            .ifEmpty { summary?.let { listOf(ScenarioLog(it)) }.orEmpty() }
        return ScenarioTaskSeed(
            taskId = obj.requiredString("taskId", index),
            title = obj.requiredString("title", index),
            subtitle = obj.optString("subtitle"),
            status = parseStatus(obj.optString("status").ifBlank { "RUNNING" }),
            conversations = conversations,
            logs = logs,
            participants = parseParticipants(obj.optJSONArray("participants")),
            progress = parseProgress(progress),
            decision = obj.optJSONObject("decision")?.let { parseDecision(it, index) },
            timeline = parseTimeline(obj.optJSONArray("timeline")),
        )
    }

    private fun parseUpdate(
        obj: JSONObject,
        index: Int,
    ): ScenarioTaskUpdate {
        val progress = obj.optJSONObject("progress") ?: defaultProgress()
        val summary = visibleSummary(obj)
        val conversations = parseConversations(obj.optJSONArray("conversations"))
        val logs = parseLogs(obj.optJSONArray("logs"))
            .ifEmpty { summary?.let { listOf(ScenarioLog(it)) }.orEmpty() }
        return ScenarioTaskUpdate(
            taskId = obj.requiredString("taskId", index),
            subtitle = obj.optString("subtitle"),
            status = parseStatus(obj.optString("status").ifBlank { "RUNNING" }),
            conversations = conversations,
            logs = logs,
            participants = obj.optJSONArray("participants")?.let(::parseParticipants),
            participantsToAdd = parseParticipants(obj.optJSONArray("participantsToAdd")),
            participantsToRemove = stringList(obj.optJSONArray("participantsToRemove")),
            progress = parseProgress(progress),
            decision = obj.optJSONObject("decision")?.let { parseDecision(it, index) },
            activeActionValue = obj.optString("activeActionValue").ifBlank { null },
            timeline = parseTimeline(obj.optJSONArray("timeline")),
            finalSummary = obj.optString("finalSummary").ifBlank { summary.takeIf { obj.optString("status") == "DONE" } },
        )
    }

    private fun visibleSummary(obj: JSONObject): String? =
        obj.optString("summary")
            .ifBlank { obj.optString("subtitle") }
            .ifBlank { null }

    private fun parseDecision(
        obj: JSONObject,
        index: Int,
    ): ScenarioDecision =
        ScenarioDecision(
            text = obj.requiredString("text", index),
            actions = parseActions(obj.optJSONArray("actions")),
        )

    private fun parseActions(arr: JSONArray?): List<ScenarioAction> =
        items(arr) {
            ScenarioAction(
                label = requiredString("label", 0),
                key = requiredString("key", 0),
            )
        }

    private fun parseConversations(arr: JSONArray?): List<ScenarioConversation> =
        items(arr) {
            ScenarioConversation(
                role = parseRole(optString("role").ifBlank { "AGENT" }),
                text = requiredString("text", 0),
            )
        }

    private fun parseLogs(arr: JSONArray?): List<ScenarioLog> =
        items(arr) { ScenarioLog(text = requiredString("text", 0)) }

    private fun parseParticipants(arr: JSONArray?): List<ScenarioParticipant> {
        if (arr == null) return emptyList()
        return buildList {
            for (index in 0 until arr.length()) {
                val obj = arr.optJSONObject(index)
                    ?: error("participants 第 ${index + 1} 项必须是对象。")
                add(
                    ScenarioParticipant(
                        id = obj.requiredString("id", index),
                        label = obj.requiredString("label", index),
                        displayName = obj.requiredString("displayName", index),
                        role = obj.optString("role").ifBlank { "service" },
                    ),
                )
            }
        }
    }

    private fun parseProgress(obj: JSONObject): ScenarioProgress =
        ScenarioProgress(
            label = obj.optString("label").ifBlank { "运行中" },
            detail = obj.optString("detail"),
            completed = obj.optInt("completed", 0),
            total = obj.optInt("total", 1).coerceAtLeast(1),
        )

    private fun parseTimeline(arr: JSONArray?): List<ScenarioTimeline> =
        items(arr) {
            ScenarioTimeline(
                title = requiredString("title", 0),
                detail = optString("detail"),
                status = parseStatus(optString("status").ifBlank { "RUNNING" }),
            )
        }

    private fun commandToJson(command: ScenarioAgentCommand): JSONObject =
        when (command) {
            is ScenarioAgentCommand.CreateTask -> seedToJson(command.seed).put("type", "create_task")
            is ScenarioAgentCommand.UpdateTask -> updateToJson(command.update).put("type", "update_task")
            is ScenarioAgentCommand.SendSms -> JSONObject()
                .put("type", "send_sms")
                .put("taskId", command.taskId)
                .put("to", command.to)
                .put("message", command.message)
                .put("displayName", command.displayName)
            is ScenarioAgentCommand.WaitSms -> JSONObject()
                .put("type", "wait_sms")
                .put("taskId", command.taskId)
                .put("contact", command.contact)
                .put("reason", command.reason)
            is ScenarioAgentCommand.CreateReminder -> JSONObject()
                .put("type", "create_reminder")
                .put("taskId", command.taskId)
                .put("title", command.title)
                .put("body", command.body)
                .put("scheduledFor", command.scheduledFor)
            is ScenarioAgentCommand.AskUser -> JSONObject()
                .put("type", "ask_user")
                .put("taskId", command.taskId)
                .put("decision", decisionToJson(command.decision))
            is ScenarioAgentCommand.SwitchTask -> JSONObject()
                .put("type", "switch_task")
                .put("taskId", command.taskId)
            is ScenarioAgentCommand.CompleteTask -> JSONObject()
                .put("type", "complete_task")
                .put("taskId", command.taskId)
                .put("summary", command.summary)
        }

    private fun seedToJson(seed: ScenarioTaskSeed): JSONObject =
        JSONObject()
            .put("taskId", seed.taskId)
            .put("title", seed.title)
            .put("subtitle", seed.subtitle)
            .put("status", seed.status.name)
            .put("conversations", JSONArray(seed.conversations.map(::conversationToJson)))
            .put("logs", JSONArray(seed.logs.map { JSONObject().put("text", it.text) }))
            .put("participants", JSONArray(seed.participants.map(::participantToJson)))
            .put("progress", progressToJson(seed.progress))
            .put("decision", seed.decision?.let(::decisionToJson))
            .put("timeline", JSONArray(seed.timeline.map(::timelineToJson)))

    private fun updateToJson(update: ScenarioTaskUpdate): JSONObject =
        JSONObject()
            .put("taskId", update.taskId)
            .put("subtitle", update.subtitle)
            .put("status", update.status.name)
            .put("conversations", JSONArray(update.conversations.map(::conversationToJson)))
            .put("logs", JSONArray(update.logs.map { JSONObject().put("text", it.text) }))
            .put("participants", update.participants?.let { JSONArray(it.map(::participantToJson)) })
            .put("participantsToAdd", JSONArray(update.participantsToAdd.map(::participantToJson)))
            .put("participantsToRemove", JSONArray(update.participantsToRemove))
            .put("progress", progressToJson(update.progress))
            .put("decision", update.decision?.let(::decisionToJson))
            .put("activeActionValue", update.activeActionValue)
            .put("timeline", JSONArray(update.timeline.map(::timelineToJson)))
            .put("finalSummary", update.finalSummary)

    private fun conversationToJson(value: ScenarioConversation): JSONObject =
        JSONObject()
            .put("role", value.role.name)
            .put("text", value.text)

    private fun participantToJson(value: ScenarioParticipant): JSONObject =
        JSONObject()
            .put("id", value.id)
            .put("label", value.label)
            .put("displayName", value.displayName)
            .put("role", value.role)

    private fun decisionToJson(value: ScenarioDecision): JSONObject =
        JSONObject()
            .put("text", value.text)
            .put("actions", JSONArray(value.actions.map {
                JSONObject().put("label", it.label).put("key", it.key)
            }))

    private fun progressToJson(value: ScenarioProgress): JSONObject =
        JSONObject()
            .put("label", value.label)
            .put("detail", value.detail)
            .put("completed", value.completed)
            .put("total", value.total)

    private fun timelineToJson(value: ScenarioTimeline): JSONObject =
        JSONObject()
            .put("title", value.title)
            .put("detail", value.detail)
            .put("status", value.status.name)

    private fun parseStatus(value: String): ScenarioSurfaceStatus =
        runCatching { ScenarioSurfaceStatus.valueOf(value.trim().uppercase()) }
            .getOrElse { ScenarioSurfaceStatus.RUNNING }

    private fun parseRole(value: String): ScenarioSurfaceRole =
        runCatching { ScenarioSurfaceRole.valueOf(value.trim().uppercase()) }
            .getOrElse { ScenarioSurfaceRole.AGENT }

    private fun defaultProgress(): JSONObject =
        JSONObject()
            .put("label", "运行中")
            .put("detail", "")
            .put("completed", 0)
            .put("total", 1)

    private fun stringList(arr: JSONArray?): List<String> =
        if (arr == null) emptyList() else (0 until arr.length()).mapNotNull {
            arr.optString(it).takeIf { value -> value.isNotBlank() }
        }

    private fun <T> items(
        arr: JSONArray?,
        block: JSONObject.() -> T,
    ): List<T> {
        if (arr == null) return emptyList()
        return buildList {
            for (index in 0 until arr.length()) {
                val obj = arr.optJSONObject(index) ?: continue
                add(block(obj))
            }
        }
    }

    private fun JSONObject.requiredString(
        key: String,
        commandIndex: Int,
    ): String =
        optString(key).trim().ifBlank {
            error("第 ${commandIndex + 1} 条命令缺少字段：$key。")
        }

    private fun extractJson(raw: String): Any {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) return JSONObject(trimmed)
        if (trimmed.startsWith("[")) return JSONArray(trimmed)
        val objectStart = trimmed.indexOf('{')
        val arrayStart = trimmed.indexOf('[')
        val start = listOf(objectStart, arrayStart).filter { it >= 0 }.minOrNull()
            ?: error("No JSON found")
        return if (trimmed[start] == '{') {
            JSONObject(trimmed.substring(start))
        } else {
            JSONArray(trimmed.substring(start))
        }
    }
}
