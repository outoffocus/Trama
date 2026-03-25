package com.mydiary.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mydiary.shared.model.DiaryEntry
import com.mydiary.shared.model.EntryActionType
import com.mydiary.shared.model.EntryPriority
import com.mydiary.shared.model.EntryStatus
import com.mydiary.shared.model.Source
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Card for a single diary entry / action item.
 *
 * Layout (normal mode):
 * ┌────────────────────────────────────────────┬────┐
 * │ 📋 Pendiente  ⬆  🤖       📱 10:34       │ ☐  │
 * │                                            │    │
 * │ Llamar al dentista mañana                  │    │
 * │ ⏰ Miércoles                               │    │
 * └────────────────────────────────────────────┴────┘
 *
 * Layout (selection mode):
 * ┌────┬────────────────────────────────────────────┐
 * │ ☑  │ 📋 Pendiente            📱 10:34           │
 * │    │ Llamar al dentista mañana                  │
 * └────┴────────────────────────────────────────────┘
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryCard(
    entry: DiaryEntry,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onToggleComplete: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val isCompleted = entry.status == EntryStatus.COMPLETED

    val cardColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            isCompleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else -> MaterialTheme.colorScheme.surface
        },
        label = "cardColor"
    )

    val priorityColor = when (entry.priority) {
        EntryPriority.URGENT -> MaterialTheme.colorScheme.error
        EntryPriority.HIGH -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCompleted) 0.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Left: Selection checkbox (only in selection mode) ──
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null, // handled by card click
                    modifier = Modifier.padding(start = 8.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                )
            }

            // ── Left accent bar (only when NOT in selection mode) ──
            if (!isSelectionMode) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                        .background(
                            if (isCompleted)
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                            else
                                priorityColor.copy(alpha = 0.7f)
                        )
                )
            }

            // ── Center: Content ──
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = 14.dp,
                        end = if (onToggleComplete != null && !isSelectionMode) 0.dp else 14.dp,
                        top = 12.dp,
                        bottom = 12.dp
                    )
            ) {
                // Row 1: metadata chips + time
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Action type chip
                    val actionEmoji = EntryActionType.emoji(entry.actionType)
                    val actionLabel = EntryActionType.label(entry.actionType)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(
                            alpha = if (isCompleted) 0.3f else 0.6f
                        )
                    ) {
                        Text(
                            text = "$actionEmoji $actionLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }

                    // Priority badge
                    if (!isCompleted && (entry.priority == EntryPriority.URGENT || entry.priority == EntryPriority.HIGH)) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            shape = CircleShape,
                            color = priorityColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = if (entry.priority == EntryPriority.URGENT) "!" else "\u2191",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = priorityColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }

                    // Manual badge
                    if (entry.isManual) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Manual",
                            modifier = Modifier.size(11.dp),
                            tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                        )
                    }

                    // AI badge
                    if (entry.wasReviewedByLLM) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "IA",
                            modifier = Modifier.size(11.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Source + time
                    Icon(
                        imageVector = if (entry.source == Source.WATCH) Icons.Default.Watch else Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = timeFormat.format(Date(entry.createdAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Row 2: Main text
                Text(
                    text = entry.displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCompleted)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    else
                        MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )

                // Row 3: Due date (if any)
                val due = entry.dueDate
                if (due != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val now = System.currentTimeMillis()
                    val isOverdue = due < now && !isCompleted
                    val daysLeft = TimeUnit.MILLISECONDS.toDays(due - now)
                    val dueDateText = when {
                        isOverdue -> "Vencida"
                        daysLeft == 0L -> "Hoy"
                        daysLeft == 1L -> "Mañana"
                        daysLeft < 7 -> {
                            val dayFormat = SimpleDateFormat("EEEE", Locale("es"))
                            dayFormat.format(Date(due)).replaceFirstChar { it.uppercase() }
                        }
                        else -> SimpleDateFormat("d MMM", Locale("es")).format(Date(due))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = if (isOverdue)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = dueDateText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOverdue)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            // ── Right: Completion checkbox (normal mode only) ──
            if (!isSelectionMode && onToggleComplete != null) {
                Checkbox(
                    checked = isCompleted,
                    onCheckedChange = { onToggleComplete() },
                    modifier = Modifier.padding(end = 8.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        checkmarkColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    }
}
