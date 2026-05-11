package com.mobilebot.domain.memory

import com.mobilebot.domain.repository.MemoryFileRepository
import com.mobilebot.domain.todo.TodoListCodec
import com.mobilebot.domain.todo.TodoListSnapshot
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the workspace context — the medium-term memory layer stored in
 * `workspace/MEMORY.md`. This sits between short-term message history and
 * long-term persistent memories.
 *
 * The workspace context tracks:
 * - Current active task / goal
 * - Key progress and observations
 * - Pending items or next steps
 *
 * It is overwritten when a new distinct task begins and updated as the task progresses.
 * The content is injected into each LLM turn via `buildMemoryDigest()`.
 */
@Singleton
class WorkspaceContextManager @Inject constructor(
    private val memoryFiles: MemoryFileRepository,
) {

    /**
     * Initialize workspace context for a new user request.
     *
     * If there is no existing context, writes a fresh task entry.
     * If there IS existing context, we assume the new request continues
     * the same session — we keep the existing context and append the
     * new user request as an "update" to current task.
     */
    suspend fun onNewUserRequest(text: String) {
        val existing = memoryFiles.readMemoryMd().trim()
        if (existing.isBlank()) {
            // Fresh start — write initial task context
            writeContext(
                task = text.truncatedForContext(),
                progress = emptyList(),
                observations = emptyList(),
            )
        } else {
            // Continuation — append the new request as an observation
            appendObservation("User asked: ${text.truncatedForContext()}")
        }
    }

    /**
     * Update workspace context after a plan step has been completed.
     */
    suspend fun onPlanStepCompleted(
        planSnapshot: TodoListSnapshot?,
        stepText: String,
    ) {
        appendProgress("Completed: ${stepText.truncatedForContext()}")

        if (planSnapshot != null && TodoListCodec.allDone(planSnapshot)) {
            appendObservation("Task completed: ${planSnapshot.title}")
        }
    }

    /**
     * Update workspace context after the agent has finished responding.
     * Captures the final response as an observation.
     */
    suspend fun onAgentResponse(reply: String) {
        if (reply.isNotBlank()) {
            appendObservation("Response: ${reply.truncatedForContext()}")
        }
    }

    /**
     * Update workspace context after tool execution produced a meaningful result.
     */
    suspend fun onToolResult(toolName: String, summary: String) {
        if (summary.isNotBlank()) {
            appendObservation("$toolName → ${summary.truncatedForContext()}")
        }
    }

    /**
     * Reset workspace context for a completely new task.
     */
    suspend fun resetContext(task: String) {
        memoryFiles.writeMemoryMd(
            buildContextBlock(
                task = task.truncatedForContext(),
                progress = emptyList(),
                observations = emptyList(),
            )
        )
    }

    /**
     * Append a progress item to the workspace context.
     */
    private suspend fun appendProgress(item: String) {
        val current = parseCurrent(memoryFiles.readMemoryMd())
        writeContext(
            task = current.task,
            progress = current.progress + item,
            observations = current.observations,
        )
    }

    /**
     * Append an observation to the workspace context.
     */
    private suspend fun appendObservation(item: String) {
        val current = parseCurrent(memoryFiles.readMemoryMd())
        // Keep last 10 observations max to prevent bloat
        val observations = (current.observations + item).takeLast(10)
        writeContext(
            task = current.task,
            progress = current.progress,
            observations = observations,
        )
    }

    private suspend fun writeContext(
        task: String,
        progress: List<String>,
        observations: List<String>,
    ) {
        memoryFiles.writeMemoryMd(buildContextBlock(task, progress, observations))
    }

    private fun buildContextBlock(
        task: String,
        progress: List<String>,
        observations: List<String>,
    ): String = buildString {
        appendLine("## Current Task")
        appendLine(task)
        appendLine()

        if (progress.isNotEmpty()) {
            appendLine("## Progress")
            for (item in progress.takeLast(10)) {
                appendLine("- $item")
            }
            appendLine()
        }

        if (observations.isNotEmpty()) {
            appendLine("## Observations")
            for (item in observations.takeLast(10)) {
                appendLine("- $item")
            }
        }
    }

    private fun parseCurrent(content: String): ParsedContext {
        if (content.isBlank()) return ParsedContext("", emptyList(), emptyList())

        val lines = content.lines()
        val task = extractSection(lines, "Current Task", "Progress")?.trim()
            ?: extractSection(lines, "Current Task", "Observations")?.trim()
            ?: ""
        val progress = extractListItems(lines, "Progress", "Observations")
        val observations = extractListItems(lines, "Observations", null)

        return ParsedContext(task, progress, observations)
    }

    private fun extractSection(lines: List<String>, section: String, nextSection: String?): String? {
        val startIdx = lines.indexOfFirst { it.trim() == "## $section" }
        if (startIdx < 0) return null
        val endIdx = nextSection?.let { n ->
            lines.indexOfFirst { it.trim() == "## $n" }.takeIf { it > startIdx }
        } ?: lines.size
        return lines.subList(startIdx + 1, endIdx).joinToString("\n").trim()
    }

    private fun extractListItems(lines: List<String>, section: String, nextSection: String?): List<String> {
        val startIdx = lines.indexOfFirst { it.trim() == "## $section" }
        if (startIdx < 0) return emptyList()
        val endIdx = nextSection?.let { n ->
            lines.indexOfFirst { it.trim() == "## $n" }.takeIf { it > startIdx }
        } ?: lines.size
        return lines.subList(startIdx + 1, endIdx)
            .map { it.trim() }
            .filter { it.startsWith("- ") }
            .map { it.removePrefix("- ") }
    }

    private data class ParsedContext(
        val task: String,
        val progress: List<String>,
        val observations: List<String>,
    )

    companion object {
        private const val MAX_CONTEXT_LENGTH = 500

        private fun String.truncatedForContext(): String =
            if (length <= MAX_CONTEXT_LENGTH) this else take(MAX_CONTEXT_LENGTH) + "..."
    }
}
