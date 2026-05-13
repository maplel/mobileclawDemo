package com.mobilebot.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory_facts",
    indices = [Index("namespace")],
)
data class MemoryFactEntity(
    @PrimaryKey val id: String,
    val namespace: String,
    val key: String,
    val value: String,
    val embeddingRef: String?,
    val confidence: Float,
    val updatedAt: Long,
)
