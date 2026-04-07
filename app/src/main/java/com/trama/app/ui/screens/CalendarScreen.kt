package com.trama.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.sp
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryActionType
import com.trama.shared.model.EntryStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onEntryClick: (Long) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { DatabaseProvider.getRepository(context) }

    val today = remember { Calendar.getInstance() }
    var displayMonth by remember {
        mutableStateOf(Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
        })
    }
    var selectedDate by remember { mutableStateOf(today.clone() as Calendar) }

    // Get entries for selected date
    val dayStart = remember(selectedDate) {
        (selectedDate.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val dayEnd = remember(selectedDate) { dayStart + 86_400_000L }
    val dayEntries by repository.byDateRange(dayStart, dayEnd).collectAsState(initial = emptyList())

    // Get all entries for the displayed month (for dot indicators)
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
            set(Calendar.SECOND, 59)
        }.timeInMillis
    }
    val monthEntries by repository.byDateRange(monthStart, monthEnd).collectAsState(initial = emptyList())

    // Group month entries by day number for dot indicators
    val entriesByDay = remember(monthEntries) {
        monthEntries.groupBy {
            Calendar.getInstance().apply { timeInMillis = it.createdAt }.get(Calendar.DAY_OF_MONTH)
        }
    }
    // Also track completed entries by day
    val completedByDay = remember(monthEntries) {
        monthEntries.filter { it.status == EntryStatus.COMPLETED }
            .groupBy {
                val cal = Calendar.getInstance()
                cal.timeInMillis = it.completedAt ?: it.createdAt
                cal.get(Calendar.DAY_OF_MONTH)
            }
    }

    val monthFormat = SimpleDateFormat("MMMM yyyy", Locale("es"))
    val dayFormat = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es"))
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Month navigation ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = {
                    displayMonth = (displayMonth.clone() as Calendar).apply {
                        add(Calendar.MONTH, -1)
                    }
                }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Mes anterior")
                }

                Text(
                    text = monthFormat.format(displayMonth.time).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                IconButton(onClick = {
                    displayMonth = (displayMonth.clone() as Calendar).apply {
                        add(Calendar.MONTH, 1)
                    }
                }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Mes siguiente")
                }
            }

            // ── Weekday headers ──
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

            // ── Calendar grid ──
            val calDays = remember(displayMonth) { buildCalendarDays(displayMonth) }

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(0.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(calDays) { calDay ->
                    CalendarDay(
                        day = calDay,
                        isSelected = calDay.isCurrentMonth &&
                                calDay.dayOfMonth == selectedDate.get(Calendar.DAY_OF_MONTH) &&
                                displayMonth.get(Calendar.MONTH) == selectedDate.get(Calendar.MONTH) &&
                                displayMonth.get(Calendar.YEAR) == selectedDate.get(Calendar.YEAR),
                        isToday = calDay.isCurrentMonth &&
                                calDay.dayOfMonth == today.get(Calendar.DAY_OF_MONTH) &&
                                displayMonth.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                                displayMonth.get(Calendar.YEAR) == today.get(Calendar.YEAR),
                        hasEntries = calDay.isCurrentMonth && entriesByDay.containsKey(calDay.dayOfMonth),
                        hasCompleted = calDay.isCurrentMonth && completedByDay.containsKey(calDay.dayOfMonth),
                        entryCount = if (calDay.isCurrentMonth) entriesByDay[calDay.dayOfMonth]?.size ?: 0 else 0,
                        onClick = {
                            if (calDay.isCurrentMonth) {
                                selectedDate = (displayMonth.clone() as Calendar).apply {
                                    set(Calendar.DAY_OF_MONTH, calDay.dayOfMonth)
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // ── Selected day header ──
            Text(
                text = dayFormat.format(selectedDate.time).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )

            // ── Entries for selected day ──
            if (dayEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sin entradas este dia",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(dayEntries, key = { it.id }) { entry ->
                        CalendarEntryRow(
                            entry = entry,
                            timeFormat = timeFormat,
                            onClick = { onEntryClick(entry.id) }
                        )
                    }
                }
            }
        }
    }
}

// ── Calendar Day Cell ────────────────────────────────────────────────────────

private data class CalDay(
    val dayOfMonth: Int,
    val isCurrentMonth: Boolean
)

@Composable
private fun CalendarDay(
    day: CalDay,
    isSelected: Boolean,
    isToday: Boolean,
    hasEntries: Boolean,
    hasCompleted: Boolean,
    entryCount: Int,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary
            isToday -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surface
        },
        label = "dayBg"
    )
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        !day.isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
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
            // Dot indicators
            if (day.isCurrentMonth && hasEntries) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(top = 1.dp)
                ) {
                    // Pending dot
                    if (entryCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected)
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                    else
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                )
                        )
                    }
                    // Completed dot (green)
                    if (hasCompleted) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected)
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                                    else
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                                )
                        )
                    }
                }
            }
        }
    }
}

// ── Entry row in calendar detail ─────────────────────────────────────────────

@Composable
private fun CalendarEntryRow(
    entry: DiaryEntry,
    timeFormat: SimpleDateFormat,
    onClick: () -> Unit
) {
    val isCompleted = entry.status == EntryStatus.COMPLETED

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCompleted) 0.dp else 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time
            Text(
                text = timeFormat.format(Date(entry.createdAt)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.width(44.dp)
            )

            // Status indicator
            val statusColor = when {
                isCompleted -> MaterialTheme.colorScheme.tertiary
                entry.status == EntryStatus.DISCARDED -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.7f))
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCompleted)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
                // Show completed time if different from created time
                val completed = entry.completedAt
                if (isCompleted && completed != null) {
                    val completedTime = timeFormat.format(Date(completed))
                    Text(
                        text = "Completada a las $completedTime",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                    )
                }
            }

            // Action type chip
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Text(
                    text = EntryActionType.emoji(entry.actionType),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// ── Build calendar grid ──────────────────────────────────────────────────────

private fun buildCalendarDays(displayMonth: Calendar): List<CalDay> {
    val cal = displayMonth.clone() as Calendar
    cal.set(Calendar.DAY_OF_MONTH, 1)

    // Monday = 1 in our grid. Calendar.DAY_OF_WEEK: Sunday=1, Monday=2,...
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    // Convert to 0-based Monday start: Mon=0, Tue=1, ..., Sun=6
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
    val totalCells = ((offset + daysInMonth + 6) / 7) * 7 // round up to full weeks

    return (0 until totalCells).map { index ->
        val dayNum = index - offset + 1
        CalDay(
            dayOfMonth = if (dayNum in 1..daysInMonth) dayNum else 0,
            isCurrentMonth = dayNum in 1..daysInMonth
        )
    }
}
