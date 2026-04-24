package com.trama.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trama.app.location.PlaceMapsLauncher
import com.trama.app.ui.theme.LocalTramaColors
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryStatus
import com.trama.shared.model.Place
import com.trama.shared.model.Recording
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    initialSelectedDayStart: Long? = null,
    onEntryClick: (Long) -> Unit,
    onRecordingClick: (Long) -> Unit = {},
    onBack: () -> Unit,
    onPlaceClick: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { DatabaseProvider.getRepository(context) }
    val scope = rememberCoroutineScope()

    val today = remember { Calendar.getInstance() }

    // Today start = max selectable day
    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val initialDayStart = remember(initialSelectedDayStart, todayStart) {
        (initialSelectedDayStart ?: todayStart).coerceAtMost(todayStart)
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
    var calendarExpanded by remember { mutableStateOf(false) }

    val monthStart = remember(displayMonth) {
        (displayMonth.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val monthEnd = remember(displayMonth) {
        (displayMonth.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }
    val selectedDayEnd = remember(selectedDayStart) {
        com.trama.shared.util.DayRange.of(selectedDayStart).endInclusiveMs
    }

    // Data
    val monthEntriesState by repository.byDateRange(monthStart, monthEnd).collectAsState(initial = null)
    val monthStoredEventsState by repository.getTimelineEventsByDateRange(monthStart, monthEnd).collectAsState(initial = null)
    val placesState by repository.getPlaces().collectAsState(initial = null)
    val selectedDayEventsState by repository.getTimelineEventsByDateRange(selectedDayStart, selectedDayEnd).collectAsState(initial = null)
    val pendingOnDayState by repository.getPendingAsOf(selectedDayEnd).collectAsState(initial = null)
    val completedOnDayState by repository.getCompletedByCompletedAt(selectedDayStart, selectedDayEnd).collectAsState(initial = null)
    val recordingsState by repository.getAllRecordings().collectAsState(initial = null)

    val monthEntries = monthEntriesState ?: emptyList()
    val monthStoredEvents = monthStoredEventsState ?: emptyList()
    val places = placesState ?: emptyList()
    val selectedDayEvents = selectedDayEventsState ?: emptyList()
    val pendingOnDay = pendingOnDayState ?: emptyList()
    val completedTasks = completedOnDayState ?: emptyList()
    val dayRecordings = recordingsState
        ?.filter { it.createdAt in selectedDayStart..selectedDayEnd }
        ?.sortedBy { it.createdAt }
        ?: emptyList()

    val isLoading = monthEntriesState == null ||
        monthStoredEventsState == null ||
        placesState == null ||
        selectedDayEventsState == null ||
        pendingOnDayState == null ||
        completedOnDayState == null ||
        recordingsState == null

    // Month grid dot data
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

    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale("es")) }
    val t = LocalTramaColors.current
    val selectedDayLabel = remember(selectedDayStart) {
        SimpleDateFormat("EEEE d 'de' MMMM", Locale("es")).format(selectedDayStart)
            .replaceFirstChar { it.uppercase() }
    }

    // Can only go forward up to today
    val atMaxDay = selectedDayStart >= todayStart
    val isSelectedToday = selectedDayStart == todayStart

    fun navigateDay(offset: Int) {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDayStart }
        cal.add(Calendar.DAY_OF_YEAR, offset)
        val newStart = cal.apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        if (newStart <= todayStart) {
            selectedDayStart = newStart
            if (cal.get(Calendar.MONTH) != displayMonth.get(Calendar.MONTH) ||
                cal.get(Calendar.YEAR) != displayMonth.get(Calendar.YEAR)
            ) {
                displayMonth = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            com.trama.app.ui.components.TramaBottomBar(
                active = com.trama.app.ui.components.TramaTab.Calendar,
                onTabSelected = { tab ->
                    if (tab == com.trama.app.ui.components.TramaTab.Home) onBack()
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Day navigation header ──────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navigateDay(-1) }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Día anterior")
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { calendarExpanded = !calendarExpanded },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = selectedDayLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = monthFormat.format(Date(selectedDayStart))
                                .replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            if (calendarExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = { navigateDay(1) },
                    enabled = !atMaxDay
                ) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Día siguiente",
                        tint = if (atMaxDay)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // ── Always-visible week strip ──────────────────────────────────
            WeekStrip(
                selectedDayStart = selectedDayStart,
                todayStart = todayStart,
                entriesByDay = entriesByDay,
                eventEntriesByDay = eventEntriesByDay,
                completedByDay = completedByDay,
                displayMonth = displayMonth,
                onDaySelected = { ms ->
                    selectedDayStart = ms
                    val newCal = Calendar.getInstance().apply { timeInMillis = ms }
                    if (newCal.get(Calendar.MONTH) != displayMonth.get(Calendar.MONTH) ||
                        newCal.get(Calendar.YEAR) != displayMonth.get(Calendar.YEAR)) {
                        displayMonth = (newCal.clone() as Calendar).apply {
                            set(Calendar.DAY_OF_MONTH, 1)
                        }
                    }
                }
            )

            // ── Collapsible month grid ─────────────────────────────────────
            AnimatedVisibility(
                visible = calendarExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                    // Weekday headers
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("L", "M", "X", "J", "V", "S", "D").forEach { d ->
                            Text(
                                text = d,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val calDays = remember(displayMonth) { buildCalendarDays(displayMonth) }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        userScrollEnabled = false,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(calDays) { day ->
                            val todayCal = today
                            val isToday = day.isCurrentMonth &&
                                day.dayOfMonth == todayCal.get(Calendar.DAY_OF_MONTH) &&
                                displayMonth.get(Calendar.MONTH) == todayCal.get(Calendar.MONTH) &&
                                displayMonth.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)
                            val isSelected = day.isCurrentMonth && run {
                                val sel = Calendar.getInstance().apply { timeInMillis = selectedDayStart }
                                day.dayOfMonth == sel.get(Calendar.DAY_OF_MONTH) &&
                                    displayMonth.get(Calendar.MONTH) == sel.get(Calendar.MONTH) &&
                                    displayMonth.get(Calendar.YEAR) == sel.get(Calendar.YEAR)
                            }
                            val hasEntries = (entriesByDay[day.dayOfMonth]?.isNotEmpty() == true) ||
                                (eventEntriesByDay[day.dayOfMonth]?.isNotEmpty() == true)
                            val hasCompleted = completedByDay[day.dayOfMonth]?.isNotEmpty() == true
                            val entryCount = entriesByDay[day.dayOfMonth]?.size ?: 0
                            CalendarDay(
                                day = day,
                                isToday = isToday,
                                isSelected = isSelected,
                                hasEntries = hasEntries,
                                hasCompleted = hasCompleted,
                                entryCount = entryCount,
                                onClick = {
                                    val clickedCal = (displayMonth.clone() as Calendar).apply {
                                        set(Calendar.DAY_OF_MONTH, day.dayOfMonth)
                                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                                    }
                                    val clickedMs = clickedCal.timeInMillis
                                    if (clickedMs <= todayStart) {
                                        selectedDayStart = clickedMs
                                    }
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }
            }

            // ── Stats bar ─────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildString {
                        if (pendingOnDay.isNotEmpty()) append("${pendingOnDay.size} pendiente${if (pendingOnDay.size != 1) "s" else ""}")
                        else append("Sin pendientes")
                        if (completedTasks.isNotEmpty()) append(" · ${completedTasks.size} cerradas")
                        if (dayRecordings.isNotEmpty()) append(" · ${dayRecordings.size} grab.")
                        if (selectedPlaces.isNotEmpty()) append(" · ${selectedPlaces.size} lugares")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            // ── Today banner ──────────────────────────────────────────────
            if (isSelectedToday) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = t.amberBg
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        t.amber.copy(alpha = 0.22f)
                    )
                ) {
                    Text(
                        text = "Hoy se va completando a lo largo del día. Todo lo que capture desde las 00:00 ya aparece aquí.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            // ── Content ───────────────────────────────────────────────────
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ── Pendientes ese día ──────────────────────────────────
                    if (pendingOnDay.isNotEmpty()) {
                        item("pending_header") {
                            CalendarHistoryHeader(
                                title = "Pendientes ese día",
                                subtitle = "(${pendingOnDay.size})"
                            )
                        }
                        items(pendingOnDay, key = { "pending_${it.id}" }) { entry ->
                            CalendarHistoryCard(
                                entry = entry,
                                isCompleted = false,
                                onClick = { onEntryClick(entry.id) }
                            )
                        }
                    } else {
                        item("pending_empty") {
                            CalendarEmptyHint("Sin tareas pendientes ese día.")
                        }
                    }

                    // ── Cerradas ese día ────────────────────────────────────
                    if (completedTasks.isNotEmpty()) {
                        item("completed_header") {
                            CalendarHistoryHeader(
                                title = "Cerradas ese día",
                                subtitle = "(${completedTasks.size})"
                            )
                        }
                        items(completedTasks, key = { "done_${it.id}" }) { entry ->
                            CalendarHistoryCard(
                                entry = entry,
                                isCompleted = true,
                                onClick = { onEntryClick(entry.id) }
                            )
                        }
                    }

                    // ── Grabaciones ─────────────────────────────────────────
                    if (dayRecordings.isNotEmpty()) {
                        item("recordings_header") {
                            CalendarHistoryHeader(
                                title = "Grabaciones",
                                subtitle = "(${dayRecordings.size})"
                            )
                        }
                        items(dayRecordings, key = { "rec_${it.id}" }) { recording ->
                            CalendarRecordingCard(
                                recording = recording,
                                onClick = { onRecordingClick(recording.id) }
                            )
                        }
                    }

                    // ── Lugares ─────────────────────────────────────────────
                    if (selectedPlaces.isNotEmpty()) {
                        item("places_header") {
                            CalendarHistoryHeader(title = "Lugares")
                        }
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
                }
            }
        }
    }
}

// ── History card (pending or completed) ─────────────────────────────────────

@Composable
private fun CalendarHistoryCard(
    entry: DiaryEntry,
    isCompleted: Boolean,
    onClick: () -> Unit
) {
    val t = LocalTramaColors.current
    val timeFormat = remember { SimpleDateFormat("d MMM · HH:mm", Locale("es")) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted)
                t.tealBg
            else
                t.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            if (isCompleted) t.teal.copy(alpha = 0.24f) else t.softBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = entry.displayText.ifBlank { entry.text },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                color = if (isCompleted)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                else
                    MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isCompleted) {
                    val closedAt = entry.completedAt ?: entry.createdAt
                    "Cerrada ${timeFormat.format(Date(closedAt))}"
                } else {
                    "Creada ${timeFormat.format(Date(entry.createdAt))}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Recording card ───────────────────────────────────────────────────────────

@Composable
private fun CalendarRecordingCard(
    recording: Recording,
    onClick: () -> Unit
) {
    val t = LocalTramaColors.current
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = t.surface),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            t.red.copy(alpha = 0.20f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint = t.red,
                modifier = Modifier.size(16.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recording.title?.ifBlank { null } ?: "Grabación",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = timeFormat.format(Date(recording.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Place card ───────────────────────────────────────────────────────────────

@Composable
private fun CalendarPlaceCard(
    place: Place,
    onRate: (Int?) -> Unit,
    onOpenDetail: () -> Unit,
    onOpenMap: () -> Unit
) {
    val t = LocalTramaColors.current
    val scope = rememberCoroutineScope()
    var rating by remember(place.id, place.rating) { mutableStateOf(place.rating ?: 0) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = t.surface),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            t.teal.copy(alpha = 0.18f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    tint = t.teal,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onOpenDetail) {
                    Text("Ficha", style = MaterialTheme.typography.labelMedium)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { star ->
                        val selected = star <= rating
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) t.warnBg
                                    else t.surface2
                                )
                                .clickable {
                                    scope.launch {
                                        rating = star
                                        onRate(star)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "$star",
                                tint = if (selected) t.warn else t.dimText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                TextButton(onClick = onOpenMap) {
                    Text("Mapa", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// ── Section header ───────────────────────────────────────────────────────────

@Composable
private fun CalendarHistoryHeader(title: String, subtitle: String = "") {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CalendarEmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
    )
}

// ── Calendar day cell ────────────────────────────────────────────────────────

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
    val t = LocalTramaColors.current
    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> t.amberBg
            isToday    -> t.surface2
            else       -> t.surface
        },
        label = "dayBg"
    )
    val textColor = when {
        !day.isCurrentMonth -> t.dimText
        isSelected -> t.amber
        isToday -> MaterialTheme.colorScheme.onSurface
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
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
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
                                .background(t.amber.copy(alpha = 0.7f))
                        )
                    }
                    if (hasCompleted) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(t.teal.copy(alpha = 0.7f))
                        )
                    }
                }
            }
        }
    }
}

// ── Grid builder ─────────────────────────────────────────────────────────────

private fun buildCalendarDays(displayMonth: Calendar): List<CalDay> {
    val cal = displayMonth.clone() as Calendar
    cal.set(Calendar.DAY_OF_MONTH, 1)

    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val offset = when (firstDayOfWeek) {
        Calendar.MONDAY -> 0; Calendar.TUESDAY -> 1; Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY -> 3; Calendar.FRIDAY -> 4; Calendar.SATURDAY -> 5
        Calendar.SUNDAY -> 6; else -> 0
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

@Composable
private fun WeekStrip(
    selectedDayStart: Long,
    todayStart: Long,
    entriesByDay: Map<Int, List<com.trama.shared.model.DiaryEntry>>,
    eventEntriesByDay: Map<Int, List<com.trama.shared.model.TimelineEvent>>,
    completedByDay: Map<Int, List<com.trama.shared.model.DiaryEntry>>,
    displayMonth: java.util.Calendar,
    onDaySelected: (Long) -> Unit,
) {
    val t = com.trama.app.ui.theme.LocalTramaColors.current
    val selCal = java.util.Calendar.getInstance().apply {
        timeInMillis = selectedDayStart
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    // Move back to Monday (locale-agnostic week start)
    val dow = selCal.get(java.util.Calendar.DAY_OF_WEEK)
    val daysFromMonday = when (dow) {
        java.util.Calendar.MONDAY -> 0
        java.util.Calendar.TUESDAY -> 1
        java.util.Calendar.WEDNESDAY -> 2
        java.util.Calendar.THURSDAY -> 3
        java.util.Calendar.FRIDAY -> 4
        java.util.Calendar.SATURDAY -> 5
        java.util.Calendar.SUNDAY -> 6
        else -> 0
    }
    val weekStart = (selCal.clone() as java.util.Calendar).apply {
        add(java.util.Calendar.DAY_OF_YEAR, -daysFromMonday)
    }
    val labels = listOf("L", "M", "X", "J", "V", "S", "D")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (i in 0..6) {
            val dayCal = (weekStart.clone() as java.util.Calendar).apply {
                add(java.util.Calendar.DAY_OF_YEAR, i)
            }
            val dayMs = dayCal.timeInMillis
            val dayOfMonth = dayCal.get(java.util.Calendar.DAY_OF_MONTH)
            val inDisplayMonth = dayCal.get(java.util.Calendar.MONTH) == displayMonth.get(java.util.Calendar.MONTH) &&
                dayCal.get(java.util.Calendar.YEAR) == displayMonth.get(java.util.Calendar.YEAR)
            val isSelected = dayMs == selCal.timeInMillis
            val isToday = dayMs == todayStart
            val future = dayMs > todayStart
            val hasEntries = inDisplayMonth && (
                (entriesByDay[dayOfMonth]?.isNotEmpty() == true) ||
                    (eventEntriesByDay[dayOfMonth]?.isNotEmpty() == true)
            )
            val hasCompleted = inDisplayMonth && (completedByDay[dayOfMonth]?.isNotEmpty() == true)

            val bg = when {
                isSelected -> t.amber
                isToday -> t.surface2
                else -> androidx.compose.ui.graphics.Color.Transparent
            }
            val fg = when {
                isSelected -> androidx.compose.ui.graphics.Color.White
                future -> t.dimText
                else -> MaterialTheme.colorScheme.onSurface
            }
            val labelFg = when {
                isSelected -> androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f)
                else -> t.mutedText
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .clickable(enabled = !future) { onDaySelected(dayMs) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = labels[i],
                    style = MaterialTheme.typography.labelSmall,
                    color = labelFg,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = fg,
                    fontWeight = if (isSelected || isToday) androidx.compose.ui.text.font.FontWeight.Bold
                        else androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                hasEntries -> if (isSelected) androidx.compose.ui.graphics.Color.White else t.amber
                                hasCompleted -> if (isSelected) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f) else t.teal
                                else -> androidx.compose.ui.graphics.Color.Transparent
                            }
                        )
                )
            }
        }
    }
}
