package com.rtchatbotapp.data.source.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rtchatbotapp.data.model.ChatThreadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatThreadDao {
    @Query("SELECT * FROM chat_threads ORDER BY lastMessageAt DESC")
    fun observeAllThreads(): Flow<List<ChatThreadEntity>>

    @Query("SELECT * FROM chat_threads WHERE threadId = :id LIMIT 1")
    suspend fun getThread(id: String): ChatThreadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(thread: ChatThreadEntity)

    @Query("UPDATE chat_threads SET unreadCount = 0 WHERE threadId = :id")
    suspend fun markRead(id: String)

    @Query("DELETE FROM chat_threads")
    suspend fun clearAll()
}