package com.mobilebot.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SessionDao {
    /**
     * 勿对 sessions 用 REPLACE：SQLite 会先删再插，触发 messages 上 FK 的 ON DELETE CASCADE，刚写入的消息会被清空。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(session: SessionEntity)

    @Query("UPDATE sessions SET updatedAt = :updatedAt WHERE sessionKey = :key")
    suspend fun updateTimestamp(key: String, updatedAt: Long): Int

    @Query("SELECT sessionKey FROM sessions ORDER BY updatedAt DESC")
    suspend fun listKeys(): List<String>

    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    suspend fun listAll(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE sessionKey = :key LIMIT 1")
    suspend fun get(key: String): SessionEntity?

    @Query("DELETE FROM sessions WHERE sessionKey = :key")
    suspend fun deleteByKey(key: String)
}
