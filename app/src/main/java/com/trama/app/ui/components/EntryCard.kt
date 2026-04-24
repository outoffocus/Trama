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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Card for a single diary entry / action item.
 *
 * Compact layout:
 * ┌───────────────────────────────────────────┐
 * │█ [Tarea] ⌚ ☁  ·············  10:34      │
 * │  Llamar al dentista                        │
 * │  ⏰ Mañana                            ☐   │
 * └───────────────────────────────────────────┘
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

    val startOfToday = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val cardColor by animateColorAsState(
        targetValue = when {
            isSelected  -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            isCompleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else        -> MaterialTheme.colorScheme.surface
        },
        label = "cardColor"
    )

    val priorityColor = when (entry.priority) {
        EntryPriority.URGENT -> MaterialTheme.colorScheme.error
        EntryPriority.HIGH   -> MaterialTheme.colorScheme.tertiary
        else                 -> accentColor ?: MaterialTheme.colorScheme.primary
    }
    val eventAccent = accentColor ?: MaterialTheme.colorScheme.primary
    val processingBadge = rememberProcessingBadge(entry = entry, isProcessing = isProcessing)
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCompleted) 0.dp else 1.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 0.5.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                    else            eventAccent.copy(alpha = 0.12f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Selection checkbox ──────────────────────────────────────────
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = fadeIn() + scaleIn(),
                exit  = fadeOut() + scaleOut()
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null,
                    modifier = Modifier.padding(start = 8.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor   = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                )
            }

            // ── Left priority / accent border ───────────────────────────────
            if (!isSelectionMode) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                        .background(
                            if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                            else             priorityColor.copy(alpha = 0.70f)
                        )
                )
            }

            // ── Main content column ─────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start  = 11.dp,
                        end    = if (onToggleComplete != null || onQuickActionClick != null) 2.dp else 11.dp,
                        top    = 9.dp,
                        bottom = 9.dp
                    )
            ) {
                // Title — primary and only content
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text           = primaryText,
                        style          = MaterialTheme.typography.titleSmall,
                        fontWeight     = if (isCompleted) FontWeight.Normal else FontWeight.SemiBold,
                        color          = if (isCompleted)
                                             MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                         else
                                             MaterialTheme.colorScheme.onSurface,
                        maxLines       = 2,
                        overflow       = TextOverflow.Ellipsis,
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        modifier       = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text  = timeFormat.format(Date(entry.createdAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)
                    )
                }
            }

            // ── Right action: quick-action button or completion checkbox ────
            if (!isSelectionMode) {
                if (onQuickActionClick != null && quickActionIcon != null) {
                    Surface(
                        onClick  = onQuickActionClick,
                        modifier = Modifier.padding(end = 8.dp),
                        shape    = CircleShape,
                        color    = eventAccent.copy(alpha = 0.10f)
                    ) {
                        Box(
                            modifier        = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector      = quickActionIcon,
                                contentDescription = quickActionLabel,
                                modifier         = Modifier.size(18.dp),
                                tint             = eventAccent
                            )
                        }
                    }
                } else if (onToggleComplete != null) {
                    Checkbox(
                        checked        = isCompleted,
                        onCheckedChange = { onToggleComplete() },
                        modifier       = Modifier.padding(end = 6.dp),
                        colors         = CheckboxDefaults.colors(
                            checkedColor   = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
                            checkmarkColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }
    }
}

// ── Processing badge helpers ─────────────────────────────────────────────────

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
            icon               = Icons.Default.AutoAwesome,
            tint               = MaterialTheme.colorScheme.primary,
            contentDescription = "Procesando"
        )
    }

    val isCloudProcessed = entry.wasReviewedByLLM && (entry.llmConfidence ?: 0f) >= 0.85f
    val isLocalProcessed = (entry.wasReviewedByLLM && !isCloudProcessed) ||
        (!entry.wasReviewedByLLM && (entry.sourceRecordingId != null || entry.llmConfidence == 0.0f))

    return when {
        entry.isManual   -> ProcessingBadge(
            icon               = Icons.Default.CheckCircle,
            tint               = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
            contentDescription = "Entrada manual"
        )
        isCloudProcessed -> ProcessingBadge(
            icon               = Icons.Default.Cloud,
            tint               = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            contentDescription = "Procesado online"
        )
        isLocalProcessed -> ProcessingBadge(
            icon               = Icons.Default.CloudOff,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            contentDescription = "Procesado local"
        )
        else -> null
    }
}
