package com.trama.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trama.app.summary.RecordingProcessorWorker
import com.trama.app.summary.RecordingTranscriptionWorker
import com.trama.app.summary.RecordingDeletion
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
    val recordings by repository.getAllRecordings().collectAsState(initialValue = emptyList())

    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    var deleteIds by remember { mutableStateOf(setOf<Long>()) }
    var deleting by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    BackHandler(enabled = selectionMode && !deleting && deleteIds.isEmpty()) {
        selectionMode = false
        selectedIds = emptySet()
    }

    if (deleteIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { if (!deleting) deleteIds = emptySet() },
            title = { Text("¿Eliminar ${deleteIds.size} grabaciones?") },
            text = { Text("Se eliminarán las grabaciones seleccionadas y su audio. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(enabled = !deleting, onClick = {
                    val ids = deleteIds
                    deleting = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { RecordingDeletion.delete(context, repository, ids) }
                            selectedIds = emptySet()
                            selectionMode = false
                            deleteIds = emptySet()
                            snackbar.showSnackbar("Grabaciones eliminadas")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            deleteIds = emptySet()
                            snackbar.showSnackbar("No se pudieron eliminar. Vuelve a intentarlo.")
                        } finally {
                            deleting = false
                        }
                    }
                }) { Text(if (deleting) "Eliminando…" else "Eliminar") }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { deleteIds = emptySet() }) { Text("Conservar") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
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
                                deleteIds = selectedIds
                            },
                            enabled = selectedIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Borrar")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            } else {
                TopAppBar(
                    title = { Text("Reuniones") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    actions = {
                        val failedCount = recordings.count {
                            it.processingStatus == RecordingStatus.FAILED ||
                                it.processingStatus == RecordingStatus.PENDING ||
                                it.processingStatus == RecordingStatus.TRANSCRIPT_ONLY ||
                                it.processedBy == "LOCAL_PARTIAL"
                        }
                        if (failedCount > 0) {
                            IconButton(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    recordings.filter {
                                        it.processingStatus == RecordingStatus.FAILED ||
                                            it.processingStatus == RecordingStatus.PENDING ||
                                            it.processingStatus == RecordingStatus.TRANSCRIPT_ONLY ||
                                            it.processedBy == "LOCAL_PARTIAL"
                                    }.forEach { recording ->
                                        if (recording.transcription.isBlank() &&
                                            recording.audioFilePath != null
                                        ) {
                                            RecordingTranscriptionWorker.retry(context, recording.id)
                                        } else {
                                            RecordingProcessorWorker.retry(context, recording.id)
                                        }
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reintentar $failedCount grabaciones pendientes de procesar")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
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
                        "Todavía no hay reuniones. Pulsa el botón flotante de grabación para empezar; después encontrarás aquí sus notas, tareas y transcripción.",
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
