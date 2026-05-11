package com.mobilebot.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sessions",
    indices = [Index(value = ["updatedAt"])],
)
data class SessionEntity(
    @PrimaryKey val sessionKey: String,
    val createdAt: Long,
    val updatedAt: Long,
)
