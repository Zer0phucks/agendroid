package com.agendroid.feature.sms.ui.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ComposeReplyBar(
    draftText: String,
    isSending: Boolean,
    onDraftChanged: (String) -> Unit,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = draftText,
                onValueChange = onDraftChanged,
                placeholder = {
                    Text("Reply")
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                enabled = !isSending,
                singleLine = false,
                maxLines = 4,
            )
            Button(
                onClick = onSendClick,
                enabled = draftText.isNotBlank() && !isSending,
            ) {
                Text(if (isSending) "Sending" else "Send")
            }
        }
    }
}
