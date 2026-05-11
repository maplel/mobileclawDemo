package com.mobilebot.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["sessionKey"],
            childColumns = ["sessionKey"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionKey")],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionKey: String,
    val role: String,
    val content: String,
    val toolCallId: String?,
    val toolName: String?,
    val toolCallsJson: String? = null,
    val createdAt: Long,
)
