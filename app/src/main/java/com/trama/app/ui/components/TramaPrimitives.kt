package com.trama.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trama.app.ui.theme.LocalTramaColors

/**
 * Editorial section divider with label and optional counter/expand toggle.
 *
 *   ──── PENDIENTE · HOY  [4] ⌄ ────
 */
@Composable
fun SectionRule(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    accent: Color? = null,
    expanded: Boolean? = null,
    onToggle: (() -> Unit)? = null,
    dimmed: Boolean = false,
) {
    val t = LocalTramaColors.current
    val color = when {
        dimmed -> t.dimText
        accent != null -> accent
        else -> t.mutedText
    }
    val rule = if (dimmed) t.hairline else color.copy(alpha = 0.2f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onToggle != null) it.clickable { onToggle() } else it }
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(rule)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = color.copy(alpha = 0.14f)
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
        if (onToggle != null && expanded != null) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(rule)
        )
    }
}

/** Status pill shown in headers. Single-line state indicator with a glowing dot. */
enum class TramaStatus { Idle, Listening, Recording, Watch, Location }

@Composable
fun StatusPill(
    status: TramaStatus,
    modifier: Modifier = Modifier,
    label: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val t = LocalTramaColors.current
    val (dot, bg, fg, defaultLabel) = when (status) {
        TramaStatus.Idle -> Quad(t.dimText, t.dimText.copy(alpha = 0.18f), t.mutedText, "Inactivo")
        TramaStatus.Listening -> Quad(t.amber, t.amberBg, t.amber, "Escuchando")
        TramaStatus.Recording -> Quad(t.red, t.redBg, t.red, "Grabando")
        TramaStatus.Watch -> Quad(t.watch, t.watchBg, t.watch, "En el reloj")
        TramaStatus.Location -> Quad(t.teal, t.tealBg, t.teal, "Ubicación activa")
    }
    val pulse = status == TramaStatus.Listening || status == TramaStatus.Recording
    val alpha = if (pulse) breathingAlpha() else 1f

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = bg,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dot.copy(alpha = alpha))
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = label ?: defaultLabel,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                fontWeight = FontWeight.Medium,
            )
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
private fun breathingAlpha(): Float {
    val infinite = rememberInfiniteTransition(label = "breathe")
    val v by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe-alpha"
    )
    return v
}

/** Soft "chip" button used for context strips, suggestions and inline quick actions. */
@Composable
fun TramaChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color? = null,
    selected: Boolean = false,
    leading: @Composable (() -> Unit)? = null,
) {
    val t = LocalTramaColors.current
    val c = color ?: t.amber
    val bg = if (selected) c.copy(alpha = 0.18f) else t.surface2
    val fg = if (selected) c else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = modifier.clip(RoundedCornerShape(20.dp)).clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = bg,
        border = BorderStroke(1.dp, if (selected) c.copy(alpha = 0.35f) else t.softBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = fg,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

/** Editorial "date hero" used at the top of Home. */
@Composable
fun DateHero(
    primary: String,
    caption: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = primary,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (caption != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalTramaColors.current.mutedText,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    background: Color? = null,
    content: @Composable () -> Unit,
) {
    val t = LocalTramaColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = background ?: t.surface,
        border = BorderStroke(1.dp, borderColor ?: t.softBorder),
    ) { content() }
}

/**
 * Discreet "transfer to watch" pill that sits next to the [StatusPill] while
 * the phone is listening.
 */
@Composable
fun TransferToWatchChip(
    toWatch: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTramaColors.current
    val label = if (toWatch) "Recuperar escucha" else "Transferir al reloj →"
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = t.watchBg,
        border = BorderStroke(1.dp, t.watch.copy(alpha = 0.3f)),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = t.watch,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
