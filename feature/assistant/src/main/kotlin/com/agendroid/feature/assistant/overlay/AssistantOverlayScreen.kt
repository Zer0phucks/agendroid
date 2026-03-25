package com.agendroid.feature.assistant.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantOverlayScreen(
    onDismiss: () -> Unit,
    viewModel: AssistantOverlayViewModel = hiltViewModel(),
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    val isListening by viewModel.isListening.collectAsState()

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.onDeactivate()
            onDismiss()
        },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Assistant",
                style = MaterialTheme.typography.headlineSmall,
            )

            Icon(
                imageVector = if (isListening) Icons.Default.PlayArrow else Icons.Default.Close,
                contentDescription = if (isListening) "Listening" else "Not listening",
                modifier = Modifier.size(64.dp),
                tint = if (isListening) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = if (isListening) "Listening for wake word…" else "Tap to activate voice entry",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isListening) {
                OutlinedButton(onClick = { viewModel.onDeactivate() }) {
                    Text("Deactivate")
                }
            } else {
                Button(onClick = { viewModel.onActivate() }) {
                    Text("Activate")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
