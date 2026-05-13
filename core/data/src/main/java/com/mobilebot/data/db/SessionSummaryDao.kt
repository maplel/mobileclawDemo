package com.mobilebot.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SessionSummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SessionSummaryEntity)

    @Query("SELECT summaryText FROM session_summaries WHERE sessionKey = :sessionKey LIMIT 1")
    suspend fun getSummary(sessionKey: String): String?
}
