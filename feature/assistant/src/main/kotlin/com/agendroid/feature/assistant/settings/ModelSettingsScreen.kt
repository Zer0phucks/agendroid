package com.agendroid.feature.assistant.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSettingsScreen(
    viewModel: AutonomySettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settingsState.collectAsState()
    var modelInput by remember(settings?.selectedModel) {
        mutableStateOf(settings?.selectedModel ?: "")
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Model Settings") })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Selected AI Model",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Enter the model identifier to use for on-device AI inference.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = modelInput,
                onValueChange = { newValue ->
                    modelInput = newValue
                    if (newValue.isNotBlank()) {
                        viewModel.onModelSelected(newValue.trim())
                    }
                },
                label = { Text("Model identifier") },
                placeholder = { Text("e.g. gemma3") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
