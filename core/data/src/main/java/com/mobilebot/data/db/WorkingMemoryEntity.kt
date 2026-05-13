package com.mobilebot.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "working_memory")
data class WorkingMemoryEntity(
    @PrimaryKey val sessionKey: String,
    val json: String,
    val updatedAt: Long,
)
