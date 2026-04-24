package com.trama.app.ui.components

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trama.app.ui.theme.LocalTramaColors

/**
 * Compact tinted indicator bar shown while a recording is active. Matches the
 * Trama Redesign v2 pattern: tinted surface with 1px border, pulsing dot,
 * monospaced timer and outlined Stop pill.
 */
@Composable
fun RecordingIndicatorBar(
    elapsedSeconds: Long,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalTramaColors.current
    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val timeStr = "Grabando — %d:%02d".format(minutes, seconds)

    val infiniteTransition = rememberInfiniteTransition(label = "rec_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "dot"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = t.redBg,
        border = BorderStroke(1.dp, t.red.copy(alpha = 0.33f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(dotAlpha)
                    .clip(CircleShape)
                    .background(t.red)
            )
            Text(
                text = timeStr,
                color = t.red,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Surface(
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onStop() },
                shape = RoundedCornerShape(8.dp),
                color = androidx.compose.ui.graphics.Color.Transparent,
                border = BorderStroke(1.dp, t.red.copy(alpha = 0.4f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = null,
                        tint = t.red,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Detener",
                        style = MaterialTheme.typography.labelMedium,
                        color = t.red,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
