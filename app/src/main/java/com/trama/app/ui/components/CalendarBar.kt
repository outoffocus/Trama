package com.trama.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trama.shared.model.DiaryEntry
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarBar(
    entries: List<DiaryEntry>,
    selectedDate: LocalDate?,
    expanded: Boolean,
    currentMonth: YearMonth,
    onToggleExpanded: () -> Unit,
    onDateSelected: (LocalDate?) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = Locale("es")

    val entriesByDate = entries.groupBy { entry ->
        java.time.Instant.ofEpochMilli(entry.createdAt)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            IconButton(onClick = onToggleExpanded) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = "Calendario",
                    tint = if (expanded || selectedDate != null)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                IconButton(onClick = { onMonthChange(currentMonth.minusMonths(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mes anterior")
                }
                Text(
                    text = "${currentMonth.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.uppercase() }} ${currentMonth.year}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = { onMonthChange(currentMonth.plusMonths(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mes siguiente")
                }
            } else {
                if (selectedDate != null) {
                    Text(
                        text = formatSelectedDate(selectedDate, locale),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onDateSelected(null) }) {
                        Text("Ver todo", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            CalendarGrid(
                yearMonth = currentMonth,
                entriesByDate = entriesByDate,
                selectedDate = selectedDate,
                onDateSelected = onDateSelected
            )
        }
    }
}

@Composable
private fun CalendarGrid(
    yearMonth: YearMonth,
    entriesByDate: Map<LocalDate, List<DiaryEntry>>,
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate?) -> Unit
) {
    val daysOfWeek = listOf("L", "M", "X", "J", "V", "S", "D")
    val firstDay = yearMonth.atDay(1)
    val startOffset = firstDay.dayOfWeek.value - 1
    val daysInMonth = yearMonth.lengthOfMonth()
    val today = LocalDate.now()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            daysOfWeek.forEach { day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        var dayCounter = 1
        val totalCells = startOffset + daysInMonth
        val rows = (totalCells + 6) / 7

        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (col in 0..6) {
                    val cellIndex = row * 7 + col
                    if (cellIndex < startOffset || dayCounter > daysInMonth) {
                        Box(modifier = Modifier.weight(1f).size(36.dp))
                    } else {
                        val date = yearMonth.atDay(dayCounter)
                        val dayEntries = entriesByDate[date] ?: emptyList()
                        val isSelected = date == selectedDate
                        val isToday = date == today

                        DayCell(
                            day = dayCounter,
                            hasEntries = dayEntries.isNotEmpty(),
                            entryCount = dayEntries.size,
                            isSelected = isSelected,
                            isToday = isToday,
                            onClick = {
                                onDateSelected(if (isSelected) null else date)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        dayCounter++
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    hasEntries: Boolean,
    entryCount: Int,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .then(
                if (isSelected) Modifier.background(
                    MaterialTheme.colorScheme.primaryContainer,
                    CircleShape
                )
                else if (isToday) Modifier.background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape
                )
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                ),
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            if (hasEntries) {
                Box(
                    modifier = Modifier
                        .size(if (entryCount > 3) 6.dp else 4.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape
                        )
                )
            }
        }
    }
}

private fun formatSelectedDate(date: LocalDate, locale: Locale): String {
    val day = date.dayOfMonth
    val month = date.month.getDisplayName(TextStyle.FULL, locale)
    return "$day de $month"
}
