package com.mydiary.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.mydiary.wear.service.RecordingController
import com.mydiary.wear.service.WatchServiceController

@Composable
fun WatchHomeScreen(
    onViewAll: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val serviceRunning by WatchServiceController.isRunning.collectAsState()

    val isRecording by RecordingController.isRecording.collectAsState()
    val elapsedSeconds by RecordingController.elapsedSeconds.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        // ── Recording indicator (visible when recording) ──
        if (isRecording) {
            item {
                val minutes = elapsedSeconds / 60
                val seconds = elapsedSeconds % 60
                val timeStr = "%02d:%02d".format(minutes, seconds)

                Chip(
                    onClick = { RecordingController.stopRecording(context) },
                    label = {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.Red)
                        )
                        Text(
                            text = "  REC $timeStr",
                            color = Color.Red
                        )
                    },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = Color.Red.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ── Record + Mic: two buttons side by side ──
        item {
            val teal = Color(0xFF00897B)
            val recRed = Color(0xFFD32F2F)
            val surface = Color(0xFF2C2C2C)
            val mutedIcon = Color(0xFF888888)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mic toggle (circle, primary — left)
                Button(
                    onClick = {
                        if (isRecording) return@Button
                        if (serviceRunning) {
                            WatchServiceController.stopByUser(context)
                        } else {
                            WatchServiceController.start(context)
                        }
                    },
                    modifier = Modifier.size(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (serviceRunning) teal else surface
                    )
                ) {
                    Icon(
                        imageVector = if (serviceRunning) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = if (serviceRunning) "Detener escucha" else "Escuchar",
                        modifier = Modifier.size(24.dp),
                        tint = if (serviceRunning) Color.White else mutedIcon
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Record button (smaller, secondary — right)
                Button(
                    onClick = {
                        if (isRecording) {
                            RecordingController.stopRecording(context)
                        } else {
                            if (!serviceRunning) WatchServiceController.start(context)
                            RecordingController.startRecording(context)
                        }
                    },
                    modifier = Modifier.size(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (isRecording) recRed else surface
                    )
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop
                                      else Icons.Default.FiberManualRecord,
                        contentDescription = if (isRecording) "Parar grabación" else "Grabar",
                        modifier = Modifier.size(20.dp),
                        tint = if (isRecording) Color.White else recRed
                    )
                }
            }
        }

        // Status label
        item {
            val teal = Color(0xFF00897B)
            val recRed = Color(0xFFD32F2F)
            Text(
                text = when {
                    isRecording -> "Grabando..."
                    serviceRunning -> "Escuchando..."
                    else -> "Inactivo"
                },
                style = MaterialTheme.typography.caption2,
                color = when {
                    isRecording -> recRed
                    serviceRunning -> teal
                    else -> Color.Gray
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Settings
        item {
            Chip(
                onClick = onSettingsClick,
                label = { Text("Ajustes") },
                icon = {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
