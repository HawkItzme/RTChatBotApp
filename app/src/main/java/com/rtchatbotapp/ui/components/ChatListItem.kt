package com.rtchatbotapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rtchatbotapp.data.model.ChatThread
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Composable
fun ChatListItem(
    thread: ChatThread,
    showLastMessage: Boolean,
    onClick: () -> Unit
){
    Row(modifier = Modifier
        .fillMaxWidth()
        .clickable { onClick() }
        .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(thread.title.ifBlank { "New Conversation" }, maxLines = 1)
            val msgText = if (showLastMessage && thread.lastMessage.isNotBlank()) {
                thread.lastMessage
            } else {
                "No Chats"
            }
            Text(msgText, maxLines = 1, style = MaterialTheme.typography.bodySmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatShortTime(thread.lastAt))
            if (thread.unread > 0) {
                BadgeBox{
                    Text(
                    thread.unread.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
                }
            }
        }
    }
}

private fun formatShortTime(lastAt: Long): String {
    if (lastAt == 0L) return ""

    val now = System.currentTimeMillis()
    val diff = now - lastAt

    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3600_000}h ago"
        else -> {
            val date = SimpleDateFormat("dd MMM", Locale.getDefault())
            date.format(Date(lastAt))
        }
    }
}

@Composable
fun BadgeBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .padding(top = 4.dp)
            .sizeIn(minWidth = 22.dp, minHeight = 22.dp)
            .background(Color.Red, shape = CircleShape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}