package com.trama.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Place
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.trama.app.summary.ActionExecutor
import com.trama.app.summary.ActionType
import com.trama.app.summary.CalendarHelper
import com.trama.app.summary.CalendarHelper.CalendarEvent
import com.trama.app.summary.EntryActionBridge
import com.trama.app.service.EntryProcessingState
import com.trama.app.location.DwellDurationFormatter
import com.trama.app.ui.components.CalendarActionDialog
import com.trama.app.ui.components.EntryCard
import com.trama.app.ui.components.RecordingCard
import com.trama.app.ui.components.SwipeableReminderCard
import com.trama.app.ui.theme.TimelineAccentConfig
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryStatus
import com.trama.shared.model.Recording
import com.trama.shared.model.TimelineEvent
import com.trama.shared.model.TimelineEventSource
import com.trama.shared.model.TimelineEventType
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface TimelineEventUi {
    val id: String
    val timestamp: Long

    data class EntryCreated(
        val entry: DiaryEntry
    ) : TimelineEventUi {
        override val id: String = "entry_created_${entry.id}"
        override val timestamp: Long = entry.createdAt
    }

    data class EntryCompleted(
        val entry: DiaryEntry
    ) : TimelineEventUi {
        override val id: String = "entry_completed_${entry.id}_${entry.completedAt ?: entry.createdAt}"
        override val timestamp: Long = entry.completedAt ?: entry.createdAt
    }

    data class RecordingCaptured(
        val recording: Recording
    ) : TimelineEventUi {
        override val id: String = "recording_${recording.id}"
        override val timestamp: Long = recording.createdAt
    }

    data class CalendarScheduled(
        val calendarEvent: CalendarEvent
    ) : TimelineEventUi {
        override val id: String = "calendar_${calendarEvent.id}_${calendarEvent.startMillis}"
        override val timestamp: Long = calendarEvent.startMillis
    }

    data class StoredEvent(
        val event: TimelineEvent
    ) : TimelineEventUi {
        override val id: String = "stored_event_${event.id}"
        override val timestamp: Long = event.timestamp
    }
}

internal fun buildTimelineEvents(
    createdEntries: List<DiaryEntry>,
    completedEntries: List<DiaryEntry>,
    recordings: List<Recording>,
    calendarEvents: List<CalendarEvent> = emptyList(),
    storedEvents: List<TimelineEvent> = emptyList()
): List<TimelineEventUi> {
    val events = buildList {
        createdEntries.forEach { entry ->
            add(TimelineEventUi.EntryCreated(entry))
        }
        completedEntries
            .filter { it.completedAt != null }
            .forEach { entry -> add(TimelineEventUi.EntryCompleted(entry)) }
        recordings.forEach { add(TimelineEventUi.RecordingCaptured(it)) }
        calendarEvents.distinctBy { "${it.id}_${it.startMillis}" }.forEach { add(TimelineEventUi.CalendarScheduled(it)) }
        storedEvents.forEach { add(TimelineEventUi.StoredEvent(it)) }
    }

    return events.sortedBy { it.timestamp }
}

@Composable
internal fun TimelineList(
    events: List<TimelineEventUi>,
    processingEntryIds: Set<Long>,
    processingBackends: Map<Long, EntryProcessingState.Backend> = emptyMap(),
    accentConfig: TimelineAccentConfig,
    onEntryClick: (Long) -> Unit,
    onRecordingClick: (Long) -> Unit,
    onPlaceClick: (Long) -> Unit = {},
    onToggleComplete: ((DiaryEntry) -> Unit)? = null,
    onPostponeEntry: ((DiaryEntry, Long, String) -> Unit)? = null,
    isSelectionMode: Boolean = false,
    selectedEntryIds: Set<Long> = emptySet(),
    onEntrySelectionChange: ((Long, Boolean) -> Unit)? = null,
    onEnterEntrySelectionMode: ((Long) -> Unit)? = null,
    selectedRecordingIds: Set<Long> = emptySet(),
    onRecordingSelectionChange: ((Long, Boolean) -> Unit)? = null,
    onEnterRecordingSelectionMode: ((Long) -> Unit)? = null,
    selectedEventIds: Set<Long> = emptySet(),
    onEventSelectionChange: ((Long, Boolean) -> Unit)? = null,
    onEnterEventSelectionMode: ((Long) -> Unit)? = null,
    keyPrefix: String = "",
    modifier: Modifier = Modifier,
    emptyTitle: String = "No hay eventos",
    emptyBody: String = "Todavía no se ha registrado nada en este día."
) {
    val hourFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    if (events.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = emptyTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = emptyBody,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        timelineListContent(
            events = events,
            processingEntryIds = processingEntryIds,
            processingBackends = processingBackends,
            hourFormat = hourFormat,
            accentConfig = accentConfig,
            itemModifier = Modifier,
            keyPrefix = keyPrefix,
            onEntryClick = onEntryClick,
            onRecordingClick = onRecordingClick,
            onPlaceClick = onPlaceClick,
            onToggleComplete = onToggleComplete,
            onPostponeEntry = onPostponeEntry,
            isSelectionMode = isSelectionMode,
            selectedEntryIds = selectedEntryIds,
            onEntrySelectionChange = onEntrySelectionChange,
            onEnterEntrySelectionMode = onEnterEntrySelectionMode,
            selectedRecordingIds = selectedRecordingIds,
            onRecordingSelectionChange = onRecordingSelectionChange,
            onEnterRecordingSelectionMode = onEnterRecordingSelectionMode,
            selectedEventIds = selectedEventIds,
            onEventSelectionChange = onEventSelectionChange,
            onEnterEventSelectionMode = onEnterEventSelectionMode
        )
    }
}

internal fun LazyListScope.timelineListContent(
    events: List<TimelineEventUi>,
    processingEntryIds: Set<Long>,
    processingBackends: Map<Long, EntryProcessingState.Backend> = emptyMap(),
    hourFormat: SimpleDateFormat,
    accentConfig: TimelineAccentConfig,
    itemModifier: Modifier = Modifier,
    keyPrefix: String = "",
    onEntryClick: (Long) -> Unit,
    onRecordingClick: (Long) -> Unit,
    onPlaceClick: (Long) -> Unit = {},
    onToggleComplete: ((DiaryEntry) -> Unit)? = null,
    onPostponeEntry: ((DiaryEntry, Long, String) -> Unit)? = null,
    isSelectionMode: Boolean = false,
    selectedEntryIds: Set<Long> = emptySet(),
    onEntrySelectionChange: ((Long, Boolean) -> Unit)? = null,
    onEnterEntrySelectionMode: ((Long) -> Unit)? = null,
    selectedRecordingIds: Set<Long> = emptySet(),
    onRecordingSelectionChange: ((Long, Boolean) -> Unit)? = null,
    onEnterRecordingSelectionMode: ((Long) -> Unit)? = null,
    selectedEventIds: Set<Long> = emptySet(),
    onEventSelectionChange: ((Long, Boolean) -> Unit)? = null,
    onEnterEventSelectionMode: ((Long) -> Unit)? = null,
) {
    items(
        count = events.size,
        key = { index -> "$keyPrefix${events[index].id}" }
    ) { index ->
        val event = events[index]
        val previousEvent = events.getOrNull(index - 1)
        if (previousEvent == null || previousEvent.timestamp.hourBucket() != event.timestamp.hourBucket()) {
            TimelineTimeMarker(
                text = hourFormat.format(Date(event.timestamp.hourStartMillis())),
                modifier = Modifier.padding(top = if (index == 0) 2.dp else 8.dp, bottom = 2.dp)
            )
        }
        when (event) {
            is TimelineEventUi.EntryCreated -> {
                val context = LocalContext.current
                val quickAction = remember(
                    event.entry.id,
                    event.entry.actionType,
                    event.entry.displayText,
                    event.entry.dueDate,
                    event.entry.status
                ) {
                    EntryActionBridge.build(event.entry)
                }
                var editingCalendarAction by remember(event.entry.id) { mutableStateOf<com.trama.app.summary.SuggestedAction?>(null) }
                var pendingCalendarAction by remember(event.entry.id) { mutableStateOf<com.trama.app.summary.SuggestedAction?>(null) }
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
                SwipeableReminderCard(
                    entry = event.entry,
                    enabled = event.entry.status == EntryStatus.PENDING &&
                        !isSelectionMode &&
                        onToggleComplete != null &&
                        onPostponeEntry != null,
                    onMarkDone = { onToggleComplete?.invoke(event.entry) },
                    onPostponeSelected = { dueDate, label ->
                        onPostponeEntry?.invoke(event.entry, dueDate, label)
                    }
                ) {
                    EntryCard(
                        modifier = itemModifier,
                        entry = event.entry,
                        accentColor = accentConfig.pending,
                        quickActionLabel = quickAction?.label,
                        quickActionIcon = quickAction?.icon,
                        onClick = {
                            if (isSelectionMode && onEntrySelectionChange != null) {
                                onEntrySelectionChange(
                                    event.entry.id,
                                    event.entry.id !in selectedEntryIds
                                )
                            } else {
                                onEntryClick(event.entry.id)
                            }
                        },
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
                        },
                        onLongClick = if (onEnterEntrySelectionMode != null && !isSelectionMode) {
                            { onEnterEntrySelectionMode(event.entry.id) }
                        } else null,
                        onToggleComplete = if (event.entry.status == EntryStatus.PENDING && onToggleComplete != null) {
                            { onToggleComplete(event.entry) }
                        } else null,
                        isProcessing = event.entry.id in processingEntryIds,
                        processingBackend = processingBackends[event.entry.id],
                        isSelectionMode = isSelectionMode,
                        isSelected = event.entry.id in selectedEntryIds
                    )
                }
            }
            is TimelineEventUi.EntryCompleted -> {
                val isSelected = event.entry.id in selectedEntryIds
                TimelineStatusCard(
                    modifier = itemModifier,
                    eyebrow = "Completada",
                    title = event.entry.displayText,
                    body = "Marcada como resuelta",
                    accent = accentConfig.completed,
                    meta = null,
                    isSelectionMode = isSelectionMode,
                    isSelected = isSelected,
                    onLongClick = if (onEnterEntrySelectionMode != null && !isSelectionMode) {
                        { onEnterEntrySelectionMode(event.entry.id) }
                    } else null,
                    onClick = if (isSelectionMode && onEntrySelectionChange != null) {
                        { onEntrySelectionChange(event.entry.id, !isSelected) }
                    } else null,
                    icon = {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = accentConfig.completed
                        )
                    }
                )
            }
            is TimelineEventUi.RecordingCaptured -> {
                val isSelected = event.recording.id in selectedRecordingIds
                RecordingCard(
                    modifier = itemModifier,
                    recording = event.recording,
                    accentColor = accentConfig.recording,
                    isSelectionMode = isSelectionMode,
                    isSelected = isSelected,
                    onLongClick = if (onEnterRecordingSelectionMode != null && !isSelectionMode) {
                        { onEnterRecordingSelectionMode(event.recording.id) }
                    } else null,
                    onClick = {
                        if (isSelectionMode && onRecordingSelectionChange != null) {
                            onRecordingSelectionChange(event.recording.id, !isSelected)
                        } else {
                            onRecordingClick(event.recording.id)
                        }
                    }
                )
            }
            is TimelineEventUi.CalendarScheduled -> {
                val calendarEvent = event.calendarEvent
                TimelineStatusCard(
                    modifier = itemModifier,
                    eyebrow = if (calendarEvent.allDay) "Calendario · todo el día" else "Calendario",
                    title = calendarEvent.title,
                    body = "",
                    accent = accentConfig.calendar,
                    meta = null,
                    icon = {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = accentConfig.calendar
                        )
                    }
                )
            }
            is TimelineEventUi.StoredEvent -> {
                val title = event.event.title
                val isSelected = event.event.id in selectedEventIds
                if (event.event.type == TimelineEventType.CALENDAR) {
                    TimelineStatusCard(
                        modifier = itemModifier,
                        eyebrow = if (event.event.source == TimelineEventSource.CALENDAR_IMPORT) "Google Calendar" else "Calendario",
                        title = title,
                        body = "",
                        accent = accentConfig.calendar,
                        meta = null,
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        onLongClick = if (onEnterEventSelectionMode != null && !isSelectionMode) {
                            { onEnterEventSelectionMode(event.event.id) }
                        } else null,
                        onClick = if (isSelectionMode && onEventSelectionChange != null) {
                            { onEventSelectionChange(event.event.id, !isSelected) }
                        } else null,
                        icon = {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = accentConfig.calendar
                            )
                        }
                    )
                    return@items
                }
                val accent = if (event.event.isHighlight) accentConfig.place
                             else accentConfig.place.copy(alpha = 0.82f)
                TimelineStatusCard(
                    modifier = itemModifier,
                    eyebrow = when (event.event.type) {
                        TimelineEventType.DWELL -> if (event.event.isHighlight) "Lugar nuevo" else "Lugar"
                        else -> "Evento"
                    },
                    title = title,
                    body = if (event.event.type == TimelineEventType.DWELL) {
                        DwellDurationFormatter.formatHours(event.event.timestamp, event.event.endTimestamp)
                    } else {
                        event.event.subtitle ?: "Evento automático"
                    },
                    accent = accent,
                    meta = null,
                    isSelectionMode = isSelectionMode,
                    isSelected = isSelected,
                    onLongClick = if (onEnterEventSelectionMode != null && !isSelectionMode) {
                        { onEnterEventSelectionMode(event.event.id) }
                    } else null,
                    onClick = if (isSelectionMode && onEventSelectionChange != null) {
                        { onEventSelectionChange(event.event.id, !isSelected) }
                    } else {
                        event.event.placeId?.let { placeId -> { onPlaceClick(placeId) } }
                    },
                    icon = {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = accentConfig.place
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun TimelineTimeMarker(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f))
        )
        Surface(
            modifier = Modifier.padding(horizontal = 10.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f))
        )
    }
}

private fun Long.hourBucket(): Long = this / 3_600_000L

private fun Long.hourStartMillis(): Long = hourBucket() * 3_600_000L

@Composable
private fun TimelineCornerAccent(
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(4.dp)
            .width(20.dp)
            .background(color = color.copy(alpha = 0.75f), shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineStatusCard(
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    secondaryEyebrow: String? = null,
    title: String,
    body: String,
    accent: Color,
    meta: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    quickActionLabel: String? = null,
    onQuickActionClick: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    icon: @Composable () -> Unit
) {
    // Body, secondary eyebrow and inline icon are deliberately dropped: detail
    // information lives in detail screens. We delegate to the unified TramaCard
    // anatomy (stripe + eyebrow + title + meta).
    com.trama.app.ui.components.TramaCard(
        eyebrow = eyebrow ?: "",
        title = title,
        accent = accent,
        modifier = modifier,
        meta = meta,
        selected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick,
        leading = if (isSelectionMode) {
            {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null,
                    modifier = Modifier.size(28.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                )
            }
        } else null,
        trailing = if (!quickActionLabel.isNullOrBlank() && onQuickActionClick != null && !isSelectionMode) {
            {
                Surface(
                    onClick = onQuickActionClick,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
                    color = accent.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = quickActionLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        } else null,
    )
    @Suppress("UNUSED_EXPRESSION") body  // body intentionally unused
    @Suppress("UNUSED_EXPRESSION") secondaryEyebrow
    @Suppress("UNUSED_EXPRESSION") icon
}

@Composable
private fun TimelineLabelChip(
    text: String,
    accent: Color,
    strong: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp),
        color = accent.copy(alpha = if (strong) 0.14f else 0.08f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (strong) FontWeight.Medium else FontWeight.Normal,
            color = if (strong) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun TimelineEvent.calendarDisplayName(): String? {
    val fromPayload = dataJson?.let { payload ->
        runCatching { JSONObject(payload).optString("calendarName").trim() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }
    return (fromPayload ?: subtitle?.lineSequence()?.firstOrNull()?.substringBefore(" · "))
        ?.stripCalendarAccount()
        ?.takeIf { it.isNotBlank() }
}

private fun TimelineEvent.subtitleWithoutCalendarName(calendarName: String?): String? {
    val subtitleValue = subtitle?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val lines = subtitleValue.lines().toMutableList()
    val firstLine = lines.firstOrNull()?.trim().orEmpty()
    val firstParts = firstLine.split(" · ").map { it.trim() }.filter { it.isNotBlank() }
    val visibleParts = firstParts.filterNot { part ->
        val cleanPart = part.stripCalendarAccount()
        cleanPart == calendarName || part.contains("@")
    }
    if (lines.isNotEmpty()) {
        lines[0] = visibleParts.joinToString(" · ")
    }
    return lines
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString("\n")
        .takeIf { it.isNotBlank() }
}

private fun String.stripCalendarAccount(): String {
    return replace(Regex("\\s*\\([^)]*@[^)]*\\)\\s*$"), "").trim()
}
