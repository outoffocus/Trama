package com.trama.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import com.trama.app.speech.PersonalDictionary
import com.trama.app.summary.ManualActionSuggestion
import com.trama.app.summary.ManualActionSuggestionExtractor
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryActionType
import com.trama.shared.model.EntryPriority
import com.trama.shared.model.EntryStatus
import com.trama.shared.model.Source
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.Toast

private sealed interface EntryDetailUiState {
    data object Loading : EntryDetailUiState
    data class Success(val entry: DiaryEntry) : EntryDetailUiState
    data object NotFound : EntryDetailUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    entryId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { DatabaseProvider.getRepository(context) }
    val dictionary = remember { PersonalDictionary(context) }
    val scope = rememberCoroutineScope()

    val entryState by produceState<EntryDetailUiState>(
        initialValue = EntryDetailUiState.Loading,
        key1 = entryId
    ) {
        repository.getById(entryId).collect { loadedEntry ->
            value = if (loadedEntry == null) {
                EntryDetailUiState.NotFound
            } else {
                EntryDetailUiState.Success(loadedEntry)
            }
        }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showExtractDialog by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    val successEntry = (entryState as? EntryDetailUiState.Success)?.entry
    var editedText by remember(successEntry?.displayText) { mutableStateOf(successEntry?.displayText ?: "") }
    var selectedSuggestionIndexes by remember { mutableStateOf(setOf<Int>()) }

    when (entryState) {
        EntryDetailUiState.Loading -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Detalle") },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                            }
                        }
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            return
        }
        EntryDetailUiState.NotFound -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Detalle") },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                            }
                        }
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No se encontró esta entrada.",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Puede que ya no exista o que todavía no se haya cargado correctamente.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            return
        }
        is EntryDetailUiState.Success -> Unit
    }
    val currentEntry = (entryState as EntryDetailUiState.Success).entry

    val dateFormat = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("es"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isEditing) {
                            isEditing = false
                            editedText = currentEntry.displayText
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            if (isEditing) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isEditing) "Cancelar" else "Volver"
                        )
                    }
                },
                actions = {
                    if (isEditing) {
                        IconButton(onClick = {
                            scope.launch {
                                val trimmed = editedText.trim()
                                repository.updateText(entryId, trimmed)
                                dictionary.learnFromEdit(currentEntry.text, trimmed)
                            }
                            isEditing = false
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Guardar")
                        }
                    } else {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar")
                        }
                        IconButton(onClick = {
                            val sendIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                putExtra(android.content.Intent.EXTRA_TEXT, currentEntry.text)
                                type = "text/plain"
                            }
                            context.startActivity(android.content.Intent.createChooser(sendIntent, "Compartir"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Compartir")
                        }
                        IconButton(onClick = {
                            val suggestions = ManualActionSuggestionExtractor.extract(currentEntry.displayText)
                            if (suggestions.isEmpty()) {
                                Toast.makeText(context, "No se encontraron acciones claras", Toast.LENGTH_SHORT).show()
                            } else {
                                selectedSuggestionIndexes = suggestions.indices.toSet()
                                showExtractDialog = true
                            }
                        }) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Extraer acciones")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (isEditing) {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                )
            } else {
                Text(
                    text = currentEntry.displayText,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DetailRow("Fecha", dateFormat.format(Date(currentEntry.createdAt)))
                    DetailRow("Palabra clave", currentEntry.keyword)
                    DetailRow("Fuente", if (currentEntry.source == Source.PHONE) "Teléfono" else "Reloj")
                    DetailRow("Confianza", "${(currentEntry.confidence * 100).toInt()}%")
                    DetailRow("Duración grabación", "${currentEntry.duration}s")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Diagnóstico de texto",
                        style = MaterialTheme.typography.titleSmall
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "rawWhisperText",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currentEntry.text.ifBlank { "-" },
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "correctedText",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currentEntry.correctedText?.ifBlank { "-" } ?: "-",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "cleanText",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currentEntry.cleanText?.ifBlank { "-" } ?: "-",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar entrada") },
            text = { Text("¿Estás seguro de que quieres eliminar esta entrada?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.deleteById(entryId)
                    }
                    onBack()
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showExtractDialog) {
        val suggestions = ManualActionSuggestionExtractor.extract(currentEntry.displayText)
        ExtractActionsDialog(
            suggestions = suggestions,
            selected = selectedSuggestionIndexes,
            onDismiss = { showExtractDialog = false },
            onToggle = { index ->
                selectedSuggestionIndexes = if (index in selectedSuggestionIndexes) {
                    selectedSuggestionIndexes - index
                } else {
                    selectedSuggestionIndexes + index
                }
            },
            onConfirm = {
                val picked = suggestions.filterIndexed { index, _ -> index in selectedSuggestionIndexes }
                scope.launch {
                    picked.forEach { suggestion ->
                        repository.insert(
                            DiaryEntry(
                                text = suggestion.text,
                                keyword = currentEntry.keyword,
                                category = currentEntry.category,
                                confidence = currentEntry.confidence,
                                source = currentEntry.source,
                                duration = currentEntry.duration,
                                cleanText = suggestion.text,
                                actionType = suggestion.actionType,
                                priority = suggestion.priority,
                                dueDate = suggestion.dueDate,
                                status = EntryStatus.PENDING
                            )
                        )
                    }
                }
                showExtractDialog = false
                if (picked.isNotEmpty()) {
                    Toast.makeText(context, "${picked.size} acciones creadas", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ExtractActionsDialog(
    suggestions: List<ManualActionSuggestion>,
    selected: Set<Int>,
    onDismiss: () -> Unit,
    onToggle: (Int) -> Unit,
    onConfirm: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM", Locale("es")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Extraer acciones") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Se crearán nuevas acciones a partir de este recordatorio. El original no se modifica.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))

                suggestions.forEachIndexed { index, suggestion ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Checkbox(
                            checked = index in selected,
                            onCheckedChange = { onToggle(index) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(suggestion.text, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${EntryActionType.label(suggestion.actionType)} · ${priorityLabel(suggestion.priority)}" +
                                    (suggestion.dueDate?.let { " · ${dateFormat.format(Date(it))}" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (index < suggestions.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = selected.isNotEmpty()
            ) {
                Text("Crear acciones")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

private fun priorityLabel(priority: String): String = when (priority) {
    EntryPriority.URGENT -> "Urgente"
    EntryPriority.HIGH -> "Alta"
    EntryPriority.LOW -> "Baja"
    else -> "Normal"
}
