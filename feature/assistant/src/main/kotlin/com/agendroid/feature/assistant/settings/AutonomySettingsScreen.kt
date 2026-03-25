package com.agendroid.feature.assistant.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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

private val SMS_MODES = listOf("AUTO", "SEMI", "MANUAL")
private val CALL_MODES = listOf("FULL_AGENT", "SCREEN_ONLY", "PASS_THROUGH")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutonomySettingsScreen(
    viewModel: AutonomySettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settingsState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Autonomy Settings") })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = "SMS Autonomy Mode",
                style = MaterialTheme.typography.titleMedium,
            )
            ModeDropdown(
                label = "SMS Mode",
                options = SMS_MODES,
                selectedOption = settings?.smsAutonomyMode ?: "SEMI",
                onOptionSelected = viewModel::onSmsMode,
            )

            Text(
                text = "Call Autonomy Mode",
                style = MaterialTheme.typography.titleMedium,
            )
            ModeDropdown(
                label = "Call Mode",
                options = CALL_MODES,
                selectedOption = settings?.callAutonomyMode ?: "SCREEN_ONLY",
                onOptionSelected = viewModel::onCallMode,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeDropdown(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
