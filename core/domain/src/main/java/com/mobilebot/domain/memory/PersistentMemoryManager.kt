package com.mobilebot.domain.memory

/**
 * Persistent typed memory system (ported from Claude Code's memory architecture).
 *
 * Stores memories as file-based .md files with frontmatter metadata.
 * Supports four typed categories: user, feedback, project, reference.
 * Generates a MEMORY.md index for LLM context injection.
 */
interface PersistentMemoryManager {

    // --- Save / Delete ---

    /**
     * Save a new memory. Creates a .md file with frontmatter in the memory directory.
     * Automatically regenerates the MEMORY.md index.
     */
    suspend fun saveMemory(
        type: MemoryType,
        name: String,
        description: String,
        content: String,
    )

    /**
     * Delete a memory file. Automatically regenerates the MEMORY.md index.
     */
    suspend fun deleteMemory(filePath: String)

    /**
     * Update the content of an existing memory file.
     * Automatically regenerates the MEMORY.md index.
     */
    suspend fun updateMemory(filePath: String, content: String)

    // --- Scan ---

    /**
     * Scan all memory files, parse frontmatter, return headers sorted newest-first.
     * Capped at MAX_MEMORY_FILES (200).
     */
    suspend fun scanMemories(): List<HeadlessMemory>

    // --- Recall ---

    /**
     * Recall relevant memories for a query.
     * Sends a manifest of all memories to the LLM for relevance selection,
     * then loads full content for selected memories.
     */
    suspend fun recallRelevant(query: String, limit: Int = 5): List<MemoryEntry>

    /**
     * Returns the current MEMORY.md index content.
     * One-line-per-memory index for LLM context injection.
     */
    suspend fun getMemoryIndex(): String

    /**
     * Rebuild MEMORY.md from all files in the memory directory.
     */
    suspend fun rebuildIndex()
}

/**
 * Maximum number of memory files to scan.
 */
private const val MAX_MEMORY_FILES = 200

/**
 * Maximum frontmatter lines to read per file during scan.
 */
private const val FRONTMATTER_MAX_LINES = 30

/**
 * Maximum MEMORY.md line count (Claude Code default: 200).
 */
private const val MAX_ENTRYPOINT_LINES = 200

/**
 * Maximum MEMORY.md byte count (~25KB).
 */
private const val MAX_ENTRYPOINT_BYTES = 25_000
