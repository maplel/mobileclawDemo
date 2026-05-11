package com.mobilebot.domain.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchContactsToolTest {
    @Test
    fun extractsChineseContactNameFromNaturalLanguage() {
        val candidates = SearchContactsTool.extractQueryCandidates("给张三发短信说周末见")
        assertTrue(candidates.contains("张三"))
    }

    @Test
    fun keepsDigitCandidateForPhoneLookups() {
        val candidates = SearchContactsTool.extractQueryCandidates("联系 13800138000")
        assertTrue(candidates.contains("13800138000"))
    }

    @Test
    fun fallsBackToOriginalShortQuery() {
        val candidates = SearchContactsTool.extractQueryCandidates("张三")
        assertEquals("张三", candidates.first())
    }
}
