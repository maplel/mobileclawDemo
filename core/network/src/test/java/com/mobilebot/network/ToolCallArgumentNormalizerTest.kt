package com.mobilebot.network

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolCallArgumentNormalizerTest {
    @Test
    fun normalizeRepairsMissingObjectClosers() {
        val raw = """{"action":"payment","params":{"amount":186,"currency":"CNY","to":"PetSmart","note":"Kylin bath"}"""

        assertEquals(
            """{"action":"payment","params":{"amount":186,"currency":"CNY","to":"PetSmart","note":"Kylin bath"}}""",
            ToolCallArgumentNormalizer.normalize(raw),
        )
    }

    @Test
    fun normalizeKeepsValidArguments() {
        val raw = """{"from":"Driver","context":"home confirmation"}"""

        assertEquals(raw, ToolCallArgumentNormalizer.normalize(raw))
    }

    @Test
    fun normalizeFallsBackWhenArgumentsCannotBeRecovered() {
        assertEquals("{}", ToolCallArgumentNormalizer.normalize("{\"action\":\"payment"))
    }
}
