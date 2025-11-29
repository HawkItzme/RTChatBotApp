package com.rtchatbotapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rtchatbotapp.ui.components.ChatListItem
import com.rtchatbotapp.ui.components.TopBar
import com.rtchatbotapp.ui.viewmodel.ChatListViewModel
import com.rtchatbotapp.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(viewModel: ChatListViewModel,
                   chatViewModel: ChatViewModel,
                   navController: NavController,
){
    val threads by viewModel.threads.collectAsState(initial = emptyList())
    val sessionActiveThreads by chatViewModel.activeThreadsInSession.collectAsState()
    val chatUi by chatViewModel.uiState.collectAsState()
    val simulateOn by chatViewModel.simulateFailure.collectAsState()

    val isOnline = chatUi.isOnline
    val connState = chatUi.connectionState

    Scaffold(
        topBar = {
            TopBar(
                isOnline = isOnline,
                connState = connState,
                simulateOn = simulateOn,
                simulateToggle = { chatViewModel.toggleSimulateFailure() }
            )
        },
        modifier = Modifier.systemBarsPadding().imePadding()
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        ) {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                if (threads.isEmpty()) {
                    item {
                        Card(modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .clickable {
                                navController.navigate("chat/default")
                            }) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "New Conversation", style = MaterialTheme.typography.bodyLarge)
                                    Text(text = "Start a new chat", style = MaterialTheme.typography.bodySmall)
                                }
                                Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                            }
                        }
                    }
                } else {
                    items(threads) { thread ->
                        val showLastMessage = sessionActiveThreads.contains(thread.id)
                        ChatListItem(
                            thread = thread,
                            showLastMessage = showLastMessage,
                            onClick = {
                                chatViewModel.setCurrentThread(thread.id)
                                navController.navigate("chat/${thread.id}")
                            }
                        )
                    }
                }
            }
        }
    }
}