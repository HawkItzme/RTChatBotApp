package com.netomi.assignment.rtchatbotapp.ui

import android.R.attr.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.netomi.assignment.rtchatbotapp.ui.components.ChatComposer
import com.netomi.assignment.rtchatbotapp.ui.components.MessageBubble
import com.netomi.assignment.rtchatbotapp.ui.components.TopBar
import com.netomi.assignment.rtchatbotapp.ui.viewmodel.ChatViewModel
import com.netomi.assignment.rtchatbotapp.ui.viewmodel.UiEvent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val messages = uiState.messages
    val simulateOn by viewModel.simulateFailure.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.padding(8.dp))
        },
        modifier = Modifier.systemBarsPadding().imePadding(),
        topBar = {
            TopBar(isOnline = uiState.isOnline, connState = uiState.connectionState, simulateOn = simulateOn, simulateToggle = { viewModel.toggleSimulateFailure() })
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).systemBarsPadding()) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                reverseLayout = false,
                contentPadding = PaddingValues(8.dp),
                state = listState
            ) {
                items(messages) { msg ->
                    MessageBubble(msg)
                }
            }
            ChatComposer(onSend = { text -> viewModel.sendUserMessage(text) })
        }
    }
}