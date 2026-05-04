package com.mobilebot.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ToolApprovalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ToolApprovalEntity)

    @Query(
        """
        SELECT COUNT(*) FROM tool_approvals 
        WHERE sessionKey = :sessionKey AND toolId = :toolId AND argsHash = :argsHash
        """,
    )
    suspend fun countApproved(
        sessionKey: String,
        toolId: String,
        argsHash: Int,
    ): Int
}
