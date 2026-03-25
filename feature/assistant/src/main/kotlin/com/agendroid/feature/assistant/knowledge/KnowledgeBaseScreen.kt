package com.agendroid.feature.assistant.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.agendroid.core.data.entity.IndexedSourceEntity

private enum class AddDialogType { DOCUMENT, URL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeBaseScreen(
    viewModel: KnowledgeBaseViewModel = hiltViewModel(),
) {
    val sources by viewModel.sourcesFlow.collectAsState()
    var showDialog by remember { mutableStateOf<AddDialogType?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Knowledge Base") })
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FloatingActionButton(
                    onClick = { showDialog = AddDialogType.URL },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Add URL")
                }
                FloatingActionButton(
                    onClick = { showDialog = AddDialogType.DOCUMENT },
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Document")
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (sources.isEmpty()) {
                item {
                    Text(
                        text = "No sources indexed yet. Add a document or URL to get started.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            items(sources, key = { it.id }) { source ->
                IndexedSourceItem(
                    source = source,
                    onDelete = { viewModel.onDelete(source) },
                )
            }
        }
    }

    showDialog?.let { dialogType ->
        when (dialogType) {
            AddDialogType.DOCUMENT -> AddDocumentDialog(
                onAdd = { uri, title ->
                    viewModel.onAddDocument(uri, title)
                    showDialog = null
                },
                onDismiss = { showDialog = null },
            )
            AddDialogType.URL -> AddUrlDialog(
                onAdd = { url ->
                    viewModel.onAddUrl(url)
                    showDialog = null
                },
                onDismiss = { showDialog = null },
            )
        }
    }
}

@Composable
private fun IndexedSourceItem(
    source: IndexedSourceEntity,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = source.uri,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatusChip(status = source.status)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete source")
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val label = when (status) {
        "INDEXED" -> "Indexed"
        "PENDING" -> "Pending"
        "FAILED" -> "Failed"
        else -> status
    }
    AssistChip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
    )
}

@Composable
private fun AddDocumentDialog(
    onAdd: (uri: String, title: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var uri by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Document") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uri,
                    onValueChange = { uri = it },
                    label = { Text("File URI") },
                    placeholder = { Text("file:///path/to/doc.pdf") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (uri.isNotBlank() && title.isNotBlank()) onAdd(uri.trim(), title.trim()) },
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun AddUrlDialog(
    onAdd: (url: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add URL") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL") },
                placeholder = { Text("https://example.com/page") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (url.isNotBlank()) onAdd(url.trim()) },
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
