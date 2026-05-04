package com.mobilebot.chat

enum class SubtaskDisplayStatus { SPAWNED, RUNNING, COMPLETED, FAILED }

sealed class ChatLine {
    data class User(
        val text: String,
    ) : ChatLine()

    data class Assistant(
        val text: String,
    ) : ChatLine()

    data class Progress(
        val text: String,
    ) : ChatLine()

    data class SystemNote(
        val text: String,
    ) : ChatLine()

    data class SubtaskPanel(
        val taskId: String,
        val label: String,
        val status: SubtaskDisplayStatus,
        val entries: List<String>,
    ) : ChatLine()

    data class SubtaskGroup(
        val panels: Map<String, SubtaskPanel> = emptyMap(),
    ) : ChatLine()

    data class ActionPrompt(
        val text: String,
        val actions: List<ActionButton>,
        val answered: Boolean = false,
        val selectedAction: String? = null,
    ) : ChatLine()

    data class TodoList(
        val listId: String,
        val title: String,
        val items: List<TodoDisplayItem>,
    ) : ChatLine()
}

data class ActionButton(
    val label: String,
    val value: String,
)
