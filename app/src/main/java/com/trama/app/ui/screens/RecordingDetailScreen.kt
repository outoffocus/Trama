package com.trama.app.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LightbulbCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trama.app.summary.ActionExecutor
import com.trama.app.summary.ActionType
import com.trama.app.summary.CalendarHelper
import com.trama.app.summary.DeletionFeedbackStore
import com.trama.app.summary.EntryActionBridge
import com.trama.app.summary.RecordingProcessorWorker
import com.trama.app.summary.RecordingTranscriptionWorker
import com.trama.app.audio.PcmRecordingStorage
import com.trama.app.summary.RecordingDeletion
import com.trama.app.summary.SuggestedAction
import com.trama.app.ui.SettingsDataStore
import com.trama.app.ui.components.CalendarActionDialog
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryStatus
import com.trama.shared.model.Recording
import com.trama.shared.model.RecordingKeyPoints
import com.trama.shared.model.RecordingStatus
import com.trama.shared.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class MeetingDetailTab(val label: String) {
    SUMMARY("Resumen"),
    ACTIONS("Acciones"),
    TRANSCRIPT("Transcripción")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingDetailScreen(
    recordingId: Long,
    onBack: () -> Unit,
    onActionClick: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { DatabaseProvider.getRepository(context) }
    val settings = remember { SettingsDataStore(context) }
    val scope = rememberCoroutineScope()

    val recording by repository.getRecordingById(recordingId).collectAsState(initialValue = null)
    val actions by repository.getByRecordingId(recordingId).collectAsState(initialValue = emptyList())
    val learnFromDeletions by settings.learnFromDeletions.collectAsState(initialValue = false)
    var editingNotes by remember { mutableStateOf(false) }
    var savingNotes by remember { mutableStateOf(false) }
    var notesError by remember { mutableStateOf<String?>(null) }
    var locallyDismissedActionIds by remember { mutableStateOf(emptySet<Long>()) }
    var selectedTab by remember { mutableStateOf(MeetingDetailTab.SUMMARY) }
    var transcriptQuery by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // Resolve duplicate original entry texts
    val duplicateOriginals = remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    LaunchedEffect(actions) {
        val dupIds = actions.mapNotNull { it.duplicateOfId }.distinct()
        if (dupIds.isNotEmpty()) {
            val pending = repository.getRecentPendingForDedup()
            duplicateOriginals.value = pending
                .filter { it.id in dupIds }
                .associate { it.id to it.displayText }
        }
    }

    val dateFormat = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale("es"))
    fun acceptSuggestedAction(action: DiaryEntry, source: String) {
        scope.launch(Dispatchers.IO) {
            repository.confirmSuggested(
                action.id,
                com.trama.shared.model.EntryVerificationSource.RECORDING
            )
            if (learnFromDeletions) {
                DeletionFeedbackStore.recordAccepted(
                    context = context,
                    text = action.displayText.ifBlank { action.text },
                    source = source
                )
            }
        }
    }

    recording?.takeIf { editingNotes }?.let { current ->
        MeetingNotesDialog(
            recording = current,
            saving = savingNotes,
            error = notesError,
            onDismiss = {
                if (!savingNotes) {
                    editingNotes = false
                    notesError = null
                }
            },
            onSave = { title, summary, points ->
                savingNotes = true
                notesError = null
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            repository.updateRecordingNotes(
                                id = current.id,
                                title = title.trim().ifBlank { null },
                                summary = summary.trim().ifBlank { null },
                                keyPoints = RecordingKeyPoints.encode(points)
                            )
                        }
                        editingNotes = false
                    } catch (_: Exception) {
                        notesError = "No se han podido guardar las notas."
                    } finally {
                        savingNotes = false
                    }
                }
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!deleting) confirmDelete = false },
            title = { Text("¿Eliminar esta grabación?") },
            text = {
                Text("Se eliminarán la reunión, sus sugerencias y el audio del dispositivo. Esta acción no se puede deshacer.")
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    RecordingDeletion.delete(context, repository, listOf(recordingId))
                                }
                                onBack()
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                confirmDelete = false
                                snackbar.showSnackbar("No se pudo eliminar. Vuelve a intentarlo.")
                            } finally {
                                deleting = false
                            }
                        }
                    }
                ) { Text(if (deleting) "Eliminando…" else "Eliminar") }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { confirmDelete = false }) {
                    Text("Conservar")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Grabación", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    val rec = recording
                    if (rec != null) {
                        // Retry processing if failed, processed locally, or stuck in pending
                        if (rec.processingStatus == RecordingStatus.FAILED ||
                            rec.processingStatus == RecordingStatus.PENDING ||
                            rec.processingStatus == RecordingStatus.TRANSCRIPT_ONLY ||
                            rec.processedBy == "LOCAL_PARTIAL") {
                            IconButton(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    if (rec.transcription.isBlank() && rec.audioFilePath != null) {
                                        RecordingTranscriptionWorker.retry(context, recordingId)
                                    } else {
                                        RecordingProcessorWorker.retry(context, recordingId)
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Refresh,
                                    contentDescription = "Procesar",
                                    tint = when (rec.processingStatus) {
                                        RecordingStatus.FAILED -> MaterialTheme.colorScheme.error
                                        RecordingStatus.PENDING -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.tertiary
                                    })
                            }
                        }
                        // Delete
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar",
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        val rec = recording
        if (rec == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Hero (inline, no extra horiz padding) ──
            item(key = "hero") {
                val min = rec.durationSeconds / 60
                val sec = rec.durationSeconds % 60
                val accent = com.trama.app.ui.theme.LocalTramaColors.current.amber
                Column(
                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(14.dp)
                                .background(accent, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GRABACIÓN",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = accent,
                        )
                    }
                    Text(
                        text = rec.title ?: "Grabación sin procesar",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "${dateFormat.format(Date(rec.createdAt))} · %d:%02d".format(min, sec),
                        style = MaterialTheme.typography.bodySmall,
                        color = com.trama.app.ui.theme.LocalTramaColors.current.mutedText,
                    )
                }
            }

            // ── Status badge ──
            item(key = "status") {
                StatusBadge(rec)
            }

            item(key = "detail_tabs") {
                TabRow(selectedTabIndex = selectedTab.ordinal) {
                    MeetingDetailTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = {
                                Text(
                                    when (tab) {
                                        MeetingDetailTab.ACTIONS -> "${tab.label} (${actions.count { it.status != EntryStatus.DISCARDED }})"
                                        else -> tab.label
                                    },
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }
            }

            val keyPoints = RecordingKeyPoints.decode(rec.keyPoints)
            if (selectedTab == MeetingDetailTab.SUMMARY &&
                (rec.processingStatus == RecordingStatus.COMPLETED ||
                rec.processingStatus == RecordingStatus.TRANSCRIPT_ONLY)) {
                item(key = "meeting_notes") {
                    SectionCard(
                        title = "Notas de la reunión",
                        action = {
                            TextButton(onClick = {
                                notesError = null
                                editingNotes = true
                            }) { Text("Editar") }
                        }
                    ) {
                        Text(
                            text = rec.summary?.takeIf { it.isNotBlank() }
                                ?: "Añade un resumen o las decisiones importantes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (rec.summary.isNullOrBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        if (keyPoints.isNotEmpty()) Spacer(modifier = Modifier.height(8.dp))
                        keyPoints.forEach { point ->
                            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                Text("•", style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(end = 8.dp))
                                Text(point, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            // ── Action Items ──
            if (selectedTab == MeetingDetailTab.ACTIONS && actions.isNotEmpty()) {
                val suggested = actions.filter {
                    it.status == EntryStatus.SUGGESTED && it.id !in locallyDismissedActionIds
                }
                val accepted = actions.filter {
                    it.status == EntryStatus.PENDING || it.status == EntryStatus.COMPLETED
                }

                item(key = "actions_header") {
                    Row(
                        modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.TaskAlt, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Acciones extraídas".uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = com.trama.app.ui.theme.LocalTramaColors.current.mutedText,
                            modifier = Modifier.weight(1f)
                        )
                        if (suggested.isNotEmpty()) {
                            TextButton(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    suggested.forEach { action ->
                                        repository.confirmSuggested(
                                            action.id,
                                            com.trama.shared.model.EntryVerificationSource.RECORDING
                                        )
                                        if (learnFromDeletions) {
                                            DeletionFeedbackStore.recordAccepted(
                                                context = context,
                                                text = action.displayText.ifBlank { action.text },
                                                source = "recording_accept_all"
                                            )
                                        }
                                    }
                                }
                            }) {
                                Text("Añadir todas", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                if (suggested.isNotEmpty()) {
                    items(suggested, key = { "action_${it.id}" }) { action ->
                        RecordingActionItem(
                            entry = action,
                            isSuggested = true,
                            duplicateOfText = action.duplicateOfId?.let { duplicateOriginals.value[it] },
                            onClick = { onActionClick(action.id) },
                            onAccept = { acceptSuggestedAction(action, source = "recording_accept_suggested") },
                            onDismiss = {
                                locallyDismissedActionIds = locallyDismissedActionIds + action.id
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) { repository.markDiscarded(action.id) }
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        locallyDismissedActionIds = locallyDismissedActionIds - action.id
                                        Toast.makeText(
                                            context,
                                            "No se ha podido descartar la sugerencia",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        )
                    }
                }
                if (accepted.isNotEmpty()) {
                    items(accepted, key = { "action_${it.id}" }) { action ->
                        RecordingActionItem(
                            entry = action,
                            isSuggested = false,
                            duplicateOfText = action.duplicateOfId?.let { duplicateOriginals.value[it] },
                            onClick = { onActionClick(action.id) }
                        )
                    }
                }
            }

            if (selectedTab == MeetingDetailTab.ACTIONS && actions.isEmpty()) {
                item(key = "actions_empty") {
                    SectionCard(title = "Acciones extraídas") {
                        Text(
                            "No se ha detectado ninguna acción en esta reunión.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Transcription ──
            if (selectedTab == MeetingDetailTab.TRANSCRIPT) item(key = "transcription") {
                SectionCard(
                    title = "Transcripción completa",
                    action = if (rec.audioFilePath != null &&
                        rec.processingStatus != RecordingStatus.TRANSCRIBING &&
                        rec.processingStatus != RecordingStatus.PROCESSING
                    ) {
                        {
                            TextButton(onClick = {
                                RecordingTranscriptionWorker.retry(context, recordingId)
                            }) { Text("Volver a transcribir") }
                        }
                    } else null
                ) {
                    OutlinedTextField(
                        value = transcriptQuery,
                        onValueChange = { transcriptQuery = it },
                        label = { Text("Buscar en la transcripción") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (transcriptQuery.isNotBlank()) {
                            {
                                IconButton(onClick = { transcriptQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Borrar búsqueda")
                                }
                            }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (transcriptQuery.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${countTextMatches(rec.transcription, transcriptQuery)} coincidencias",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = rec.transcription.ifBlank { "Todavía no hay transcripción." },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

internal fun countTextMatches(text: String, query: String): Int {
    val needle = query.trim()
    if (needle.isEmpty()) return 0
    return Regex(Regex.escape(needle), RegexOption.IGNORE_CASE).findAll(text).count()
}

@Composable
private fun MeetingNotesDialog(
    recording: Recording,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (title: String, summary: String, points: List<String>) -> Unit
) {
    var title by remember(recording.id, recording.title) { mutableStateOf(recording.title.orEmpty()) }
    var summary by remember(recording.id, recording.summary) { mutableStateOf(recording.summary.orEmpty()) }
    var pointsText by remember(recording.id, recording.keyPoints) {
        mutableStateOf(RecordingKeyPoints.decode(recording.keyPoints).joinToString("\n"))
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Editar notas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    enabled = !saving,
                    label = { Text("Título") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    enabled = !saving,
                    label = { Text("Resumen") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 7
                )
                OutlinedTextField(
                    value = pointsText,
                    onValueChange = { pointsText = it },
                    enabled = !saving,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    label = { Text("Puntos clave, uno por línea") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 6
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !saving,
                onClick = { onSave(title, summary, pointsText.lineSequence().toList()) }
            ) { Text(if (saving) "Guardando…" else "Guardar") }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun RecordingHeader(recording: Recording, dateFormat: SimpleDateFormat) {
    val t = com.trama.app.ui.theme.LocalTramaColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = recording.title ?: "Grabación sin procesar",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (recording.source == Source.WATCH) Icons.Default.Watch
                              else Icons.Default.Mic,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = t.mutedText
            )
            val min = recording.durationSeconds / 60
            val sec = recording.durationSeconds % 60
            Text(
                text = "${dateFormat.format(Date(recording.createdAt))} · %d:%02d".format(min, sec),
                style = MaterialTheme.typography.labelMedium,
                color = t.mutedText
            )
        }
    }
}

@Composable
private fun StatusBadge(recording: Recording) {
    val status = recording.processingStatus
    val (icon, label, color) = when {
        status == RecordingStatus.COMPLETED && recording.processedBy == "LOCAL_PARTIAL" ->
            Triple(
                Icons.Default.Schedule,
                "Análisis parcial · puedes reintentarlo",
                MaterialTheme.colorScheme.tertiary
            )
        status == RecordingStatus.COMPLETED && recording.processedBy == "LOCAL" ->
            Triple(Icons.Default.CheckCircle, "Procesado con modelo local", MaterialTheme.colorScheme.tertiary)
        status == RecordingStatus.TRANSCRIPT_ONLY ||
            (status == RecordingStatus.COMPLETED && recording.processedBy == "TRANSCRIPT_ONLY") ->
            Triple(Icons.Default.CheckCircle, "Transcripción local sin analizar", MaterialTheme.colorScheme.tertiary)
        status == RecordingStatus.COMPLETED ->
            Triple(Icons.Default.CheckCircle, "Procesado localmente", MaterialTheme.colorScheme.primary)
        status == RecordingStatus.PROCESSING ->
            Triple(Icons.Default.Schedule, "Audio guardado · preparando notas", MaterialTheme.colorScheme.tertiary)
        status == RecordingStatus.CAPTURING ->
            Triple(Icons.Default.Schedule, "Grabando audio...", MaterialTheme.colorScheme.tertiary)
        status == RecordingStatus.TRANSCRIBING ->
            Triple(Icons.Default.Schedule, "Audio guardado · transcribiendo...", MaterialTheme.colorScheme.tertiary)
        status == RecordingStatus.FAILED ->
            Triple(
                Icons.Default.Error,
                if (recording.audioFilePath.isNullOrBlank()) "Error de grabación"
                else "Error al procesar · audio conservado",
                MaterialTheme.colorScheme.error
            )
        else ->
            Triple(Icons.Default.Schedule, "Pendiente", MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (status == RecordingStatus.PROCESSING ||
            status == RecordingStatus.CAPTURING ||
            status == RecordingStatus.TRANSCRIBING
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = color
            )
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = color)
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun SectionCard(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val t = com.trama.app.ui.theme.LocalTramaColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = t.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, t.softBorder),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = t.mutedText,
                    modifier = Modifier.weight(1f)
                )
                action?.invoke()
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun RecordingActionItem(
    entry: DiaryEntry,
    isSuggested: Boolean,
    duplicateOfText: String? = null,
    onClick: () -> Unit,
    onAccept: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val actionScope = rememberCoroutineScope()
    val quickAction = remember(
        entry.id,
        entry.actionType,
        entry.displayText,
        entry.dueDate,
        entry.status
    ) {
        EntryActionBridge.build(entry)
    }
    var editingCalendarAction by remember(entry.id) { mutableStateOf<SuggestedAction?>(null) }
    var pendingCalendarAction by remember(entry.id) { mutableStateOf<SuggestedAction?>(null) }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        editingCalendarAction = pendingCalendarAction
        pendingCalendarAction = null
    }

    editingCalendarAction?.let { action ->
        com.trama.app.ui.components.EntryCalendarActionDialog(
            entryId = entry.id,
            action = action,
            onDismiss = { editingCalendarAction = null }
        )
    }

    ActionItemCard(
        entry = entry,
        isSuggested = isSuggested,
        duplicateOfText = duplicateOfText,
        onClick = onClick,
        onAccept = onAccept,
        onDismiss = onDismiss,
        quickActionLabel = quickAction?.label,
        quickActionIcon = quickAction?.icon,
        onQuickActionClick = quickAction?.let { action ->
            {
                if (action.action.type == ActionType.CALENDAR_EVENT || action.action.type == ActionType.REMINDER) {
                    if (CalendarHelper.hasWriteCalendarPermission(context)) {
                        editingCalendarAction = action.action
                    } else {
                        pendingCalendarAction = action.action
                        calendarPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_CALENDAR,
                                Manifest.permission.WRITE_CALENDAR
                            )
                        )
                    }
                } else {
                    ActionExecutor.execute(context, action.action)
                }
            }
        }
    )
}

@Composable
private fun ActionItemCard(
    entry: DiaryEntry,
    isSuggested: Boolean,
    duplicateOfText: String? = null,
    onClick: () -> Unit,
    onAccept: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    quickActionLabel: String? = null,
    quickActionIcon: ImageVector? = null,
    onQuickActionClick: (() -> Unit)? = null
) {
    val isDuplicate = duplicateOfText != null
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isDuplicate -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                isSuggested -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            }
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Priority accent
            val priorityColor = when (entry.priority?.uppercase()) {
                "URGENT" -> MaterialTheme.colorScheme.error
                "HIGH" -> MaterialTheme.colorScheme.tertiary
                "LOW" -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            }
            Icon(
                if (isSuggested) Icons.Default.LightbulbCircle else Icons.Default.TaskAlt,
                contentDescription = null,
                tint = if (isSuggested) MaterialTheme.colorScheme.secondary else priorityColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // Duplicate indicator
                if (isDuplicate) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 3.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Ya existe: $duplicateOfText",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    entry.actionType?.let { type ->
                        Text(
                            text = type,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    entry.priority?.let { prio ->
                        if (prio.uppercase() != "NORMAL") {
                            Text(
                                text = prio,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = priorityColor
                            )
                        }
                    }
                }
            }
            if (isSuggested && onAccept != null && onDismiss != null) {
                // Accept / Dismiss buttons
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Descartar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
                IconButton(onClick = onAccept, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Añadir a tareas",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (onQuickActionClick != null) {
                Spacer(modifier = Modifier.width(2.dp))
                IconButton(onClick = onQuickActionClick, modifier = Modifier.size(48.dp)) {
                    Icon(
                        quickActionIcon ?: Icons.Default.Add,
                        contentDescription = quickActionLabel ?: "Ejecutar acción",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
