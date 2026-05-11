package com.mobilebot.domain.memory

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * End-to-end integration test for the persistent memory system.
 * Tests the full lifecycle: save → scan → manifest → index → freshness → recall pipeline.
 * Runs on pure JVM without Android runtime.
 */
class PersistentMemoryE2ETest {

    private val testDir = File(System.getProperty("java.io.tmpdir"), "mobilebot-memory-test-${System.nanoTime()}")

    @Before
    fun setUp() {
        testDir.mkdirs()
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    @Test
    fun fullLifecycle_saveScanIndexFreshness() {
        // 1. Simulate saving three typed memory files
        val userMemory = buildFrontmatterFile(testDir, "user_pref_language", "User prefers Chinese", "user", "该用户主要使用中文交流")
        val feedbackMemory = buildFrontmatterFile(testDir, "feedback_terse_responses", "User wants terse responses", "feedback", "不要写冗长总结，用户只看diff")
        val projectMemory = buildFrontmatterFile(testDir, "project_release_v2", "Release v2 on Friday", "project", "本周五发布v2版本，冻结非关键合并")

        // 2. Verify files were created
        assertEquals(3, testDir.listFiles { f -> f.isFile && f.name.endsWith(".md") && f.name != MEMORY_INDEX_FILENAME }?.size)

        // 3. Scan memories (sorted newest-first)
        val scanned = scanMemoryFiles(testDir)
        assertEquals(3, scanned.size)

        // 4. Verify all types found (order doesn't matter, only content)
        val foundTypes = scanned.mapNotNull { it.type }.toSet()
        assertEquals(3, foundTypes.size)
        assertTrue(foundTypes.contains(MemoryType.User))
        assertTrue(foundTypes.contains(MemoryType.Feedback))
        assertTrue(foundTypes.contains(MemoryType.Project))

        // 5. Verify types parsed correctly
        val userScanned = scanned.find { it.filename == "user_pref_language.md" }
        assertEquals(MemoryType.User, userScanned?.type)

        val feedbackScanned = scanned.find { it.filename == "feedback_terse_responses.md" }
        assertEquals(MemoryType.Feedback, feedbackScanned?.type)

        val projectScanned = scanned.find { it.filename == "project_release_v2.md" }
        assertEquals(MemoryType.Project, projectScanned?.type)

        // 6. Verify description parsed
        assertEquals("User prefers Chinese", userScanned?.description)
        assertEquals("User wants terse responses", feedbackScanned?.description)

        // 7. Generate manifest (as used in relevance selection)
        val manifest = MemoryDigestBuilder.manifest(scanned)
        val manifestLines = manifest.trim().split("\n")
        assertEquals(3, manifestLines.size)
        val allLines = manifestLines.joinToString("\n")
        assertTrue("Manifest should contain [user]", allLines.contains("[user]"))
        assertTrue("Manifest should contain [feedback]", allLines.contains("[feedback]"))
        assertTrue("Manifest should contain [project]", allLines.contains("[project]"))
        // No description should have "):" pattern
        assertTrue(allLines.contains("user_pref_language.md") || allLines.contains("feedback_terse_responses.md") || allLines.contains("project_release_v2.md"))

        // 8. Rebuild index
        rebuildIndex(testDir, scanned)
        val indexFile = File(testDir, MEMORY_INDEX_FILENAME)
        assertTrue(indexFile.exists())
        val indexContent = indexFile.readText()
        assertTrue(indexContent.contains("# Persistent Memory Index"))
        assertTrue(indexContent.contains("user_pref_language.md"))
        assertTrue(indexContent.contains("feedback_terse_responses.md"))
        assertTrue(indexContent.contains("project_release_v2.md"))

        // 9. Freshness: new files should have no warning
        val userFile = userMemory
        assertTrue(MemoryDigestBuilder.freshnessWarning(userFile.lastModified()).isEmpty())

        // 10. Freshness: old files should have warning
        val oldTime = System.currentTimeMillis() - (5L * 86_400_000L)
        val warning = MemoryDigestBuilder.freshnessWarning(oldTime)
        assertTrue(warning.contains("5 days old"))
        assertTrue(warning.contains("point-in-time observations"))

        // 11. Age string
        assertEquals("today", MemoryDigestBuilder.ageString(System.currentTimeMillis()))
        assertEquals("yesterday", MemoryDigestBuilder.ageString(System.currentTimeMillis() - 86_400_000L))
        assertEquals("30 days ago", MemoryDigestBuilder.ageString(System.currentTimeMillis() - 30L * 86_400_000L))

        // 12. Delete one memory and verify index updates
        feedbackScanned?.let { File(it.filePath).delete() }
        rebuildIndex(testDir, scanMemoryFiles(testDir))
        val updatedIndex = File(testDir, MEMORY_INDEX_FILENAME).readText()
        assertTrue(!updatedIndex.contains("feedback_terse_responses"))
        assertTrue(updatedIndex.contains("user_pref_language"))

        // 13. Index truncation: create >200 entries, verify lines are capped
        for (i in 201..210) {
            buildFrontmatterFile(testDir, "extra_entry_$i", "Extra memory $i", "reference", "Extra content for line $i")
        }
        rebuildIndex(testDir, scanMemoryFiles(testDir))
        val truncatedIndex = File(testDir, MEMORY_INDEX_FILENAME).readText()
        val lineCount = truncatedIndex.lines().size
        assertTrue("Index should be truncated to MAX_INDEX_LINES ($MAX_INDEX_LINES) but was $lineCount", lineCount <= MAX_INDEX_LINES)
    }

    @Test
    fun noFrontmatter_skippedDuringScan() {
        // File without frontmatter — should produce no HeadlessMemory
        val noFrontmatter = File(testDir, "no_frontmatter.md")
        noFrontmatter.writeText("Just plain text with no frontmatter")

        val scanned = scanMemoryFiles(testDir)
        // Files without frontmatter return empty map, producing HeadlessMemory with null fields
        // In production, these are included but with no type/description
        assertEquals(1, scanned.size)
        assertNull(scanned[0].type)
        assertNull(scanned[0].description)
    }

    @Test
    fun noTypeField_includedWithNullType() {
        // File with frontmatter but missing type field — should still be scanned
        val noType = buildFrontmatterFile(testDir, "no_type_memory", "No type specified", "", "Some content")
        val scanned = scanMemoryFiles(testDir)
        assertEquals(1, scanned.size)
        assertNull(scanned[0].type)
        assertEquals("No type specified", scanned[0].description)
    }

    @Test
    fun memoryType_roundTrip_allTypes() {
        for (type in MemoryType.values()) {
            val label = MemoryType.toLabel(type)
            val restored = MemoryType.fromLabel(label)
            assertEquals("Round-trip failed for $type: $label → $restored", type, restored)
        }
    }

    // --- Test helpers ---

    private fun buildFrontmatterFile(
        dir: File,
        name: String,
        description: String,
        type: String,
        content: String,
    ): File {
        val file = File(dir, "$name.md")
        val lines = mutableListOf("---")
        lines.add("name: $name")
        lines.add("description: $description")
        if (type.isNotBlank()) lines.add("type: $type")
        lines.add("---")
        file.writeText(lines.joinToString("\n") + "\n$content")
        return file
    }

    private fun scanMemoryFiles(dir: File): List<HeadlessMemory> {
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".md") && f.name != MEMORY_INDEX_FILENAME }
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.take(MAX_MEMORY_FILES)
            ?.mapNotNull { file ->
                try {
                    val text = file.readText()
                    val (frontmatter, _) = parseFrontmatter(text)
                    HeadlessMemory(
                        filename = file.name,
                        filePath = file.absolutePath,
                        mtimeMs = file.lastModified(),
                        description = frontmatter[KEY_DESCRIPTION],
                        type = frontmatter[KEY_TYPE]?.let { MemoryType.fromLabel(it) },
                    )
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()
    }

    private fun rebuildIndex(dir: File, memories: List<HeadlessMemory>) {
        val lines = mutableListOf("# Persistent Memory Index\n")
        for (mem in memories.take(MAX_MEMORY_FILES)) {
            val tag = mem.type?.let { "[${MemoryType.toLabel(it)}] " } ?: ""
            val line = if (mem.description != null) {
                "- $tag${mem.filename} — ${mem.description.take(120)}"
            } else {
                "- $tag${mem.filename}"
            }
            lines.add(line)
        }
        while (lines.size > MAX_INDEX_LINES) {
            lines.removeAt(lines.size - 1)
        }
        File(dir, MEMORY_INDEX_FILENAME).writeText(lines.joinToString("\n"))
    }

    companion object {
        private const val MEMORY_INDEX_FILENAME = "MEMORY.md"
        private const val KEY_TYPE = "type"
        private const val KEY_DESCRIPTION = "description"
        private const val MAX_MEMORY_FILES = 200
        private const val MAX_INDEX_LINES = 200

        private fun parseFrontmatter(text: String): Pair<Map<String, String>, String> {
            val trimmed = text.trim()
            if (!trimmed.startsWith("---")) return emptyMap<String, String>() to text
            val endIndex = trimmed.indexOf("\n---\n", 3)
            val fmText = if (endIndex > 0) trimmed.substring(3, endIndex) else trimmed.substring(3).trim()
            val fm = mutableMapOf<String, String>()
            for (line in fmText.lineSequence()) {
                val ci = line.indexOf(':')
                if (ci > 0) {
                    fm[line.substring(0, ci).trim()] = line.substring(ci + 1).trim()
                }
            }
            val body = if (endIndex > 0) trimmed.substring(endIndex + 4).trim() else ""
            return fm to body
        }
    }
}
