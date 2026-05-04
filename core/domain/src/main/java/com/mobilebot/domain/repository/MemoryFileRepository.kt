package com.mobilebot.domain.repository

interface MemoryFileRepository {
    fun readMemoryMd(): String

    fun readHistoryTail(maxChars: Int): String

    fun appendHistoryLine(line: String)

    fun writeMemoryMd(content: String)
}
