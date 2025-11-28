package com.netomi.assignment.rtchatbotapp.ui.viewmodel

import com.netomi.assignment.rtchatbotapp.data.model.Message

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isOnline: Boolean = false,
    val connectionState: String = "DISCONNECTED"
)
