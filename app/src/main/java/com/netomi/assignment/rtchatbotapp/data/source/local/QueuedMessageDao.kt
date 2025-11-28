package com.netomi.assignment.rtchatbotapp.data.source.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.netomi.assignment.rtchatbotapp.data.model.QueuedMessageEntity

@Dao
interface QueuedMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: QueuedMessageEntity)

    @Query("SELECT * FROM queued_messages ORDER BY timestamp ASC")
    suspend fun getAll(): List<QueuedMessageEntity>

    @Query("DELETE FROM queued_messages WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM queued_messages")
    suspend fun clearAll()
}