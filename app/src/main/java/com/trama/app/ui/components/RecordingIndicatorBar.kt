package com.trama.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Compact red indicator bar shown at the top of HomeScreen while recording.
 * Shows pulsing red dot + timer + stop button. Tappable to go to detail.
 */
@Composable
fun RecordingIndicatorBar(
    elapsedSeconds: Long,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val timeStr = "%02d:%02d".format(minutes, seconds)

    // Pulsing red dot
    val infiniteTransition = rememberInfiniteTransition(label = "rec_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "dot"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFD32F2F))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Pulsing dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .alpha(dotAlpha)
                .clip(CircleShape)
                .background(Color.White)
        )

        // Timer
        Text(
            text = "REC $timeStr",
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )

        // Stop button
        IconButton(
            onClick = onStop,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Stop,
                contentDescription = "Parar grabación",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
