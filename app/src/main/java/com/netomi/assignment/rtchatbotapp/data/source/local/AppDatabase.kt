package com.netomi.assignment.rtchatbotapp.data.source.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.netomi.assignment.rtchatbotapp.data.model.QueuedMessageEntity

@Database(entities = [QueuedMessageEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun queuedMessageDao(): QueuedMessageDao
}