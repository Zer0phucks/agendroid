package com.agendroid.feature.assistant.integrations

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class Integration(
    val name: String,
    val description: String,
    val comingSoon: Boolean = true,
)

private val INTEGRATIONS = listOf(
    Integration(
        name = "CalDAV Calendar",
        description = "Sync events from CalDAV-compatible calendar servers.",
    ),
    Integration(
        name = "IMAP Email",
        description = "Read and summarise emails via IMAP.",
    ),
    Integration(
        name = "Contacts Sync",
        description = "Keep contact preferences aligned with your address book.",
    ),
    Integration(
        name = "WebDAV Notes",
        description = "Index notes stored on a WebDAV server.",
    ),
    Integration(
        name = "RSS / Atom Feeds",
        description = "Follow and index news or blog feeds.",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationsSettingsScreen() {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Integrations") })
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            item {
                Text(
                    text = "Future integrations — coming in a later release.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(INTEGRATIONS) { integration ->
                ListItem(
                    headlineContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = integration.name,
                                modifier = Modifier.weight(1f),
                            )
                            if (integration.comingSoon) {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("Soon", style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                    },
                    supportingContent = {
                        Text(
                            text = integration.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = false,
                            onCheckedChange = null,
                            enabled = false,
                        )
                    },
                )
                HorizontalDivider()
            }
        }
    }
}
