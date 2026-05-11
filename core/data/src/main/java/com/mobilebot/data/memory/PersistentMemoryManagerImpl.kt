package com.mobilebot.data.memory

import android.content.Context
import com.mobilebot.domain.memory.HeadlessMemory
import com.mobilebot.domain.memory.MemoryDigestBuilder
import com.mobilebot.domain.memory.MemoryEntry
import com.mobilebot.domain.memory.MemoryType
import com.mobilebot.domain.memory.PersistentMemoryManager
import com.mobilebot.network.LlmClient
import com.mobilebot.network.LlmMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * File-based persistent memory manager implementation.
 * Stores typed memories as .md files with frontmatter in the app's internal storage.
 * Integrates with LlmClient for relevance-based recall.
 */
@Singleton
class PersistentMemoryManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val llmClient: LlmClient,
) : PersistentMemoryManager {

    private val memoryDir: File
        get() = File(context.filesDir, MEMORY_DIR).apply { mkdirs() }

    private val indexPath: File
        get() = File(memoryDir, INDEX_FILENAME)

    // --- Save / Delete ---

    override suspend fun saveMemory(
        type: MemoryType,
        name: String,
        description: String,
        content: String,
    ) = withContext(Dispatchers.IO) {
        val safeName = sanitizeFileName(name)
        val file = File(memoryDir, "$safeName.md")
        if (file.exists()) {
            updateMemory(file.absolutePath, content)
            return@withContext
        }

        val frontmatter = buildFrontmatter(safeName, type, description)
        file.writeText("$frontmatter\n---\n$content", Charsets.UTF_8)
        rebuildIndex()
    }

    override suspend fun deleteMemory(filePath: String) = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (file.exists() && file.parentFile == memoryDir) {
            file.delete()
            rebuildIndex()
        }
    }

    override suspend fun updateMemory(filePath: String, content: String) = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (file.exists() && file.parentFile == memoryDir) {
            val existingText = file.readText(Charsets.UTF_8)
            val separatorIdx = existingText.indexOf("\n---\n")
            val frontmatter = if (separatorIdx > 0) existingText.substring(0, separatorIdx + 4) else ""
            file.writeText("$frontmatter$content", Charsets.UTF_8)
            rebuildIndex()
        }
    }

    // --- Scan ---

    override suspend fun scanMemories(): List<HeadlessMemory> = withContext(Dispatchers.IO) {
        val files = memoryDir.listFiles { f ->
            f.isFile && f.name.endsWith(".md") && f.name != INDEX_FILENAME
        }?.filter { it.isFile }?.sortedByDescending { it.lastModified() }?.take(MAX_MEMORY_FILES)

        files.orEmpty().mapNotNull { file ->
            try {
                val text = file.readText(Charsets.UTF_8)
                val (frontmatter, _) = parseFrontmatter(text)
                HeadlessMemory(
                    filename = file.name,
                    filePath = file.absolutePath,
                    mtimeMs = file.lastModified(),
                    description = frontmatter[KEY_DESCRIPTION],
                    type = frontmatter[KEY_TYPE]?.let { MemoryType.fromLabel(it) },
                )
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Skipping malformed memory: ${file.name}", e)
                null
            }
        }
    }

    // --- Recall ---

    override suspend fun recallRelevant(query: String, limit: Int): List<MemoryEntry> =
        withContext(Dispatchers.IO) {
            val memories = scanMemories()
            if (memories.isEmpty()) return@withContext emptyList()

            val selected = selectRelevantMemories(query, memories, limit)

            selected.mapNotNull { headless ->
                try {
                    val file = File(headless.filePath)
                    val text = file.readText(Charsets.UTF_8)
                    val (_, body) = parseFrontmatter(text)
                    val freshnessWarning = MemoryDigestBuilder.freshnessWarning(headless.mtimeMs)
                    MemoryEntry(
                        filePath = headless.filePath,
                        filename = headless.filename,
                        mtimeMs = headless.mtimeMs,
                        description = headless.description,
                        type = headless.type,
                        content = body + freshnessWarning,
                    )
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Failed to load memory: ${headless.filename}", e)
                    null
                }
            }
        }

    // --- Index ---

    override suspend fun getMemoryIndex(): String = withContext(Dispatchers.IO) {
        if (indexPath.exists()) {
            indexPath.readText(Charsets.UTF_8)
        } else {
            ""
        }
    }

    override suspend fun rebuildIndex() = withContext(Dispatchers.IO) {
        val memories = scanMemories()
        val lines = mutableListOf("# Persistent Memory Index\n")

        for (mem in memories.take(MAX_MEMORY_FILES)) {
            val tag = mem.type?.let { "[${MemoryType.toLabel(it)}] " } ?: ""
            val desc = mem.description
            val line = if (desc != null) {
                "- $tag${mem.filename} — ${desc.take(120)}"
            } else {
                "- $tag${mem.filename}"
            }
            lines.add(line)
        }

        // Truncate to max lines
        while (lines.size > MAX_INDEX_LINES) {
            lines.removeAt(lines.size - 1)
        }

        val indexContent = lines.joinToString("\n")
        indexPath.writeText(indexContent, Charsets.UTF_8)
    }

    // --- Private helpers ---

    private fun buildFrontmatter(
        name: String,
        type: MemoryType,
        description: String,
    ): String {
        return buildString {
            appendLine("---")
            appendLine("name: $name")
            appendLine("description: $description")
            appendLine("type: ${MemoryType.toLabel(type)}")
            appendLine("---")
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.lowercase()
            .replace(Regex("[^a-z0-9_-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_', '-')
    }

    private suspend fun selectRelevantMemories(
        query: String,
        memories: List<HeadlessMemory>,
        limit: Int,
    ): List<HeadlessMemory> {
        val manifest = MemoryDigestBuilder.manifest(memories)
        val systemPrompt = SELECT_MEMORY_SYSTEM_PROMPT.format(limit)

        return try {
            val messages = listOf(
                LlmMessage(role = "system", content = systemPrompt),
                LlmMessage(role = "user", content = "Request: $query\n\nAvailable memories:\n$manifest"),
            )
            val response = llmClient.chat(messages, null, null, 256)
            val selectedFilenames = extractSelectedFilenames(response.content.orEmpty())
            val byFilename = memories.associateBy { it.filename }
            selectedFilenames.mapNotNull { byFilename[it] }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "LLM relevance selection failed, falling back to most recent", e)
            memories.take(limit)
        }
    }

    private fun extractSelectedFilenames(jsonContent: String): List<String> {
        return try {
            val root = JSONObject(jsonContent)
            val array = root.optJSONArray("selected_memories") ?: return emptyList()
            val filenames = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val s = array.optString(i)
                if (s.isNotBlank()) filenames.add(s)
            }
            filenames
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to parse LLM relevance response", e)
            emptyList()
        }
    }

    private fun parseFrontmatter(text: String): Pair<Map<String, String>, String> {
        val trimmed = text.trim()
        if (!trimmed.startsWith("---")) return emptyMap<String, String>() to text

        val endIndex = trimmed.indexOf("\n---\n", 3)
        val frontmatterText = if (endIndex > 0) trimmed.substring(3, endIndex) else trimmed.substring(3).trim()

        val frontmatter = mutableMapOf<String, String>()
        for (line in frontmatterText.lineSequence()) {
            val colonIdx = line.indexOf(':')
            if (colonIdx > 0) {
                val key = line.substring(0, colonIdx).trim()
                val value = line.substring(colonIdx + 1).trim()
                frontmatter[key] = value
            }
        }

        val body = if (endIndex > 0) trimmed.substring(endIndex + 4).trim() else ""
        return frontmatter to body
    }

    companion object {
        private const val TAG = "PersistentMemory"
        private const val MEMORY_DIR = "memory"
        private const val INDEX_FILENAME = "MEMORY.md"
        private const val KEY_TYPE = "type"
        private const val KEY_DESCRIPTION = "description"
        private const val MAX_MEMORY_FILES = 200
        private const val MAX_INDEX_LINES = 200

        private const val SELECT_MEMORY_SYSTEM_PROMPT =
            "You are selecting memories that will be useful to MobileBot as it processes a user's request. " +
                "You will be given the user's request and a list of available memory files with their " +
                "filenames, types, and descriptions.\n\n" +
                "Return a JSON object with a 'selected_memories' array containing filenames (up to %d). " +
                "Only include memories that you are certain will be helpful. Be selective. " +
                "If none are useful, return an empty array."
    }
}
