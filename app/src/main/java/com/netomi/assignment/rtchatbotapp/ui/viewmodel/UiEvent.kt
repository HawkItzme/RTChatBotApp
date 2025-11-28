package com.netomi.assignment.rtchatbotapp.ui.viewmodel

sealed class UiEvent {
    data class ShowSnackbar(val message: String): UiEvent()
}