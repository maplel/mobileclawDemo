package com.mobilebot.domain.todo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoListCodecTest {
    @Test
    fun roundTripsTodoSnapshot() {
        val snapshot =
            TodoListSnapshot(
                listId = "todo_123",
                title = "打开浏览器然后查位置",
                items = listOf(
                    TodoItemSnapshot("todo_1", "打开浏览器", TodoStatus.COMPLETED),
                    TodoItemSnapshot("todo_2", "查询当前位置", TodoStatus.RUNNING),
                ),
            )

        val decoded = TodoListCodec.parseJson(TodoListCodec.toJson(snapshot))

        assertNotNull(decoded)
        assertEquals(snapshot, decoded)
    }

    @Test
    fun updateStatusMarksMatchingTodo() {
        val snapshot =
            TodoListSnapshot(
                listId = "todo_123",
                title = "Plan",
                items = listOf(
                    TodoItemSnapshot("todo_1", "Open browser", TodoStatus.PENDING),
                    TodoItemSnapshot("todo_2", "Get location", TodoStatus.PENDING),
                ),
            )

        val updated = TodoListCodec.updateStatus(snapshot, "todo_2", TodoStatus.COMPLETED)

        assertEquals(TodoStatus.PENDING, updated.items[0].status)
        assertEquals(TodoStatus.COMPLETED, updated.items[1].status)
        assertTrue(!TodoListCodec.allDone(updated))
        assertTrue(TodoListCodec.allDone(TodoListCodec.updateStatus(updated, "todo_1", TodoStatus.COMPLETED)))
    }
}
