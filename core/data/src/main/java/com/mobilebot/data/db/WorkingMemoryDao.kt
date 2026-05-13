package com.mobilebot.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WorkingMemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WorkingMemoryEntity)

    @Query("SELECT json FROM working_memory WHERE sessionKey = :sessionKey LIMIT 1")
    suspend fun getJson(sessionKey: String): String?
}
