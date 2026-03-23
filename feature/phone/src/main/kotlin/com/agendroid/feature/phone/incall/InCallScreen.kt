package com.agendroid.feature.phone.incall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agendroid.core.telephony.CallTranscriptLine
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InCallScreen(
    uiState: StateFlow<InCallUiState>,
    onBack: () -> Unit,
    onTakeover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = uiState.collectAsState().value
    InCallScreen(
        uiState = state,
        onBack = onBack,
        onTakeover = onTakeover,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InCallScreen(
    uiState: InCallUiState,
    onBack: () -> Unit,
    onTakeover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(uiState.number ?: "Active call") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (uiState.disclosureDelivered) {
                            "Disclosure delivered"
                        } else {
                            "Disclosure pending"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Mode: ${uiState.mode.name}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = when {
                            uiState.isTakeoverRequested -> "User takeover active"
                            uiState.isAssistantSpeaking -> "Assistant speaking"
                            else -> "Listening"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (uiState.transcript.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Transcript will appear here")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.transcript) { line ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = line.speaker.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = line.text,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onTakeover,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Take over call")
            }
        }
    }
}
