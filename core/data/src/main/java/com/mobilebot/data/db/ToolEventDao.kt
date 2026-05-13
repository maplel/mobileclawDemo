package com.mobilebot.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ToolEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ToolEventEntity)

    @Query("SELECT * FROM tool_events WHERE sessionKey = :sessionKey ORDER BY createdAt DESC LIMIT :limit")
    suspend fun listRecent(
        sessionKey: String,
        limit: Int,
    ): List<ToolEventEntity>
}
