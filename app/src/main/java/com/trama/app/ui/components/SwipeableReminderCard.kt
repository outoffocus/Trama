package com.trama.app.ui.components

import android.app.DatePickerDialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trama.shared.model.DiaryEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableReminderCard(
    entry: DiaryEntry,
    enabled: Boolean,
    onMarkDone: () -> Unit,
    onPostponeSelected: (Long, String) -> Unit,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        content()
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPostponeSheet by remember(entry.id) { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onMarkDone()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    showPostponeSheet = true
                    false
                }
                else -> false
            }
        },
        positionalThreshold = { it * 0.35f }
    )

    if (showPostponeSheet) {
        val chooseDate: (Int, Int, Int) -> Unit = { year, month, dayOfMonth ->
            val dueDate = resolveCustomDate(
                year = year,
                month = month,
                dayOfMonth = dayOfMonth,
                previousDueDate = entry.dueDate
            )
            val label = SimpleDateFormat("d MMM", Locale("es")).format(Date(dueDate))
            onPostponeSelected(dueDate, label)
            showPostponeSheet = false
        }

        ModalBottomSheet(
            onDismissRequest = { showPostponeSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Posponer recordatorio",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Mueve esta tarea a otro momento sin perderla.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {
                            onPostponeSelected(
                                resolveTomorrow(entry.dueDate),
                                "mañana"
                            )
                            showPostponeSheet = false
                        },
                        label = { Text("Mañana") },
                        leadingIcon = {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )
                    AssistChip(
                        onClick = {
                            onPostponeSelected(
                                resolveWeekend(entry.dueDate),
                                "este finde"
                            )
                            showPostponeSheet = false
                        },
                        label = { Text("Este finde") },
                        leadingIcon = {
                            Icon(Icons.Default.EventRepeat, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {
                            onPostponeSelected(
                                resolveNextWeek(entry.dueDate),
                                "la semana que viene"
                            )
                            showPostponeSheet = false
                        },
                        label = { Text("Semana que viene") },
                        leadingIcon = {
                            Icon(Icons.Default.EventRepeat, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )
                    AssistChip(
                        onClick = {
                            val today = Calendar.getInstance()
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    chooseDate(year, month, dayOfMonth)
                                },
                                today.get(Calendar.YEAR),
                                today.get(Calendar.MONTH),
                                today.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        label = { Text("Elegir fecha") },
                        leadingIcon = {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )
                }
            }
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val (bgColor, icon, label, alignment) = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> SwipeReminderInfo(
                    Color(0xFF2E7D32),
                    Icons.Default.CheckCircle,
                    "Hecho",
                    Alignment.CenterStart
                )
                SwipeToDismissBoxValue.EndToStart -> SwipeReminderInfo(
                    Color(0xFFE65100),
                    Icons.Default.Schedule,
                    "Otro dia",
                    Alignment.CenterEnd
                )
                else -> SwipeReminderInfo(Color.Transparent, Icons.Default.CheckCircle, "", Alignment.CenterStart)
            }

            val animatedColor by animateColorAsState(
                targetValue = bgColor,
                animationSpec = tween(150),
                label = "entrySwipeBg"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .background(animatedColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                if (direction != SwipeToDismissBoxValue.Settled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (direction == SwipeToDismissBoxValue.EndToStart) {
                            Text(
                                label,
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
                        if (direction == SwipeToDismissBoxValue.StartToEnd) {
                            Text(
                                label,
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    ) {
        content()
    }
}

private data class SwipeReminderInfo(
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val alignment: Alignment
)

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

private fun resolveWeekend(previousDueDate: Long?): Long =
    Calendar.getInstance().apply {
        while (get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
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
