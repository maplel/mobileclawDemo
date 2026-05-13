package com.mobilebot.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MemoryFactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MemoryFactEntity)

    @Query(
        """
        SELECT * FROM memory_facts
        WHERE value LIKE '%' || :needle || '%' OR key LIKE '%' || :needle || '%'
        ORDER BY updatedAt DESC LIMIT :limit
        """,
    )
    suspend fun search(
        needle: String,
        limit: Int,
    ): List<MemoryFactEntity>
}
