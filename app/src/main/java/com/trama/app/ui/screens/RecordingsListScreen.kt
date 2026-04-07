package com.trama.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trama.app.summary.RecordingProcessor
import com.trama.app.ui.components.RecordingCard
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.RecordingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsListScreen(
    onBack: () -> Unit,
    onRecordingClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { DatabaseProvider.getRepository(context) }
    val recordings by repository.getAllRecordings().collectAsState(initial = emptyList())

    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size} seleccionadas") },
                    navigationIcon = {
                        IconButton(onClick = { selectionMode = false; selectedIds = emptySet() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancelar")
                        }
                    },
                    actions = {
                        IconButton(onClick = { selectedIds = recordings.map { it.id }.toSet() }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Seleccionar todas")
                        }
                        IconButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    repository.deleteRecordingsByIds(selectedIds.toList())
                                }
                                selectionMode = false
                                selectedIds = emptySet()
                            },
                            enabled = selectedIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Borrar")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Mic, contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Grabaciones")
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    actions = {
                        val failedCount = recordings.count {
                            it.processingStatus == RecordingStatus.FAILED ||
                                it.processingStatus == RecordingStatus.PENDING
                        }
                        if (failedCount > 0) {
                            IconButton(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val processor = RecordingProcessor(context)
                                    recordings.filter {
                                        it.processingStatus == RecordingStatus.FAILED ||
                                            it.processingStatus == RecordingStatus.PENDING
                                    }.forEach { processor.process(it.id, repository) }
                                }
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reprocesar")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (recordings.isEmpty()) {
                item {
                    Text(
                        "No hay grabaciones",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                items(recordings, key = { it.id }) { recording ->
                    RecordingCard(
                        recording = recording,
                        isSelectionMode = selectionMode,
                        isSelected = recording.id in selectedIds,
                        onClick = {
                            if (selectionMode) {
                                selectedIds = if (recording.id in selectedIds)
                                    selectedIds - recording.id else selectedIds + recording.id
                                if (selectedIds.isEmpty()) selectionMode = false
                            } else {
                                onRecordingClick(recording.id)
                            }
                        },
                        onLongClick = {
                            if (!selectionMode) {
                                selectionMode = true
                                selectedIds = setOf(recording.id)
                            }
                        }
                    )
                }
            }
        }
    }
}
