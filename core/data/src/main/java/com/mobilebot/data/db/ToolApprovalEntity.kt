package com.mobilebot.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tool_approvals",
    indices = [Index(value = ["sessionKey", "toolId", "argsHash"], unique = true)],
)
data class ToolApprovalEntity(
    @PrimaryKey val id: String,
    val sessionKey: String,
    val toolId: String,
    val argsJson: String,
    val argsHash: Int,
    val approvedAt: Long,
)
