package com.trama.app.ui.screens

import android.app.DatePickerDialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trama.app.location.PlaceMapsLauncher
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.DailyPageStatus
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryStatus
import com.trama.shared.model.Place
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    initialSelectedDayStart: Long? = null,
    onDayClick: (Long) -> Unit,
    onEntryClick: (Long) -> Unit,
    onBack: () -> Unit,
    onPlaceClick: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { DatabaseProvider.getRepository(context) }
    val scope = rememberCoroutineScope()

    val today = remember { Calendar.getInstance() }
    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val initialDayStart = remember(initialSelectedDayStart) {
        initialSelectedDayStart ?: Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    var selectedDayStart by remember { mutableStateOf(initialDayStart) }
    var displayMonth by remember {
        mutableStateOf(
            Calendar.getInstance().apply {
                timeInMillis = initialDayStart
                set(Calendar.DAY_OF_MONTH, 1)
            }
        )
    }
    var postponeEntry by remember { mutableStateOf<DiaryEntry?>(null) }

    val monthStart = remember(displayMonth) {
        (displayMonth.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val monthEnd = remember(displayMonth) {
        (displayMonth.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }
    val selectedDayEnd = remember(selectedDayStart) { selectedDayStart + 86_400_000L - 1L }

    val monthEntriesState by repository.byDateRange(monthStart, monthEnd).collectAsState(initial = null)
    val monthStoredEventsState by repository.getTimelineEventsByDateRange(monthStart, monthEnd).collectAsState(initial = null)
    val placesState by repository.getPlaces().collectAsState(initial = null)
    val selectedDayEntriesState by repository.byDateRange(selectedDayStart, selectedDayEnd).collectAsState(initial = null)
    val selectedDayEventsState by repository.getTimelineEventsByDateRange(selectedDayStart, selectedDayEnd).collectAsState(initial = null)
    val selectedDailyPageState by repository.getDailyPage(selectedDayStart).collectAsState(initial = null)

    val monthEntries = monthEntriesState ?: emptyList()
    val monthStoredEvents = monthStoredEventsState ?: emptyList()
    val places = placesState ?: emptyList()
    val selectedDayEntries = selectedDayEntriesState ?: emptyList()
    val selectedDayEvents = selectedDayEventsState ?: emptyList()
    val selectedDailyPage = selectedDailyPageState

    val isLoading = monthEntriesState == null ||
        monthStoredEventsState == null ||
        placesState == null ||
        selectedDayEntriesState == null ||
        selectedDayEventsState == null

    val entriesByDay = remember(monthEntries) {
        monthEntries.groupBy {
            Calendar.getInstance().apply { timeInMillis = it.createdAt }.get(Calendar.DAY_OF_MONTH)
        }
    }
    val eventEntriesByDay = remember(monthStoredEvents) {
        monthStoredEvents.groupBy {
            Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.DAY_OF_MONTH)
        }
    }
    val completedByDay = remember(monthEntries) {
        monthEntries
            .filter { it.status == EntryStatus.COMPLETED }
            .groupBy {
                val cal = Calendar.getInstance()
                cal.timeInMillis = it.completedAt ?: it.createdAt
                cal.get(Calendar.DAY_OF_MONTH)
            }
    }

    val selectedPlaces = remember(selectedDayEvents, places) {
        val placeIds = selectedDayEvents.mapNotNull { it.placeId }.toSet()
        places.filter { it.id in placeIds }.sortedBy { it.name.lowercase(Locale.getDefault()) }
    }
    val openTasks = remember(selectedDayEntries, selectedDayEnd) {
        selectedDayEntries
            .filter { it.status == EntryStatus.PENDING && it.duplicateOfId == null && ((it.dueDate ?: Long.MIN_VALUE) <= selectedDayEnd) }
            .sortedByDescending { it.createdAt }
    }
    val postponedTasks = remember(selectedDayEntries, selectedDayEnd) {
        selectedDayEntries
            .filter { it.status == EntryStatus.PENDING && (it.dueDate ?: Long.MIN_VALUE) > selectedDayEnd }
            .sortedBy { it.dueDate ?: Long.MAX_VALUE }
    }
    val duplicateTasks = remember(selectedDayEntries) {
        selectedDayEntries
            .filter { it.status == EntryStatus.PENDING && it.duplicateOfId != null }
            .sortedByDescending { it.createdAt }
    }
    val completedTasks = remember(selectedDayEntries) {
        selectedDayEntries
            .filter { it.status == EntryStatus.COMPLETED }
            .sortedByDescending { it.completedAt ?: it.createdAt }
    }

    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale("es")) }
    val selectedDayLabel = remember(selectedDayStart) {
        SimpleDateFormat("EEEE d 'de' MMMM", Locale("es")).format(selectedDayStart)
            .replaceFirstChar { it.uppercase() }
    }
    val briefSummary = remember(openTasks, postponedTasks, completedTasks, selectedPlaces, duplicateTasks) {
        buildHeuristicDaySummary(
            openTasks = openTasks,
            postponedTasks = postponedTasks,
            completedTasks = completedTasks,
            places = selectedPlaces,
            duplicateTasks = duplicateTasks
        )
    }
    val canPostponeSelectedDay = selectedDayStart >= todayStart

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendario") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Cargando calendario...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.44f)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = {
                            displayMonth = (displayMonth.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                        }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Mes anterior")
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = monthFormat.format(displayMonth.time).replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        IconButton(onClick = {
                            displayMonth = (displayMonth.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                        }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Mes siguiente")
                        }
                    }

                    val weekdays = listOf("L", "M", "X", "J", "V", "S", "D")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        weekdays.forEach { day ->
                            Text(
                                text = day,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val calDays = remember(displayMonth) { buildCalendarDays(displayMonth) }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(calDays) { calDay ->
                            CalendarDay(
                                day = calDay,
                                isToday = calDay.isCurrentMonth &&
                                    calDay.dayOfMonth == today.get(Calendar.DAY_OF_MONTH) &&
                                    displayMonth.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                                    displayMonth.get(Calendar.YEAR) == today.get(Calendar.YEAR),
                                isSelected = calDay.isCurrentMonth &&
                                    selectedDayStart == (displayMonth.clone() as Calendar).apply {
                                        set(Calendar.DAY_OF_MONTH, calDay.dayOfMonth.coerceAtLeast(1))
                                        set(Calendar.HOUR_OF_DAY, 0)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }.timeInMillis,
                                hasEntries = calDay.isCurrentMonth &&
                                    (entriesByDay.containsKey(calDay.dayOfMonth) || eventEntriesByDay.containsKey(calDay.dayOfMonth)),
                                hasCompleted = calDay.isCurrentMonth && completedByDay.containsKey(calDay.dayOfMonth),
                                entryCount = if (calDay.isCurrentMonth) {
                                    (entriesByDay[calDay.dayOfMonth]?.size ?: 0) +
                                        (eventEntriesByDay[calDay.dayOfMonth]?.size ?: 0)
                                } else 0,
                                onClick = {
                                    if (!calDay.isCurrentMonth) return@CalendarDay
                                    selectedDayStart = (displayMonth.clone() as Calendar).apply {
                                        set(Calendar.DAY_OF_MONTH, calDay.dayOfMonth)
                                        set(Calendar.HOUR_OF_DAY, 0)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }.timeInMillis
                                }
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.56f)
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedDayLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = buildString {
                                    append("${openTasks.size} abiertas")
                                    if (completedTasks.isNotEmpty()) append(" · ${completedTasks.size} hechas")
                                    if (selectedPlaces.isNotEmpty()) append(" · ${selectedPlaces.size} lugares")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { onDayClick(selectedDayStart) }) {
                            Text("Abrir timeline")
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item("summary") {
                            DaySummaryCard(
                                summary = briefSummary,
                                isFinal = selectedDailyPage?.status == DailyPageStatus.FINAL
                            )
                        }

                        item("tasks_header") {
                            CalendarHistoryHeader(
                                title = "Tareas del día",
                                subtitle = buildString {
                                    append("${openTasks.size} activas")
                                    if (postponedTasks.isNotEmpty()) append(" · ${postponedTasks.size} pospuestas")
                                    if (duplicateTasks.isNotEmpty()) append(" · ${duplicateTasks.size} duplicadas")
                                }
                            )
                        }

                        if (openTasks.isEmpty() && postponedTasks.isEmpty() && duplicateTasks.isEmpty()) {
                            item("tasks_empty") {
                                CalendarEmptyCard(
                                    title = "Nada pendiente aquí",
                                    body = "Ese día no dejó tareas abiertas o ya quedaron ordenadas."
                                )
                            }
                        } else {
                            if (openTasks.isNotEmpty()) {
                                items(openTasks, key = { "open_${it.id}" }) { entry ->
                                    CalendarTaskCard(
                                        entry = entry,
                                        label = "Activa",
                                        canPostpone = canPostponeSelectedDay,
                                        onEdit = { onEntryClick(entry.id) },
                                        onDone = {
                                            scope.launch {
                                                repository.markCompleted(entry.id)
                                                repository.markDailyPageReviewed(selectedDayStart)
                                            }
                                        },
                                        onPostpone = { postponeEntry = entry }
                                    )
                                }
                            }
                            if (postponedTasks.isNotEmpty()) {
                                items(postponedTasks, key = { "postponed_${it.id}" }) { entry ->
                                    CalendarTaskCard(
                                        entry = entry,
                                        label = entry.dueDate?.let { "Pospuesta a ${SimpleDateFormat("d MMM", Locale("es")).format(Date(it))}" }
                                            ?: "Pospuesta",
                                        canPostpone = canPostponeSelectedDay,
                                        onEdit = { onEntryClick(entry.id) },
                                        onDone = {
                                            scope.launch {
                                                repository.markCompleted(entry.id)
                                                repository.markDailyPageReviewed(selectedDayStart)
                                            }
                                        },
                                        onPostpone = { postponeEntry = entry }
                                    )
                                }
                            }
                            if (duplicateTasks.isNotEmpty()) {
                                items(duplicateTasks, key = { "dup_${it.id}" }) { entry ->
                                    CalendarTaskCard(
                                        entry = entry,
                                        label = "Posible duplicado",
                                        canPostpone = canPostponeSelectedDay,
                                        onEdit = { onEntryClick(entry.id) },
                                        onDone = {
                                            scope.launch {
                                                repository.clearDuplicate(entry.id)
                                                repository.markCompleted(entry.id)
                                                repository.markDailyPageReviewed(selectedDayStart)
                                            }
                                        },
                                        onPostpone = { postponeEntry = entry }
                                    )
                                }
                            }
                        }

                        item("places_header") {
                            CalendarHistoryHeader(
                                title = "Lugares visitados",
                                subtitle = if (selectedPlaces.isEmpty()) {
                                    "No hay ubicaciones registradas este día"
                                } else {
                                    "Valóralos rápido o abre su ficha para dejar una opinión"
                                }
                            )
                        }

                        if (selectedPlaces.isEmpty()) {
                            item("places_empty") {
                                CalendarEmptyCard(
                                    title = "Sin lugares detectados",
                                    body = "Cuando Trama detecta estancias, aparecerán aquí para valorarlas fácilmente."
                                )
                            }
                        } else {
                            items(selectedPlaces, key = { "place_${it.id}" }) { place ->
                                CalendarPlaceCard(
                                    place = place,
                                    onRate = { rating ->
                                        scope.launch {
                                            repository.updatePlaceOpinion(
                                                id = place.id,
                                                rating = rating,
                                                opinionText = place.opinionText,
                                                opinionSummary = place.opinionSummary,
                                                opinionUpdatedAt = System.currentTimeMillis()
                                            )
                                            repository.markDailyPageReviewed(selectedDayStart)
                                        }
                                    },
                                    onOpenDetail = { onPlaceClick(place.id) },
                                    onOpenMap = {
                                        PlaceMapsLauncher.openInGoogleMaps(
                                            context = context,
                                            latitude = place.latitude,
                                            longitude = place.longitude,
                                            label = place.name
                                        )
                                    }
                                )
                            }
                        }

                        if (completedTasks.isNotEmpty()) {
                            item("completed_header") {
                                CalendarHistoryHeader(
                                    title = "Completadas",
                                    subtitle = "${completedTasks.size} tareas cerradas ese día"
                                )
                            }
                            items(completedTasks, key = { "done_${it.id}" }) { entry ->
                                CalendarCompletedTaskCard(
                                    entry = entry,
                                    onOpen = { onEntryClick(entry.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    postponeEntry?.let { entry ->
        CalendarPostponeDialog(
            entry = entry,
            onDismiss = { postponeEntry = null },
            onPostpone = { dueDate ->
                scope.launch {
                    repository.updateDueDate(entry.id, dueDate)
                    repository.markDailyPageReviewed(selectedDayStart)
                    postponeEntry = null
                }
            }
        )
    }
}

@Composable
private fun DaySummaryCard(summary: String, isFinal: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Resumen breve",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isFinal) {
                Text(
                    text = "La memoria técnica de este día ya quedó consolidada para el chat.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun CalendarHistoryHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CalendarEmptyCard(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CalendarTaskCard(
    entry: DiaryEntry,
    label: String,
    canPostpone: Boolean,
    onEdit: () -> Unit,
    onDone: () -> Unit,
    onPostpone: () -> Unit
) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = entry.displayText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = buildTaskMeta(entry, label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Editar")
                }
                FilledTonalButton(onClick = onDone) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Hecha")
                }
            }
            if (canPostpone) {
                TextButton(onClick = onPostpone) {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Posponer")
                }
            } else {
                Text(
                    text = "Las tareas de días pasados se consultan aquí, pero no se posponen desde el histórico.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CalendarCompletedTaskCard(
    entry: DiaryEntry,
    onOpen: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.displayText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildCompletedMeta(entry),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onOpen) {
                Text("Abrir")
            }
        }
    }
}

@Composable
private fun CalendarPlaceCard(
    place: Place,
    onRate: (Int?) -> Unit,
    onOpenDetail: () -> Unit,
    onOpenMap: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var rating by remember(place.id, place.rating) { mutableStateOf(place.rating ?: 0) }

    Card(shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = place.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = buildPlaceMeta(place),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                place.rating?.let { savedRating ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "$savedRating/5",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "¿Qué tal fue?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { star ->
                        val selected = star <= rating
                        Surface(
                            onClick = {
                                scope.launch {
                                    rating = star
                                    onRate(star)
                                }
                            },
                            shape = CircleShape,
                            color = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            },
                            modifier = Modifier
                                .size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "$star estrellas",
                                    tint = if (selected) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    },
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
                Text(
                    text = if (rating > 0) "Tu nota: $rating de 5" else "Toca una estrella para puntuar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            place.opinionSummary?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } ?: place.opinionText?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenDetail) {
                    Text(if (place.opinionText.isNullOrBlank()) "Comentar" else "Abrir ficha")
                }
                TextButton(onClick = onOpenMap) {
                    Text("Abrir en mapas")
                }
            }
        }
    }
}

@Composable
private fun CalendarPostponeDialog(
    entry: DiaryEntry,
    onDismiss: () -> Unit,
    onPostpone: (Long) -> Unit
) {
    val context = LocalContext.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Posponer tarea") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = entry.displayText,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { onPostpone(resolveTomorrow(entry.dueDate)) },
                        label = { Text("Mañana") }
                    )
                    AssistChip(
                        onClick = { onPostpone(resolveNextWeek(entry.dueDate)) },
                        label = { Text("Semana que viene") }
                    )
                }
                TextButton(
                    onClick = {
                        val today = Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                onPostpone(resolveCustomDate(year, month, dayOfMonth, entry.dueDate))
                            },
                            today.get(Calendar.YEAR),
                            today.get(Calendar.MONTH),
                            today.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
                ) {
                    Text("Elegir fecha")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

private fun buildTaskMeta(entry: DiaryEntry, label: String): String {
    val dateFormat = SimpleDateFormat("d MMM · HH:mm", Locale("es"))
    val created = dateFormat.format(Date(entry.createdAt))
    val due = entry.dueDate?.let { " · vence ${dateFormat.format(Date(it))}" }.orEmpty()
    return "$label · creada $created$due"
}

private fun buildCompletedMeta(entry: DiaryEntry): String {
    val completedAt = entry.completedAt ?: entry.createdAt
    val dateFormat = SimpleDateFormat("d MMM · HH:mm", Locale("es"))
    return "Cerrada ${dateFormat.format(Date(completedAt))}"
}

private fun buildPlaceMeta(place: Place): String =
    buildString {
        if (!place.type.isNullOrBlank()) append(place.type)
        if (place.visitCount > 0) {
            if (isNotEmpty()) append(" · ")
            append("${place.visitCount} visitas")
        }
        if (place.rating != null) {
            if (isNotEmpty()) append(" · ")
            append("${place.rating}/5")
        }
    }.ifBlank { "Lugar detectado" }

private fun buildHeuristicDaySummary(
    openTasks: List<DiaryEntry>,
    postponedTasks: List<DiaryEntry>,
    completedTasks: List<DiaryEntry>,
    places: List<Place>,
    duplicateTasks: List<DiaryEntry>
): String {
    if (openTasks.isEmpty() && postponedTasks.isEmpty() && completedTasks.isEmpty() && places.isEmpty()) {
        return "No hay mucha actividad registrada en este día todavía."
    }
    return buildString {
        append("Este día dejó ${openTasks.size} tareas activas")
        if (completedTasks.isNotEmpty()) append(", ${completedTasks.size} tareas completadas")
        if (postponedTasks.isNotEmpty()) append(", ${postponedTasks.size} tareas pospuestas")
        if (places.isNotEmpty()) append(" y ${places.size} lugares visitados")
        append(".")
        if (duplicateTasks.isNotEmpty()) {
            append(" Además hay ${duplicateTasks.size} recordatorios que parecen duplicados.")
        }
    }
}

private data class CalDay(
    val dayOfMonth: Int,
    val isCurrentMonth: Boolean
)

@Composable
private fun CalendarDay(
    day: CalDay,
    isToday: Boolean,
    isSelected: Boolean,
    hasEntries: Boolean,
    hasCompleted: Boolean,
    entryCount: Int,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.secondaryContainer
            isToday -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surface
        },
        label = "dayBg"
    )
    val textColor = when {
        !day.isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
        isSelected -> MaterialTheme.colorScheme.secondary
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable(enabled = day.isCurrentMonth, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (day.isCurrentMonth) "${day.dayOfMonth}" else "",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
            if (day.isCurrentMonth && hasEntries) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(top = 1.dp)
                ) {
                    if (entryCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                        )
                    }
                    if (hasCompleted) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f))
                        )
                    }
                }
            }
        }
    }
}

private fun buildCalendarDays(displayMonth: Calendar): List<CalDay> {
    val cal = displayMonth.clone() as Calendar
    cal.set(Calendar.DAY_OF_MONTH, 1)

    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val offset = when (firstDayOfWeek) {
        Calendar.MONDAY -> 0
        Calendar.TUESDAY -> 1
        Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY -> 3
        Calendar.FRIDAY -> 4
        Calendar.SATURDAY -> 5
        Calendar.SUNDAY -> 6
        else -> 0
    }

    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val totalCells = ((offset + daysInMonth + 6) / 7) * 7

    return (0 until totalCells).map { index ->
        val dayNum = index - offset + 1
        CalDay(
            dayOfMonth = if (dayNum in 1..daysInMonth) dayNum else 0,
            isCurrentMonth = dayNum in 1..daysInMonth
        )
    }
}

private fun preferredHour(previousDueDate: Long?): Int =
    previousDueDate?.let {
        Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.HOUR_OF_DAY)
    } ?: 9

private fun preferredMinute(previousDueDate: Long?): Int =
    previousDueDate?.let {
        Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.MINUTE)
    } ?: 0

private fun resolveTomorrow(previousDueDate: Long?): Long =
    Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, preferredHour(previousDueDate))
        set(Calendar.MINUTE, preferredMinute(previousDueDate))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun resolveNextWeek(previousDueDate: Long?): Long =
    Calendar.getInstance().apply {
        add(Calendar.WEEK_OF_YEAR, 1)
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, preferredHour(previousDueDate))
        set(Calendar.MINUTE, preferredMinute(previousDueDate))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun resolveCustomDate(
    year: Int,
    month: Int,
    dayOfMonth: Int,
    previousDueDate: Long?
): Long =
    Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, dayOfMonth)
        set(Calendar.HOUR_OF_DAY, preferredHour(previousDueDate))
        set(Calendar.MINUTE, preferredMinute(previousDueDate))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
