package com.mobilebot.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tool_events",
    indices = [Index("sessionKey"), Index(value = ["sessionKey", "createdAt"])],
)
data class ToolEventEntity(
    @PrimaryKey val id: String,
    val sessionKey: String,
    val toolId: String,
    val argsJson: String,
    val resultJson: String?,
    val status: String,
    val createdAt: Long,
)
