package com.mobilebot.bridge.impl

import android.content.Context
import com.mobilebot.bridge.FileBridge
import com.mobilebot.bridge.WorkspaceFileRead
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidFileBridge
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : FileBridge {
        private val workspace: File
            get() = File(context.filesDir, "workspace").apply { mkdirs() }

        override suspend fun readWorkspaceText(
            relativePath: String,
            maxChars: Int,
        ): WorkspaceFileRead =
            withContext(Dispatchers.IO) {
                try {
                    val path = relativePath.trim().replace("..", "")
                    val f = File(workspace, path).canonicalFile
                    if (!f.path.startsWith(workspace.canonicalPath)) {
                        return@withContext WorkspaceFileRead(text = "", error = "path outside workspace")
                    }
                    if (!f.exists() || !f.isFile) {
                        return@withContext WorkspaceFileRead(text = "", error = "file not found")
                    }
                    val full = f.readText(Charsets.UTF_8)
                    val truncated = full.length > maxChars
                    WorkspaceFileRead(
                        text = full.take(maxChars),
                        truncated = truncated,
                        error = null,
                    )
                } catch (e: Exception) {
                    WorkspaceFileRead(text = "", error = e.message ?: "read failed")
                }
            }
    }
