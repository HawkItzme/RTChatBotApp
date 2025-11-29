package com.rtchatbotapp.data.model

data class ChatThread(
    val id: String,
    val title: String,
    val lastMessage: String,
    val lastAt: Long,
    val unread: Int
)