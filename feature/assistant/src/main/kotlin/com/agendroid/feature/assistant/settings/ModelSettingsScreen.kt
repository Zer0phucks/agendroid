package com.agendroid.feature.assistant.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSettingsScreen(
    viewModel: ModelSettingsViewModel = hiltViewModel(),
) {
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    var draftModel by remember(selectedModel) {
        mutableStateOf(selectedModel)
    }

    fun saveModel() {
        val trimmed = draftModel.trim()
        if (trimmed.isNotBlank()) {
            viewModel.onModelSelected(trimmed)
        }
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
                value = draftModel,
                onValueChange = { newValue ->
                    draftModel = newValue
                },
                label = { Text("Model identifier") },
                placeholder = { Text("e.g. gemma3") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { saveModel() },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { saveModel() },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Save")
            }
        }
    }
}
