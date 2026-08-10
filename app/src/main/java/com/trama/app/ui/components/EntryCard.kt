package com.trama.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
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
import com.trama.shared.model.EntryProcessingBackend
import com.trama.shared.model.EntryPriority
import com.trama.shared.model.EntryStatus
import com.trama.shared.model.Source
import com.trama.app.service.EntryProcessingState
import com.trama.app.ui.theme.LocalTramaColors
import com.trama.app.ui.theme.TramaColors

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
    processingBackend: EntryProcessingState.Backend? = null,
    accentColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val isCompleted = entry.status == EntryStatus.COMPLETED
    val isSuggested = entry.status == EntryStatus.SUGGESTED
    val primaryText = entry.displayText.ifBlank { entry.text }
    val t = LocalTramaColors.current
    val hasTrailingActions = !isSelectionMode && (
        (isSuggested && onToggleComplete != null) ||
            (onQuickActionClick != null && quickActionIcon != null)
        )

    val cardColor by animateColorAsState(
        targetValue = when {
            isSelected  -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            isCompleted -> t.surface.copy(alpha = 0.52f)
            else        -> t.surface2
        },
        label = "cardColor"
    )

    val priorityColor = when (entry.priority) {
        EntryPriority.URGENT -> MaterialTheme.colorScheme.error
        EntryPriority.HIGH   -> t.amber
        else                 -> accentColor ?: MaterialTheme.colorScheme.primary
    }
    val eventAccent = accentColor ?: MaterialTheme.colorScheme.primary
    val actionVisual = remember(entry.actionType, eventAccent) {
        actionVisualFor(entry.actionType, eventAccent, t)
    }
    val processingBadge = rememberProcessingBadge(
        entry = entry,
        isProcessing = isProcessing,
        processingBackend = processingBackend
    )
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
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 0.5.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                    else            t.softBorder
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
                        .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                        .background(
                            if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                            else             priorityColor.copy(alpha = 0.70f)
                        )
                )
            }

            ActionGlyph(
                icon = actionVisual.icon,
                tint = if (isCompleted) t.dimText else actionVisual.color,
                background = if (isCompleted) t.hairline else actionVisual.color.copy(alpha = 0.13f),
                modifier = Modifier.padding(start = 10.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start  = 10.dp,
                        end    = if (hasTrailingActions) 2.dp else 11.dp,
                        top    = 9.dp,
                        bottom = 9.dp
                    )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text           = primaryText,
                        style          = MaterialTheme.typography.titleMedium,
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
                    if ((isSuggested || entry.userConfirmedAt != null) && !isSelectionMode) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = t.teal.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = if (isSuggested) "SUGERIDA" else "CONFIRMADA",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = t.teal,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = actionVisual.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCompleted) t.dimText else actionVisual.color
                    )
                    Spacer(Modifier.width(8.dp))
                    EntrySourceIcon(entry.source)
                    if (processingBadge != null) ProcessingBadgeIcons(processingBadge)
                }
            }

            // ── Right action: quick-action button ───────────────────────────
            if (!isSelectionMode && isSuggested && onToggleComplete != null) {
                TextButton(
                    onClick = onToggleComplete,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text("Confirmar")
                }
            } else if (!isSelectionMode && onQuickActionClick != null && quickActionIcon != null) {
                EntryActionIconButton(
                    onClick = onQuickActionClick,
                    icon = quickActionIcon,
                    contentDescription = quickActionLabel,
                    tint = eventAccent
                )
            }
        }
    }
}

@Composable
private fun EntryActionIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    tint: Color
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.padding(end = 8.dp),
        shape = CircleShape,
        color = tint.copy(alpha = 0.13f)
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
                tint = tint
            )
        }
    }
}

// ── Processing badge helpers ─────────────────────────────────────────────────

private data class ActionVisual(
    val icon: ImageVector,
    val label: String,
    val color: Color
)

private data class ProcessingBadge(
    val icon: ImageVector,
    val tint: Color,
    val contentDescription: String,
    val showSparkle: Boolean = false
)

@Composable
private fun ActionGlyph(
    icon: ImageVector,
    tint: Color,
    background: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(34.dp),
        shape = RoundedCornerShape(8.dp),
        color = background
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun actionVisualFor(type: String, fallback: Color, t: TramaColors): ActionVisual {
    return when (type) {
        EntryActionType.CALL -> ActionVisual(Icons.Default.Phone, "Llamar", t.watch)
        EntryActionType.BUY -> ActionVisual(Icons.Default.ShoppingCart, "Comprar", t.amber)
        EntryActionType.SEND -> ActionVisual(Icons.Default.Send, "Enviar", t.teal)
        EntryActionType.EVENT -> ActionVisual(Icons.Default.Event, "Evento", t.warn)
        EntryActionType.REVIEW -> ActionVisual(Icons.Default.Search, "Revisar", t.teal)
        EntryActionType.TALK_TO -> ActionVisual(Icons.Default.Forum, "Hablar", t.watch)
        else -> ActionVisual(Icons.Default.CheckCircle, EntryActionType.label(type), fallback)
    }
}

@Composable
private fun EntrySourceIcon(source: Source) {
    val (icon, description, tint) = when (source) {
        Source.PHONE -> Triple(
            Icons.Default.Smartphone,
            "Capturado desde el telefono",
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.46f)
        )
        Source.WATCH -> Triple(
            Icons.Default.Watch,
            "Capturado desde el reloj",
            LocalTramaColors.current.watch.copy(alpha = 0.72f)
        )
        Source.SCREENSHOT -> Triple(
            Icons.Default.AutoAwesome,
            "Capturado desde pantalla",
            LocalTramaColors.current.amber.copy(alpha = 0.72f)
        )
    }

    Icon(
        imageVector = icon,
        contentDescription = description,
        modifier = Modifier
            .padding(end = 4.dp)
            .size(14.dp),
        tint = tint
    )
}

@Composable
private fun rememberProcessingBadge(
    entry: DiaryEntry,
    isProcessing: Boolean,
    processingBackend: EntryProcessingState.Backend?
): ProcessingBadge? {
    if (isProcessing) {
        val backend = processingBackend ?: EntryProcessingState.Backend.UNKNOWN
        return ProcessingBadge(
            icon = when (backend) {
                EntryProcessingState.Backend.LOCAL -> Icons.Default.AutoAwesome
                EntryProcessingState.Backend.UNKNOWN -> Icons.Default.AutoAwesome
            },
            tint = when (backend) {
                EntryProcessingState.Backend.LOCAL -> LocalTramaColors.current.teal.copy(alpha = 0.75f)
                EntryProcessingState.Backend.UNKNOWN -> LocalTramaColors.current.amber.copy(alpha = 0.85f)
            },
            contentDescription = when (backend) {
                EntryProcessingState.Backend.LOCAL -> "Procesando en este móvil"
                EntryProcessingState.Backend.UNKNOWN -> "Procesando"
            },
            showSparkle = true
        )
    }

    val isHeuristicProcessed = entry.processingBackend == EntryProcessingBackend.HEURISTIC

    return when {
        entry.isManual   -> ProcessingBadge(
            icon               = Icons.Default.CheckCircle,
            tint               = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
            contentDescription = "Entrada manual"
        )
        isHeuristicProcessed -> ProcessingBadge(
            icon               = Icons.Default.CheckCircle,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            contentDescription = "Procesado por reglas locales"
        )
        else -> null
    }
}

@Composable
private fun ProcessingBadgeIcons(badge: ProcessingBadge) {
    val sparkleAlpha = if (badge.showSparkle) {
        val infinite = rememberInfiniteTransition(label = "entry-processing")
        val alpha by infinite.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse
            ),
            label = "entry-processing-alpha"
        )
        alpha
    } else {
        1f
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = badge.icon,
            contentDescription = badge.contentDescription,
            modifier = Modifier
                .size(14.dp)
                .alpha(sparkleAlpha),
            tint = badge.tint
        )
    }
}
