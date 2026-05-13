package com.mobilebot.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "session_summaries")
data class SessionSummaryEntity(
    @PrimaryKey val sessionKey: String,
    val summaryText: String,
    val updatedAt: Long,
)
