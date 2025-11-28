package com.netomi.assignment.rtchatbotapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_threads")
data class ChatThreadEntity(
    @PrimaryKey val threadId: String,
    val title: String = "Conversation",
    val lastMessageText: String = "",
    val lastMessageAt: Long = 0L,
    val unreadCount: Int = 0
) {
    fun toModel() = ChatThread(
        id = threadId, title = title, lastMessage = lastMessageText,
        lastAt = lastMessageAt, unread = unreadCount
    )
}
