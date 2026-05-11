package com.mobilebot.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionKey = :key ORDER BY id ASC")
    suspend fun listForSession(key: String): List<MessageEntity>

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Query("DELETE FROM messages WHERE sessionKey = :key")
    suspend fun clearSession(key: String)

    @Query("SELECT content FROM messages WHERE sessionKey = :key AND role = 'user' ORDER BY id ASC LIMIT 1")
    suspend fun firstUserContent(key: String): String?
}
