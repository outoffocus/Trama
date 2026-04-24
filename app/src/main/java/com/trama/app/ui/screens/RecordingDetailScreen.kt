package com.trama.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LightbulbCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trama.app.summary.ActionExecutor
import com.trama.app.summary.ActionType
import com.trama.app.summary.CalendarHelper
import com.trama.app.summary.EntryActionBridge
import com.trama.app.summary.RecordingProcessor
import com.trama.app.summary.SuggestedAction
import com.trama.app.ui.components.CalendarActionDialog
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryStatus
import com.trama.shared.model.Recording
import com.trama.shared.model.RecordingStatus
import com.trama.shared.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingDetailScreen(
    recordingId: Long,
    onBack: () -> Unit,
    onActionClick: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { DatabaseProvider.getRepository(context) }
    val scope = rememberCoroutineScope()

    val recording by repository.getRecordingById(recordingId).collectAsState(initial = null)
    val actions by repository.getByRecordingId(recordingId).collectAsState(initial = emptyList())

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
    val json = remember { Json { ignoreUnknownKeys = true } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(recording?.title ?: "Grabación") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
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
                            rec.processingStatus == RecordingStatus.PENDING) {
                            IconButton(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    RecordingProcessor(context).process(recordingId, repository)
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
                        IconButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                repository.deleteRecording(recordingId)
                            }
                            onBack()
                        }) {
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header info ──
            item(key = "header") {
                RecordingHeader(rec, dateFormat)
            }

            // ── Status badge ──
            item(key = "status") {
                StatusBadge(rec)
            }

            // ── Summary ──
            if (rec.summary != null) {
                item(key = "summary") {
                    SectionCard(title = "Resumen") {
                        Text(
                            text = rec.summary!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // ── Key Points ──
            val keyPoints = rec.keyPoints?.let { kp ->
                try {
                    json.decodeFromString<List<String>>(kp)
                } catch (_: Exception) { null }
            }
            if (!keyPoints.isNullOrEmpty()) {
                item(key = "keypoints") {
                    SectionCard(title = "Puntos clave") {
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
            if (actions.isNotEmpty()) {
                val suggested = actions.filter { it.status == EntryStatus.SUGGESTED }
                val accepted = actions.filter { it.status != EntryStatus.SUGGESTED }

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
                            "Acciones extraídas",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        if (suggested.isNotEmpty()) {
                            TextButton(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    suggested.forEach { repository.markPending(it.id) }
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
                            onAccept = { scope.launch(Dispatchers.IO) { repository.markPending(action.id) } },
                            onDismiss = { scope.launch(Dispatchers.IO) { repository.markDiscarded(action.id) } }
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

            // ── Transcription ──
            item(key = "transcription") {
                SectionCard(title = "Transcripción completa") {
                    Text(
                        text = rec.transcription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun RecordingHeader(recording: Recording, dateFormat: SimpleDateFormat) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (recording.source == Source.WATCH) Icons.Default.Watch
                          else Icons.Default.Mic,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Text(
            text = dateFormat.format(Date(recording.createdAt)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Text(
            text = "·",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
        val min = recording.durationSeconds / 60
        val sec = recording.durationSeconds % 60
        Text(
            text = "%d:%02d".format(min, sec),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun StatusBadge(recording: Recording) {
    val status = recording.processingStatus
    val (icon, label, color) = when {
        status == RecordingStatus.COMPLETED && recording.processedBy == "LOCAL" ->
            Triple(Icons.Default.CheckCircle, "Procesado con modelo local", MaterialTheme.colorScheme.tertiary)
        status == RecordingStatus.COMPLETED ->
            Triple(Icons.Default.CheckCircle, "Procesado con Gemini", MaterialTheme.colorScheme.primary)
        status == RecordingStatus.PROCESSING ->
            Triple(Icons.Default.Schedule, "Procesando...", MaterialTheme.colorScheme.tertiary)
        status == RecordingStatus.FAILED ->
            Triple(Icons.Default.Error, "Error al procesar", MaterialTheme.colorScheme.error)
        else ->
            Triple(Icons.Default.Schedule, "Pendiente", MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (status == RecordingStatus.PROCESSING) {
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
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    ) { result ->
        val granted = result[Manifest.permission.READ_CALENDAR] == true &&
            result[Manifest.permission.WRITE_CALENDAR] == true
        if (granted) {
            editingCalendarAction = pendingCalendarAction
        }
        pendingCalendarAction = null
    }

    editingCalendarAction?.let { action ->
        val isReminder = action.type == ActionType.REMINDER
        CalendarActionDialog(
            action = action,
            dialogTitle = if (isReminder) "Crear recordatorio" else "Añadir al calendario",
            confirmLabel = if (isReminder) "Crear" else "Añadir",
            onDismiss = { editingCalendarAction = null },
            onConfirm = { title, description, date, time, calendarId ->
                val datetime = "${date}T${time}"
                val updatedAction = action.copy(title = title, description = description, datetime = datetime)
                val startMillis = try {
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()).parse(datetime)?.time
                } catch (_: Exception) { null }

                if (startMillis != null && calendarId != null) {
                    CalendarHelper.insertEventInCalendar(
                        context = context,
                        calendarId = calendarId,
                        title = title,
                        description = description.ifBlank { null },
                        startMillis = startMillis,
                        reminderMinutes = if (isReminder) 15 else 0
                    )
                } else {
                    CalendarHelper.insertEventFromAction(context, updatedAction, isReminder = isReminder)
                }
                editingCalendarAction = null
            }
        )
    }

    ActionItemCard(
        entry = entry,
        isSuggested = isSuggested,
        duplicateOfText = duplicateOfText,
        onClick = onClick,
        onAccept = onAccept,
        onDismiss = onDismiss,
        onQuickActionClick = quickAction
            ?.takeIf { action ->
                action.action.type == ActionType.CALENDAR_EVENT ||
                    action.action.type == ActionType.REMINDER ||
                    action.action.type == ActionType.TODO
            }
            ?.let { action ->
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
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Descartar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
                IconButton(onClick = onAccept, modifier = Modifier.size(32.dp)) {
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
                IconButton(onClick = onQuickActionClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = "Añadir al calendario",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
