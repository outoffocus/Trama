package com.trama.app.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trama.app.ui.components.SwipeableReminderCard
import com.trama.app.ui.theme.LocalTramaColors
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryStatus
import com.trama.shared.model.TimelineEvent
import com.trama.shared.model.TimelineEventType
import com.trama.shared.util.DayRange
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class AgendaDayBucket(
    val dayStartMs: Long,
    val label: String,
    val events: List<TimelineEvent>,
    val tasks: List<DiaryEntry>
)

private data class AgendaSection(
    val title: String,
    val rangeLabel: String,
    val days: List<AgendaDayBucket>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(
    onBack: () -> Unit,
    onEntryClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { DatabaseProvider.getRepository(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val today = remember { DayRange.today() }
    val endOfThisWeek = remember(today) { endOfWeekMs(today.startMs) }
    val startOfNextWeek = remember(endOfThisWeek) { endOfThisWeek + 1 }
    val endOfNextWeek = remember(startOfNextWeek) {
        startOfNextWeek + 6L * 24 * 3_600_000L + (23 * 60 + 59) * 60_000L + 59_999L
    }
    val rangeFar = endOfNextWeek + 1

    val pending by repository.getPending().collectAsState(initial = emptyList())
    val thisWeekEvents by repository
        .getTimelineEventsByDateRange(today.startMs, endOfThisWeek)
        .collectAsState(initial = emptyList())
    val nextWeekEvents by repository
        .getTimelineEventsByDateRange(startOfNextWeek, endOfNextWeek)
        .collectAsState(initial = emptyList())

    val thisWeekSection = remember(pending, thisWeekEvents, today, endOfThisWeek) {
        buildSection(
            title = "Esta semana",
            rangeStart = today.startMs,
            rangeEnd = endOfThisWeek,
            events = thisWeekEvents,
            pending = pending
        )
    }
    val nextWeekSection = remember(pending, nextWeekEvents, startOfNextWeek, endOfNextWeek) {
        buildSection(
            title = "Próxima semana",
            rangeStart = startOfNextWeek,
            rangeEnd = endOfNextWeek,
            events = nextWeekEvents,
            pending = pending
        )
    }
    val laterTasks = remember(pending, rangeFar) {
        pending
            .filter { (it.dueDate ?: Long.MIN_VALUE) >= rangeFar }
            .sortedBy { it.dueDate ?: 0L }
    }
    val undatedTasks = remember(pending) {
        pending.filter { it.dueDate == null }.sortedByDescending { it.createdAt }
    }
    val overdueTasks = remember(pending, today) {
        pending
            .filter { (it.dueDate ?: Long.MAX_VALUE) < today.startMs }
            .sortedBy { it.dueDate ?: 0L }
    }

    val totalCalendarThisWeek = thisWeekEvents.count { it.type == TimelineEventType.CALENDAR }
    val totalTasksThisWeek = thisWeekSection.days.sumOf { it.tasks.size }
    val urgent = overdueTasks.size +
        thisWeekSection.days.sumOf { day ->
            day.tasks.count { it.priority.equals("URGENT", ignoreCase = true) }
        }

    fun markEntryCompleted(entry: DiaryEntry) {
        scope.launch {
            repository.markCompleted(entry.id)
            val result = snackbarHostState.showSnackbar(
                message = "Marcada como hecha",
                actionLabel = "Deshacer",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                repository.markPending(entry.id)
            }
        }
    }

    fun postponeEntry(entry: DiaryEntry, dueDate: Long) {
        scope.launch {
            val previousDue = entry.dueDate
            repository.updateDueDate(entry.id, dueDate)
            val result = snackbarHostState.showSnackbar(
                message = "Pospuesta",
                actionLabel = "Deshacer",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                repository.updateDueDate(entry.id, previousDue)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Agenda") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item("stats") {
                AgendaStatsCard(
                    calendarCount = totalCalendarThisWeek,
                    taskCount = totalTasksThisWeek,
                    overdueCount = overdueTasks.size,
                    urgentCount = urgent
                )
            }

            if (overdueTasks.isNotEmpty()) {
                item("overdue_header") { SectionTitle("Vencidas", "${overdueTasks.size}") }
                items(overdueTasks, key = { "ov_${it.id}" }) { task ->
                    SwipeableTaskRow(
                        entry = task,
                        onClick = { onEntryClick(task.id) },
                        onComplete = { markEntryCompleted(task) },
                        onPostpone = { dueDate -> postponeEntry(task, dueDate) },
                        overdue = true
                    )
                }
            }

            agendaSection(
                section = thisWeekSection,
                onEntryClick = onEntryClick,
                onComplete = ::markEntryCompleted,
                onPostpone = ::postponeEntry
            )
            agendaSection(
                section = nextWeekSection,
                onEntryClick = onEntryClick,
                onComplete = ::markEntryCompleted,
                onPostpone = ::postponeEntry
            )

            if (laterTasks.isNotEmpty()) {
                item("later_header") { SectionTitle("Más adelante", "${laterTasks.size}") }
                items(laterTasks, key = { "later_${it.id}" }) { task ->
                    SwipeableTaskRow(
                        entry = task,
                        onClick = { onEntryClick(task.id) },
                        onComplete = { markEntryCompleted(task) },
                        onPostpone = { dueDate -> postponeEntry(task, dueDate) }
                    )
                }
            }

            if (undatedTasks.isNotEmpty()) {
                item("undated_header") {
                    SectionTitle(
                        "Sin fecha",
                        "${undatedTasks.size}",
                        hint = "Tareas pendientes sin fecha asignada — toca para programar."
                    )
                }
                items(undatedTasks, key = { "und_${it.id}" }) { task ->
                    SwipeableTaskRow(
                        entry = task,
                        onClick = { onEntryClick(task.id) },
                        onComplete = { markEntryCompleted(task) },
                        onPostpone = { dueDate -> postponeEntry(task, dueDate) }
                    )
                }
            }

            if (
                thisWeekSection.days.isEmpty() &&
                nextWeekSection.days.isEmpty() &&
                laterTasks.isEmpty() &&
                undatedTasks.isEmpty() &&
                overdueTasks.isEmpty()
            ) {
                item("empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Agenda despejada · disfrútalo.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.agendaSection(
    section: AgendaSection,
    onEntryClick: (Long) -> Unit,
    onComplete: (DiaryEntry) -> Unit,
    onPostpone: (DiaryEntry, Long) -> Unit
) {
    if (section.days.isEmpty()) return
    item("${section.title}_header") {
        SectionTitle(section.title, section.rangeLabel)
    }
    section.days.forEach { day ->
        item("${section.title}_${day.dayStartMs}_day") {
            DayHeader(day.label)
        }
        items(day.events, key = { "${section.title}_${day.dayStartMs}_ev_${it.id}" }) { ev ->
            EventRow(event = ev)
        }
        items(day.tasks, key = { "${section.title}_${day.dayStartMs}_tk_${it.id}" }) { task ->
            SwipeableTaskRow(
                entry = task,
                onClick = { onEntryClick(task.id) },
                onComplete = { onComplete(task) },
                onPostpone = { dueDate -> onPostpone(task, dueDate) }
            )
        }
    }
}

@Composable
private fun AgendaStatsCard(
    calendarCount: Int,
    taskCount: Int,
    overdueCount: Int,
    urgentCount: Int
) {
    val t = LocalTramaColors.current
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = t.surface),
        border = BorderStroke(0.5.dp, t.softBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(
                icon = Icons.Default.CalendarMonth,
                tint = t.amber,
                count = calendarCount,
                label = if (calendarCount == 1) "evento" else "eventos"
            )
            VerticalDivider()
            StatItem(
                icon = Icons.Default.CheckCircle,
                tint = t.teal,
                count = taskCount,
                label = if (taskCount == 1) "tarea" else "tareas"
            )
            if (overdueCount > 0 || urgentCount > 0) {
                VerticalDivider()
                StatItem(
                    icon = Icons.Default.WarningAmber,
                    tint = MaterialTheme.colorScheme.error,
                    count = overdueCount + urgentCount,
                    label = if (overdueCount + urgentCount == 1) "urgente" else "urgentes"
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    count: Int,
    label: String
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = tint
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .height(20.dp)
            .width(1.dp)
            .padding(horizontal = 0.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = LocalTramaColors.current.hairline
        ) {}
    }
}

@Composable
private fun SectionTitle(title: String, count: String, hint: String? = null) {
    val t = LocalTramaColors.current
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = t.mutedText
            )
            Spacer(Modifier.width(6.dp))
            Text(text = "($count)", style = MaterialTheme.typography.labelSmall, color = t.dimText)
        }
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun DayHeader(label: String) {
    val t = LocalTramaColors.current
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = t.mutedText,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}

@Composable
private fun EventRow(event: TimelineEvent) {
    val t = LocalTramaColors.current
    val timeLabel = remember(event) { formatTimeRange(event) }
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = t.surface),
        border = BorderStroke(0.5.dp, t.amber.copy(alpha = 0.20f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Event, contentDescription = null, tint = t.amber, modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SwipeableTaskRow(
    entry: DiaryEntry,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    onPostpone: (Long) -> Unit,
    overdue: Boolean = false
) {
    SwipeableReminderCard(
        entry = entry,
        enabled = entry.status != EntryStatus.COMPLETED && entry.status != EntryStatus.DISCARDED,
        onMarkDone = onComplete,
        onPostponeSelected = { dueDate, _ -> onPostpone(dueDate) }
    ) {
        TaskRow(entry = entry, onClick = onClick, overdue = overdue)
    }
}

@Composable
private fun TaskRow(entry: DiaryEntry, onClick: () -> Unit, overdue: Boolean = false) {
    val t = LocalTramaColors.current
    val urgent = entry.priority.equals("URGENT", ignoreCase = true)
    val due = entry.dueDate
    val dueLabel = remember(due) {
        due?.let {
            val cal = Calendar.getInstance().apply { timeInMillis = it }
            SimpleDateFormat("EEE d", Locale("es")).format(cal.time)
                .replaceFirstChar { ch -> ch.uppercase() }
        }
    }
    val accent = when {
        overdue -> MaterialTheme.colorScheme.error
        urgent -> MaterialTheme.colorScheme.error
        else -> t.teal
    }
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = t.surface),
        border = BorderStroke(0.5.dp, accent.copy(alpha = 0.20f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.displayText.ifBlank { entry.text },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (entry.status == EntryStatus.COMPLETED) TextDecoration.LineThrough else TextDecoration.None
                )
                if (dueLabel != null) {
                    Text(
                        text = if (overdue) "Vencía $dueLabel" else dueLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (overdue) accent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun buildSection(
    title: String,
    rangeStart: Long,
    rangeEnd: Long,
    events: List<TimelineEvent>,
    pending: List<DiaryEntry>
): AgendaSection {
    val rangeLabel = run {
        val fmt = SimpleDateFormat("d MMM", Locale("es"))
        "${fmt.format(Date(rangeStart))} → ${fmt.format(Date(rangeEnd))}"
    }
    val tasksInRange = pending.filter {
        val due = it.dueDate ?: return@filter false
        due in rangeStart..rangeEnd
    }
    val byDay = sortedMapOf<Long, MutableList<TimelineEvent>>()
    val taskByDay = sortedMapOf<Long, MutableList<DiaryEntry>>()
    events
        .filter { it.type == TimelineEventType.CALENDAR }
        .forEach { ev ->
            val key = DayRange.of(ev.timestamp).startMs
            byDay.getOrPut(key) { mutableListOf() } += ev
        }
    tasksInRange.forEach { task ->
        val key = DayRange.of(task.dueDate!!).startMs
        taskByDay.getOrPut(key) { mutableListOf() } += task
    }
    val allDayKeys = (byDay.keys + taskByDay.keys).toSortedSet()
    val days = allDayKeys.map { dayMs ->
        AgendaDayBucket(
            dayStartMs = dayMs,
            label = SimpleDateFormat("EEEE d", Locale("es")).format(Date(dayMs))
                .replaceFirstChar { it.uppercase() },
            events = byDay[dayMs].orEmpty().sortedBy { it.timestamp },
            tasks = taskByDay[dayMs].orEmpty().sortedBy { it.dueDate ?: 0L }
        )
    }
    return AgendaSection(title = title, rangeLabel = rangeLabel, days = days)
}

private fun endOfWeekMs(todayStartMs: Long): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = todayStartMs }
    while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    cal.set(Calendar.SECOND, 59)
    cal.set(Calendar.MILLISECOND, 999)
    return cal.timeInMillis
}

private fun formatTimeRange(event: TimelineEvent): String {
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    val end = event.endTimestamp
    val isAllDay = end != null &&
        (end - event.timestamp) >= 23L * 3_600_000L &&
        Calendar.getInstance().apply { timeInMillis = event.timestamp }.let {
            it.get(Calendar.HOUR_OF_DAY) == 0 && it.get(Calendar.MINUTE) == 0
        }
    if (isAllDay) return "Todo el día"
    val start = timeFmt.format(Date(event.timestamp))
    return if (end == null) start else "$start–${timeFmt.format(Date(end))}"
}
