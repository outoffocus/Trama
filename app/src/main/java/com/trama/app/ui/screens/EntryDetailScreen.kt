package com.trama.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trama.app.summary.ActionExecutor
import com.trama.app.summary.ActionType
import com.trama.app.summary.CalendarHelper
import com.trama.app.summary.DeletionFeedbackStore
import com.trama.app.summary.EntryActionBridge
import com.trama.app.summary.SuggestedAction
import com.trama.app.audio.OfflineDictationCapture
import com.trama.app.audio.SherpaWhisperAsrEngine
import com.trama.app.ui.SettingsDataStore
import com.trama.app.ui.components.CalendarActionDialog
import com.trama.app.ui.components.DeleteReasonDialog
import com.trama.app.ui.components.SoftCard
import com.trama.app.ui.theme.LocalTramaColors
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryContentKind
import com.trama.shared.model.EntryActionType
import com.trama.shared.model.EntryPriority
import com.trama.shared.model.EntryStatus
import com.trama.shared.model.EntryVerificationSource
import com.trama.shared.model.EntryExternalState
import com.trama.shared.model.Source
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface EntryDetailUiState {
    data object Loading : EntryDetailUiState
    data class Success(val entry: DiaryEntry) : EntryDetailUiState
    data object NotFound : EntryDetailUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    entryId: Long,
    onBack: () -> Unit,
    onRecordingClick: (Long) -> Unit = {},
    editor: EntryEditorViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val repository = remember { DatabaseProvider.getRepository(context) }
    val settings = remember(context) { SettingsDataStore(context) }
    val scope = rememberCoroutineScope()
    val learnFromDeletions by settings.learnFromDeletions.collectAsStateWithLifecycle(initialValue = false)

    val entryState by produceState<EntryDetailUiState>(EntryDetailUiState.Loading, entryId) {
        repository.getById(entryId).collect { entry ->
            value = entry?.let(EntryDetailUiState::Success) ?: EntryDetailUiState.NotFound
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val isEditing by editor.editing.collectAsStateWithLifecycle()
    val savingEdit by editor.saving.collectAsStateWithLifecycle()
    val editError by editor.error.collectAsStateWithLifecycle()
    val editedText by editor.draft.collectAsStateWithLifecycle()
    val whisper = remember(context) { SherpaWhisperAsrEngine(context) }
    var activeCorrectionCapture by remember { mutableStateOf<OfflineDictationCapture?>(null) }
    var recordingCorrection by remember { mutableStateOf(false) }
    var transcribingCorrection by remember { mutableStateOf(false) }
    var voiceCorrectionReady by remember { mutableStateOf(false) }
    var voiceCorrectionError by remember { mutableStateOf<String?>(null) }

    fun resetVoiceCorrection() {
        activeCorrectionCapture?.requestStop()
        activeCorrectionCapture = null
        recordingCorrection = false
        transcribingCorrection = false
        voiceCorrectionReady = false
        voiceCorrectionError = null
    }

    fun cancelEdit() {
        resetVoiceCorrection()
        editor.cancel()
    }

    fun startVoiceCorrection() {
        if (!whisper.isAvailable) {
            voiceCorrectionError = "El dictado local no está disponible. Revisa el modelo de voz en Ajustes."
            return
        }
        voiceCorrectionError = null
        val capture = OfflineDictationCapture(context)
        activeCorrectionCapture = capture
        scope.launch {
            try {
                recordingCorrection = true
                val window = capture.capture(maxDurationMs = 20_000L)
                if (window == null || window.durationMs() < 300L) {
                    voiceCorrectionError = "No he captado audio suficiente."
                    return@launch
                }
                transcribingCorrection = true
                val transcript = withContext(Dispatchers.IO) {
                    whisper.transcribe(window, languageTag = "es")?.text?.trim()
                }
                if (transcript.isNullOrBlank()) {
                    voiceCorrectionError = "No he podido transcribir la explicación."
                } else {
                    editor.change(transcript)
                    voiceCorrectionReady = true
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                voiceCorrectionError = "No he podido procesar la explicación."
            } finally {
                recordingCorrection = false
                transcribingCorrection = false
                activeCorrectionCapture = null
            }
        }
    }

    val correctionPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceCorrection()
        } else {
            voiceCorrectionError = "Necesito permiso de micrófono para escuchar la explicación."
        }
    }

    fun requestVoiceCorrection() {
        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startVoiceCorrection()
        } else {
            correctionPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(isEditing) {
        if (!isEditing) resetVoiceCorrection()
    }
    DisposableEffect(Unit) {
        onDispose { activeCorrectionCapture?.requestStop() }
    }
    BackHandler(enabled = isEditing) { cancelEdit() }

    when (entryState) {
        EntryDetailUiState.Loading -> {
            DetailStateScaffold(onBack) { CircularProgressIndicator() }
            return
        }
        EntryDetailUiState.NotFound -> {
            DetailStateScaffold(onBack) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Esta entrada ya no está disponible.", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Puede que se haya eliminado.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return
        }
        is EntryDetailUiState.Success -> Unit
    }

    val entry = (entryState as EntryDetailUiState.Success).entry
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale("es")) }
    val quickAction = remember(entry.actionType, entry.displayText, entry.dueDate, entry.status) {
        EntryActionBridge.build(entry)
    }
    var editingCalendarAction by remember(entry.id) { mutableStateOf<SuggestedAction?>(null) }
    var pendingCalendarAction by remember(entry.id) { mutableStateOf<SuggestedAction?>(null) }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val requestedAction = pendingCalendarAction
        // Calendar's own pre-filled editor remains available without provider
        // permission, so the task must never lose its action after a denial.
        editingCalendarAction = requestedAction
        pendingCalendarAction = null
    }

    fun shareEntry() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, entry.displayText)
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(intent, "Compartir"))
    }

    fun launchQuickAction() {
        val action = quickAction?.action ?: return
        if (action.type == ActionType.CALENDAR_EVENT || action.type == ActionType.REMINDER) {
            if (CalendarHelper.hasWriteCalendarPermission(context)) {
                editingCalendarAction = action
            } else {
                pendingCalendarAction = action
                calendarPermissionLauncher.launch(
                    arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                )
            }
        } else {
            ActionExecutor.execute(context, action)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Editar" else "Detalle") },
                navigationIcon = {
                    IconButton(onClick = { if (isEditing) cancelEdit() else onBack() }) {
                        Icon(
                            if (isEditing) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isEditing) "Cancelar edición" else "Volver"
                        )
                    }
                },
                actions = {
                    if (isEditing) {
                        IconButton(
                            onClick = {
                                editor.save(entryId, requireLocalModel = voiceCorrectionReady)
                            },
                            enabled = editedText.isNotBlank() &&
                                !savingEdit && !recordingCorrection && !transcribingCorrection
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = if (voiceCorrectionReady) {
                                    "Guardar y volver a analizar"
                                } else {
                                    "Guardar cambios"
                                }
                            )
                        }
                    } else {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Compartir") },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        shareEntry()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Eliminar") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            EntryHeadline(entry, dateFormat)
            Spacer(Modifier.height(16.dp))
            if (isEditing) {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = editor::change,
                    enabled = !savingEdit,
                    isError = editError != null,
                    supportingText = { editError?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    textStyle = MaterialTheme.typography.titleLarge
                )
                if (entry.contentKind == EntryContentKind.MEMORY || entry.contentKind == EntryContentKind.ACTION) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Si el texto no refleja lo que querías decir, explícalo con tu voz. " +
                            "Podrás revisar la transcripción antes de que el modelo local reconstruya la tarea.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            if (recordingCorrection) {
                                activeCorrectionCapture?.requestStop()
                            } else {
                                requestVoiceCorrection()
                            }
                        },
                        enabled = !savingEdit && !transcribingCorrection,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (transcribingCorrection) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(18.dp).height(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                if (recordingCorrection) Icons.Default.Close else Icons.Default.Mic,
                                contentDescription = null
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                transcribingCorrection -> "Transcribiendo explicación…"
                                recordingCorrection -> "Detener explicación"
                                else -> "Explicar la corrección por voz"
                            }
                        )
                    }
                    voiceCorrectionError?.let { error ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (voiceCorrectionReady) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Explicación transcrita. Revísala y corrígela si hace falta.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { editor.save(entryId, requireLocalModel = true) },
                        enabled = editedText.isNotBlank() &&
                            !savingEdit && !recordingCorrection && !transcribingCorrection,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (savingEdit) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(18.dp).height(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (savingEdit) "Analizando…" else "Guardar y volver a analizar")
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editor.start(entry.displayText) }
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        entry.displayText,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Toca el texto para editarlo o vuelve a explicarlo con tu voz.",
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalTramaColors.current.mutedText
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        editor.start(entry.displayText)
                        requestVoiceCorrection()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Re-explicar con voz")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { editor.start(entry.displayText) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Corregir el texto")
                }
            }

            entry.sourceRecordingId?.let { recordingId ->
                Spacer(Modifier.height(16.dp))
                SoftCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRecordingClick(recordingId) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Reunión de origen", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Consulta la transcripción y las notas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            EntryStateActions(
                entry = entry,
                quickActionLabel = quickAction?.let { externalActionLabel(it.action.type) },
                quickActionIcon = quickAction?.icon,
                onQuickAction = ::launchQuickAction,
                onAccept = {
                    scope.launch { repository.confirmSuggested(entryId, EntryVerificationSource.DETAIL) }
                },
                onDiscard = {
                    scope.launch {
                        try {
                            repository.markDiscarded(entryId)
                            onBack()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                "No se ha podido descartar la sugerencia",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onComplete = { scope.launch { repository.markCompleted(entryId) } },
                onRestore = { scope.launch { repository.markPending(entryId) } }
            )

            Spacer(Modifier.height(24.dp))
            Text("Información", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    DetailRow("Creada", dateFormat.format(Date(entry.createdAt)))
                    entry.dueDate?.let { DetailRow("Para", dateFormat.format(Date(it))) }
                    DetailRow("Origen", if (entry.source == Source.PHONE) "Teléfono" else "Reloj")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    fun deleteEntry(reason: DeletionFeedbackStore.Reason?) {
        scope.launch {
            runCatching {
                repository.deleteById(entryId)
                com.trama.app.diagnostics.CaptureLog.logUserDelete(
                    entryId = entry.id,
                    text = entry.displayText.ifBlank { entry.text },
                    createdAtMs = entry.createdAt,
                    status = entry.status,
                    actionType = entry.actionType,
                    isManual = entry.isManual,
                    wasCompleted = entry.completedAt != null,
                    hadDueDate = entry.dueDate != null,
                    source = "detail_screen",
                    reason = reason?.storageKey,
                    learningEnabled = learnFromDeletions
                )
                if (learnFromDeletions && reason != null) {
                    DeletionFeedbackStore.record(context, entry.displayText, reason)
                }
            }.onSuccess {
                onBack()
            }.onFailure {
                Toast.makeText(context, "No se ha podido eliminar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showDeleteDialog && learnFromDeletions) {
        DeleteReasonDialog(
            entryCount = 1,
            onDismiss = { showDeleteDialog = false },
            onConfirm = { reason ->
                showDeleteDialog = false
                deleteEntry(reason)
            }
        )
    } else if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar entrada") },
            text = { Text("Esta entrada desaparecerá de Trama.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    deleteEntry(null)
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }

    editingCalendarAction?.let { action ->
        com.trama.app.ui.components.EntryCalendarActionDialog(
            entryId = entryId,
            action = action,
            onDismiss = { editingCalendarAction = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailStateScaffold(onBack: () -> Unit, content: @Composable () -> Unit) {
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
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            contentAlignment = Alignment.Center
        ) { content() }
    }
}

@Composable
private fun EntryHeadline(entry: DiaryEntry, dateFormat: SimpleDateFormat) {
    Column {
        val accent = when (entry.priority) {
            EntryPriority.URGENT -> MaterialTheme.colorScheme.error
            EntryPriority.HIGH -> LocalTramaColors.current.warn
            else -> MaterialTheme.colorScheme.primary
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(6.dp), color = accent.copy(alpha = 0.14f)) {
                Text(
                    EntryActionType.label(entry.actionType).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                statusLabel(entry.status),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        entry.dueDate?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "Para ${dateFormat.format(Date(it))}",
                style = MaterialTheme.typography.labelMedium,
                color = accent
            )
        }
    }
}

@Composable
private fun EntryStateActions(
    entry: DiaryEntry,
    quickActionLabel: String?,
    quickActionIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    onQuickAction: () -> Unit,
    onAccept: () -> Unit,
    onDiscard: () -> Unit,
    onComplete: () -> Unit,
    onRestore: () -> Unit
) {
    when (entry.status) {
        EntryStatus.SUGGESTED -> {
            Text("¿Quieres conservar esta acción?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Confírmala para poder realizarla o marcarla como hecha.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDiscard, modifier = Modifier.weight(1f)) { Text("Descartar") }
                Button(onClick = onAccept, modifier = Modifier.weight(1f)) { Text("Añadir a pendientes") }
            }
        }
        EntryStatus.PENDING -> {
            Text("Acciones", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            if (quickActionLabel != null && quickActionIcon != null) {
                Button(onClick = onQuickAction, modifier = Modifier.fillMaxWidth()) {
                    Icon(quickActionIcon, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(quickActionLabel)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
                    Text("Marcar como hecha")
                }
            } else {
                Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
                    Text("Marcar como hecha")
                }
            }
        }
        EntryStatus.COMPLETED -> {
            FilledTonalButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) { Text("Reabrir") }
        }
        EntryStatus.DISCARDED -> {
            FilledTonalButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                Text("Recuperar como pendiente")
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun statusLabel(status: String): String = when (status) {
    EntryStatus.SUGGESTED -> "Por confirmar"
    EntryStatus.COMPLETED -> "Completada"
    EntryStatus.DISCARDED -> "Descartada"
    else -> "Pendiente"
}

private fun externalActionLabel(type: ActionType): String = when (type) {
    ActionType.CALENDAR_EVENT -> "Añadir a Calendar"
    ActionType.REMINDER -> "Crear recordatorio"
    ActionType.NOTE -> "Abrir en Keep"
    ActionType.CALL -> "Programar llamada"
    ActionType.MESSAGE -> "Abrir en Gmail"
    ActionType.TODO -> "Añadir al calendario"
}
