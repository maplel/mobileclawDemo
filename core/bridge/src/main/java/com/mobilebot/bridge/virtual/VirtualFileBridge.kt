package com.mobilebot.bridge.virtual

import android.util.Log
import com.mobilebot.bridge.FileBridge
import com.mobilebot.bridge.WorkspaceFileRead
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VirtualFileBridge
    @Inject
    constructor() : FileBridge {
        private val virtualFiles = mutableMapOf(
            "shared/incoming.txt" to "这是通过系统分享导入的测试文本。\nHello from shared content!",
            "notes/todo.txt" to "1. 完成项目报告\n2. 预约下周体检\n3. 给张三回电话",
        )

        override suspend fun readWorkspaceText(relativePath: String, maxChars: Int): WorkspaceFileRead {
            Log.d(TAG, "[VIRTUAL] readWorkspaceText($relativePath, maxChars=$maxChars)")
            val content = virtualFiles[relativePath]
                ?: return WorkspaceFileRead(
                    text = "",
                    error = "File not found: $relativePath",
                )
            val truncated = content.length > maxChars
            return WorkspaceFileRead(
                text = if (truncated) content.take(maxChars) else content,
                truncated = truncated,
            )
        }

        fun addVirtualFile(path: String, content: String) {
            virtualFiles[path] = content
        }

        private companion object {
            private const val TAG = "VirtualFile"
        }
    }
