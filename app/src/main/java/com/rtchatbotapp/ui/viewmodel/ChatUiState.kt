package com.rtchatbotapp.ui.viewmodel

import com.rtchatbotapp.data.model.Message

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isOnline: Boolean = false,
    val connectionState: String = "DISCONNECTED"
)
