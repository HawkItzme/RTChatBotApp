package com.rtchatbotapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "queued_messages")
data class QueuedMessageEntity(
    @PrimaryKey val id: String,
    val text: String,
    val timestamp: Long,
    val isFromUser: Boolean,
    val attemptCount: Int = 0
) {
    fun toMessage() = Message(id, text, timestamp, isFromUser, MessageStatus.PENDING)
}
