package com.mobilebot.data.memory

import android.content.Context
import com.mobilebot.domain.repository.MemoryFileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryFileRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : MemoryFileRepository {
        private val workspace: File
            get() =
                File(context.filesDir, "workspace").apply {
                    mkdirs()
                }

        private val memoryFile: File
            get() = File(workspace, "MEMORY.md")

        private val historyFile: File
            get() = File(workspace, "HISTORY.md")

        override fun readMemoryMd(): String =
            if (memoryFile.exists()) {
                memoryFile.readText(Charsets.UTF_8)
            } else {
                ""
            }

        override fun readHistoryTail(maxChars: Int): String {
            if (!historyFile.exists()) return ""
            val t = historyFile.readText(Charsets.UTF_8)
            return if (t.length <= maxChars) t else t.takeLast(maxChars)
        }

        override fun appendHistoryLine(line: String) {
            historyFile.appendText(line + "\n", Charsets.UTF_8)
        }

        override fun writeMemoryMd(content: String) {
            memoryFile.writeText(content, Charsets.UTF_8)
        }
    }
