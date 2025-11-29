package com.rtchatbotapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rtchatbotapp.data.model.Message
import com.rtchatbotapp.data.model.MessageStatus

@Composable
fun MessageBubble(message: Message) {

    val bubbleColor = if (message.isFromUser) Color(0xFF0D47A1) else Color(0xFFBBDEFB)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 300.dp),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(text = message.text, color = Color.Black)
                Spacer(modifier = Modifier.height(4.dp))
                val statusText = when (message.status) {
                    MessageStatus.SENT -> ""
                    MessageStatus.PENDING -> "Sending..."
                    MessageStatus.FAILED -> "Failed"
                }
                if (statusText.isNotEmpty()) {
                    Text(text = statusText, modifier = Modifier.align(Alignment.End))
                }
            }
        }
    }
}