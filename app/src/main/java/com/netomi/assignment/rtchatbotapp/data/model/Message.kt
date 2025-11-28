package com.netomi.assignment.rtchatbotapp.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFromUser: Boolean,
    val status: MessageStatus = MessageStatus.SENT
)
