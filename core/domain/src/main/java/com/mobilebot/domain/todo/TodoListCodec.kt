package com.mobilebot.domain.todo

import org.json.JSONArray
import org.json.JSONObject

data class PlanTodo(
    val id: String,
    val text: String,
)

enum class TodoStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
}

data class TodoItemSnapshot(
    val id: String,
    val text: String,
    val status: TodoStatus = TodoStatus.PENDING,
)

data class TodoListSnapshot(
    val listId: String,
    val title: String,
    val items: List<TodoItemSnapshot>,
)

object TodoListCodec {
    const val MESSAGE_TOOL_NAME = "todo_list"

    fun normalizeTodos(todos: List<PlanTodo>): List<PlanTodo> {
        val out = mutableListOf<PlanTodo>()
        val seen = linkedSetOf<String>()
        todos.forEachIndexed { index, todo ->
            val text = todo.text.trim()
            if (text.isBlank()) return@forEachIndexed
            val id = todo.id.trim().ifBlank { "todo_${index + 1}" }
            if (!seen.add(id)) return@forEachIndexed
            out += PlanTodo(id = id, text = text)
        }
        return out
    }

    fun fromPlan(
        listId: String,
        title: String,
        todos: List<PlanTodo>,
    ): TodoListSnapshot =
        TodoListSnapshot(
            listId = listId,
            title = title.trim().ifBlank { "Plan" },
            items =
                normalizeTodos(todos).map {
                    TodoItemSnapshot(
                        id = it.id,
                        text = it.text,
                        status = TodoStatus.PENDING,
                    )
                },
        )

    fun updateStatus(
        snapshot: TodoListSnapshot,
        todoId: String?,
        status: TodoStatus,
    ): TodoListSnapshot {
        val target = todoId?.trim().orEmpty()
        if (target.isEmpty()) return snapshot
        return snapshot.copy(
            items =
                snapshot.items.map { item ->
                    if (item.id == target) item.copy(status = status) else item
                },
        )
    }

    fun allDone(snapshot: TodoListSnapshot): Boolean =
        snapshot.items.isNotEmpty() &&
            snapshot.items.all { it.status == TodoStatus.COMPLETED }

    fun toJson(snapshot: TodoListSnapshot): String {
        val items =
            JSONArray().apply {
                snapshot.items.forEach { item ->
                    put(
                        JSONObject()
                            .put("id", item.id)
                            .put("text", item.text)
                            .put("status", item.status.name.lowercase()),
                    )
                }
            }
        return JSONObject()
            .put("listId", snapshot.listId)
            .put("title", snapshot.title)
            .put("items", items)
            .toString()
    }

    fun parseJson(json: String?): TodoListSnapshot? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(json.trim())
            val listId = root.optString("listId", "").trim()
            val title = root.optString("title", "").trim()
            val itemsJson = root.optJSONArray("items") ?: JSONArray()
            val items = mutableListOf<TodoItemSnapshot>()
            for (i in 0 until itemsJson.length()) {
                val item = itemsJson.optJSONObject(i) ?: continue
                val id = item.optString("id", "").trim()
                val text = item.optString("text", "").trim()
                if (id.isBlank() || text.isBlank()) continue
                val status =
                    when (item.optString("status", "").trim().lowercase()) {
                        "running" -> TodoStatus.RUNNING
                        "completed" -> TodoStatus.COMPLETED
                        "failed" -> TodoStatus.FAILED
                        else -> TodoStatus.PENDING
                    }
                items += TodoItemSnapshot(id = id, text = text, status = status)
            }
            if (listId.isBlank() || items.isEmpty()) return null
            TodoListSnapshot(
                listId = listId,
                title = title.ifBlank { "Plan" },
                items = items,
            )
        }.getOrNull()
    }
}
