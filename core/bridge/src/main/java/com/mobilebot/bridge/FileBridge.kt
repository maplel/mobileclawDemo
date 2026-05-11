package com.mobilebot.bridge

data class WorkspaceFileRead(
    val text: String,
    val truncated: Boolean = false,
    val error: String? = null,
)

interface FileBridge {
    suspend fun readWorkspaceText(
        relativePath: String,
        maxChars: Int = 32_000,
    ): WorkspaceFileRead
}
