package com.agendroid.feature.phone.dialer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow

private val keypadDigits = listOf(
    "1", "2", "3",
    "4", "5", "6",
    "7", "8", "9",
    "*", "0", "#",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialerScreen(
    uiState: StateFlow<DialerUiState>,
    onDigitPressed: (String) -> Unit,
    onBackspace: () -> Unit,
    onCallPressed: () -> Unit,
    onShowCallLog: () -> Unit,
    onShowInCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state = uiState.collectAsState().value

    LaunchedEffect(state.pendingCallIntent) {
        state.pendingCallIntent?.let { context.startActivity(it) }
    }

    DialerScreen(
        uiState = state,
        onDigitPressed = onDigitPressed,
        onBackspace = onBackspace,
        onCallPressed = onCallPressed,
        onShowCallLog = onShowCallLog,
        onShowInCall = onShowInCall,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialerScreen(
    uiState: DialerUiState,
    onDigitPressed: (String) -> Unit,
    onBackspace: () -> Unit,
    onCallPressed: () -> Unit,
    onShowCallLog: () -> Unit,
    onShowInCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("Phone") })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OutlinedTextField(
                value = uiState.phoneNumber,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                label = { Text("Number") },
                textStyle = MaterialTheme.typography.headlineSmall,
            )

            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(keypadDigits.chunked(3)) { rowDigits ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    ) {
                        rowDigits.forEach { digit ->
                            Button(
                                onClick = { onDigitPressed(digit) },
                                modifier = Modifier.widthIn(min = 88.dp),
                            ) {
                                Text(digit)
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onBackspace,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Delete")
                }
                Button(
                    onClick = onCallPressed,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Call")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onShowCallLog,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Call log")
                }
                Button(
                    onClick = onShowInCall,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("In call")
                }
            }
        }
    }
}
