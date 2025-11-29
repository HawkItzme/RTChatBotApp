package com.rtchatbotapp.data.source.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.rtchatbotapp.data.model.ChatThreadEntity
import com.rtchatbotapp.data.model.QueuedMessageEntity

@Database(entities = [QueuedMessageEntity::class, ChatThreadEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun queuedMessageDao(): QueuedMessageDao
    abstract fun chatThreadDao(): ChatThreadDao
}