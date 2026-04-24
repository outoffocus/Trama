package com.trama.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Watch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trama.app.ui.theme.LocalTramaColors

enum class TramaTab { Home, Calendar }

enum class TramaMicState { Idle, Listening, Recording, Watch }

/**
 * Bottom navigation with a centred mic FAB. Implements the v2 redesign where the
 * whole app collapses to two top-level tabs (Hoy / Historial) and the mic stays
 * pinned as the primary action.
 *
 * Behaviour is delegated to the caller via [onMicTap] and [onMicLongPress].
 */
@Composable
fun TramaBottomBar(
    active: TramaTab,
    onTabSelected: (TramaTab) -> Unit,
    modifier: Modifier = Modifier,
    micState: TramaMicState? = null,
    onMicTap: (() -> Unit)? = null,
    onMicLongPress: (() -> Unit)? = null,
    centerSlot: (@Composable () -> Unit)? = null,
) {
    val t = LocalTramaColors.current
    val showMic = micState != null && onMicTap != null
    val reserveCenter = showMic || centerSlot != null
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = t.surface,
        border = null,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = 8.dp, bottom = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavTab(
                    label = "Hoy",
                    icon = Icons.Default.Home,
                    selected = active == TramaTab.Home,
                    onClick = { onTabSelected(TramaTab.Home) },
                    modifier = Modifier.weight(1f)
                )
                if (reserveCenter) {
                    Spacer(Modifier.width(88.dp))
                }
                NavTab(
                    label = "Historial",
                    icon = Icons.Default.CalendarMonth,
                    selected = active == TramaTab.Calendar,
                    onClick = { onTabSelected(TramaTab.Calendar) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (centerSlot != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 2.dp)
                ) {
                    centerSlot()
                }
            } else if (showMic) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 2.dp)
                ) {
                    MicFab(state = micState!!, onTap = onMicTap!!, onLongPress = onMicLongPress)
                }
            }
        }
    }
}

@Composable
private fun NavTab(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTramaColors.current
    val color = if (selected) t.teal else t.mutedText
    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MicFab(
    state: TramaMicState,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    val t = LocalTramaColors.current
    val (bg, icon, ring) = when (state) {
        TramaMicState.Idle -> Triple(t.dimText, Icons.Default.Mic, null)
        TramaMicState.Listening -> Triple(t.amber, Icons.Default.Mic, t.amber)
        TramaMicState.Recording -> Triple(t.red, Icons.Default.Stop, t.red)
        TramaMicState.Watch -> Triple(t.watch, Icons.Default.Watch, t.watch)
    }
    val breathe = state == TramaMicState.Listening || state == TramaMicState.Watch
    val pulse = state == TramaMicState.Recording

    Box(contentAlignment = Alignment.Center) {
        if (ring != null && breathe) {
            val infinite = rememberInfiniteTransition(label = "mic-ring")
            val scale by infinite.animateFloat(
                initialValue = 1f,
                targetValue = 1.25f,
                animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "mic-scale"
            )
            Box(
                modifier = Modifier
                    .size((64 * scale).dp)
                    .clip(CircleShape)
                    .background(ring.copy(alpha = 0.15f))
            )
        }
        if (ring != null && pulse) {
            val infinite = rememberInfiniteTransition(label = "mic-pulse")
            val scale by infinite.animateFloat(
                initialValue = 1f,
                targetValue = 1.55f,
                animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Restart),
                label = "mic-pulse-scale"
            )
            val alpha by infinite.animateFloat(
                initialValue = 0.7f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Restart),
                label = "mic-pulse-alpha"
            )
            Box(
                modifier = Modifier
                    .size((56 * scale).dp)
                    .clip(CircleShape)
                    .border(2.dp, ring.copy(alpha = alpha), CircleShape)
            )
        }

        Surface(
            modifier = Modifier
                .size(56.dp)
                .clickable { onTap() },
            shape = CircleShape,
            color = bg,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Mic",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
