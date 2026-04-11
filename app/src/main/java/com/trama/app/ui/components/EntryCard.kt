package com.trama.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryActionType
import com.trama.shared.model.EntryPriority
import com.trama.shared.model.EntryStatus
import com.trama.shared.model.Source
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
    onQuickActionClick: (() -> Unit)? = null,
    quickActionLabel: String? = null,
    quickActionIcon: ImageVector? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    isProcessing: Boolean = false,
    accentColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dayTimeFormat = SimpleDateFormat("d MMM · HH:mm", Locale("es"))
    val isCompleted = entry.status == EntryStatus.COMPLETED
    val primaryText = entry.displayText.ifBlank { entry.text }

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
    val eventAccent = accentColor ?: MaterialTheme.colorScheme.primary
    val processingBadge = rememberProcessingBadge(entry = entry, isProcessing = isProcessing)
    val metaParts = buildList {
        if (entry.source == Source.WATCH) add("Reloj")
        if (entry.isManual) add("Manual")
    }.joinToString(" · ")
    val cardInteractionSource = remember { MutableInteractionSource() }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = cardInteractionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCompleted) 0.dp else 1.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            } else {
                eventAccent.copy(alpha = 0.14f)
            }
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (!isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 12.dp)
                        .width(28.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(eventAccent.copy(alpha = if (isCompleted) 0.35f else 0.85f))
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedVisibility(
                    visible = isSelectionMode,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null,
                        modifier = Modifier.padding(start = 8.dp),
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    )
                }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val actionLabel = EntryActionType.label(entry.actionType)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = eventAccent.copy(alpha = if (isCompleted) 0.12f else 0.18f)
                        ) {
                            Text(
                                text = actionLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = eventAccent,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = if (isCompleted) {
                                timeFormat.format(Date(entry.createdAt))
                            } else {
                                dayTimeFormat.format(Date(entry.createdAt))
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = primaryText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isCompleted) FontWeight.Medium else FontWeight.SemiBold,
                        color = if (isCompleted)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    )

                    if (!isCompleted && (entry.priority == EntryPriority.URGENT || entry.priority == EntryPriority.HIGH) ||
                        processingBadge != null ||
                        metaParts.isNotBlank()
                    ) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!isCompleted && (entry.priority == EntryPriority.URGENT || entry.priority == EntryPriority.HIGH)) {
                                Surface(
                                    shape = CircleShape,
                                    color = priorityColor.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = if (entry.priority == EntryPriority.URGENT) "Urgente" else "Alta prioridad",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = priorityColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (processingBadge != null) {
                                if (!isCompleted && (entry.priority == EntryPriority.URGENT || entry.priority == EntryPriority.HIGH)) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Icon(
                                    processingBadge.icon,
                                    contentDescription = processingBadge.contentDescription,
                                    modifier = Modifier.size(14.dp),
                                    tint = processingBadge.tint
                                )
                            }

                            if (metaParts.isNotBlank()) {
                                if (processingBadge != null || (!isCompleted && (entry.priority == EntryPriority.URGENT || entry.priority == EntryPriority.HIGH))) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = metaParts,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

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
                                tint = if (isOverdue) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                }
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = dueDateText,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOverdue) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                }
                            )
                        }
                    }

                }

                if (!isSelectionMode && onQuickActionClick != null && quickActionIcon != null) {
                    Surface(
                        onClick = onQuickActionClick,
                        modifier = Modifier.padding(end = 10.dp),
                        shape = CircleShape,
                        color = eventAccent.copy(alpha = 0.12f)
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = quickActionIcon,
                                contentDescription = quickActionLabel,
                                modifier = Modifier.size(20.dp),
                                tint = eventAccent
                            )
                        }
                    }
                } else if (!isSelectionMode && onToggleComplete != null) {
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
}

private data class ProcessingBadge(
    val icon: ImageVector,
    val tint: Color,
    val contentDescription: String
)

@Composable
private fun rememberProcessingBadge(
    entry: DiaryEntry,
    isProcessing: Boolean
): ProcessingBadge? {
    if (isProcessing) {
        return ProcessingBadge(
            icon = Icons.Default.AutoAwesome,
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = "Procesando"
        )
    }

    val isCloudProcessed = entry.wasReviewedByLLM &&
        (entry.llmConfidence ?: 0f) >= 0.85f
    val isLocalProcessed = (entry.wasReviewedByLLM && !isCloudProcessed) ||
        (!entry.wasReviewedByLLM && (entry.sourceRecordingId != null || entry.llmConfidence == 0.0f))

    return when {
        entry.isManual -> ProcessingBadge(
            icon = Icons.Default.CheckCircle,
            tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
            contentDescription = "Entrada manual"
        )
        isCloudProcessed -> ProcessingBadge(
            icon = Icons.Default.Cloud,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            contentDescription = "Procesado online"
        )
        isLocalProcessed -> ProcessingBadge(
            icon = Icons.Default.CloudOff,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            contentDescription = "Procesado local"
        )
        else -> null
    }
}
