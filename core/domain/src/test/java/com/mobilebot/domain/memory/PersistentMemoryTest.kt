package com.mobilebot.domain.memory

import junit.framework.AssertionFailedError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

/**
 * Unit tests for the persistent memory system types and utilities.
 */
class PersistentMemoryTest {

    @Test
    fun memoryType_fromLabel_parsesCorrectly() {
        assertEquals(MemoryType.User, MemoryType.fromLabel("user"))
        assertEquals(MemoryType.Feedback, MemoryType.fromLabel("feedback"))
        assertEquals(MemoryType.Project, MemoryType.fromLabel("project"))
        assertEquals(MemoryType.Reference, MemoryType.fromLabel("reference"))
    }

    @Test
    fun memoryType_fromLabel_isCaseInsensitive() {
        assertEquals(MemoryType.User, MemoryType.fromLabel("USER"))
        assertEquals(MemoryType.Feedback, MemoryType.fromLabel("Feedback"))
        assertEquals(MemoryType.Project, MemoryType.fromLabel("PROJECT"))
    }

    @Test
    fun memoryType_fromLabel_returnsNullForInvalid() {
        assertNull(MemoryType.fromLabel("invalid"))
        assertNull(MemoryType.fromLabel(""))
        assertNull(MemoryType.fromLabel(null))
        assertNull(MemoryType.fromLabel("tool"))
    }

    @Test
    fun memoryType_toLabel_convertsBack() {
        assertEquals("user", MemoryType.toLabel(MemoryType.User))
        assertEquals("feedback", MemoryType.toLabel(MemoryType.Feedback))
        assertEquals("project", MemoryType.toLabel(MemoryType.Project))
        assertEquals("reference", MemoryType.toLabel(MemoryType.Reference))
    }

    @Test
    fun parseMemoryType_delegatesToFromLabel() {
        assertNotNull(parseMemoryType("user"))
        assertNull(parseMemoryType("invalid"))
    }

    @Test
    fun memoryDigestBuilder_ageString_today() {
        val today = System.currentTimeMillis()
        assertEquals("today", MemoryDigestBuilder.ageString(today))
    }

    @Test
    fun memoryDigestBuilder_ageString_yesterday() {
        val yesterday = System.currentTimeMillis() - 86_400_000L
        assertEquals("yesterday", MemoryDigestBuilder.ageString(yesterday))
    }

    @Test
    fun memoryDigestBuilder_ageString_daysAgo() {
        val old = System.currentTimeMillis() - (47L * 86_400_000L)
        assertEquals("47 days ago", MemoryDigestBuilder.ageString(old))
    }

    @Test
    fun memoryDigestBuilder_freshnessWarning_emptyForFresh() {
        val today = System.currentTimeMillis()
        assertTrue(MemoryDigestBuilder.freshnessWarning(today).isEmpty())
        val yesterday = System.currentTimeMillis() - 86_400_000L
        assertTrue(MemoryDigestBuilder.freshnessWarning(yesterday).isEmpty())
    }

    @Test
    fun memoryDigestBuilder_freshnessWarning_forOldMemories() {
        val old = System.currentTimeMillis() - (47L * 86_400_000L)
        val warning = MemoryDigestBuilder.freshnessWarning(old)
        assertTrue(warning.contains("47 days old"))
        assertTrue(warning.contains("point-in-time observations"))
    }

    @Test
    fun memoryDigestBuilder_manifest_formatsCorrectly() {
        val memories = listOf(
            HeadlessMemory(
                filename = "user_lang.md",
                filePath = "/path/user_lang.md",
                mtimeMs = 1_000_000_000_000L,
                description = "User language preference",
                type = MemoryType.User,
            ),
            HeadlessMemory(
                filename = "project_release.md",
                filePath = "/path/project_release.md",
                mtimeMs = 1_000_000_000_000L,
                description = null,
                type = MemoryType.Project,
            ),
            HeadlessMemory(
                filename = "no_type.md",
                filePath = "/path/no_type.md",
                mtimeMs = 1_000_000_000_000L,
                description = "No type specified",
                type = null,
            ),
        )

        val manifest = MemoryDigestBuilder.manifest(memories)
        val lines = manifest.trim().split("\n")
        assertEquals(3, lines.size)
        assertTrue(lines[0].contains("[user]"))
        assertTrue(lines[0].contains("user_lang.md"))
        assertTrue(lines[0].contains("User language preference"))
        assertTrue(lines[1].contains("[project]"))
        assertTrue(lines[1].contains("project_release.md"))
        // Timestamp contains colon (HH:mm), but no description colon
        assertFalse(lines[1].contains("):"))
        assertTrue(lines[2].contains("no_type.md"))
        assertFalse(lines[2].contains("["))
    }

    @Test
    fun memoryEntry_creation() {
        val entry = MemoryEntry(
            filePath = "/data/user/0/com.mobilebot/memory/test.md",
            filename = "test.md",
            mtimeMs = 1_000_000_000L,
            description = "Test memory",
            type = MemoryType.User,
            content = "This is test content",
        )
        assertEquals("test.md", entry.filename)
        assertEquals("Test memory", entry.description)
        assertEquals("This is test content", entry.content)
    }

    @Test
    fun headlessMemory_creation() {
        val headless = HeadlessMemory(
            filename = "test.md",
            filePath = "/path/test.md",
            mtimeMs = 1_000_000_000L,
            description = "Headless",
            type = MemoryType.Feedback,
        )
        assertEquals("test.md", headless.filename)
        assertEquals(MemoryType.Feedback, headless.type)
    }

    @Test
    fun memoryType_toLabel_roundTrip() {
        for (type in MemoryType.values()) {
            val label = MemoryType.toLabel(type)
            val restored = MemoryType.fromLabel(label)
            assertEquals("Round-trip failed for $type", type, restored)
        }
    }
}
