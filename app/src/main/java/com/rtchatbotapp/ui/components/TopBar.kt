package com.rtchatbotapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TopBar(isOnline: Boolean,
           connState: String,
           simulateOn: Boolean,
           simulateToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = if (isOnline) "Online" else "Offline")
            Text(text = connState)
        }

        Spacer(modifier = Modifier.weight(1f))

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = if (simulateOn) "SimFail:ON" else "SimFail:OFF",
                modifier = Modifier.padding(end = 8.dp))
            TextButton(
                onClick = simulateToggle,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text("SimToggle Button")
            }
        }
    }
}