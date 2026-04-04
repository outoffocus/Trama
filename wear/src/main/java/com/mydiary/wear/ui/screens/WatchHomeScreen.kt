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
import androidx.compose.material.icons.filled.PhoneAndroid
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
    val phoneActive by WatchServiceController.isPhoneActive.collectAsState()

    val isRecording by RecordingController.isRecording.collectAsState()
    val elapsedSeconds by RecordingController.elapsedSeconds.collectAsState()

    val teal = Color(0xFF00897B)
    val recRed = Color(0xFFD32F2F)
    val phoneBlue = Color(0xFF1E88E5)
    val surface = Color(0xFF2C2C2C)
    val mutedIcon = Color(0xFF888888)

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

        // ── 3 buttons: Keyword | Record | Transfer ──
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Keyword toggle (left)
                Button(
                    onClick = {
                        if (phoneActive) return@Button
                        if (serviceRunning) {
                            WatchServiceController.stopByUser(context)
                        } else {
                            WatchServiceController.start(context)
                        }
                    },
                    modifier = Modifier.size(46.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (serviceRunning) teal else surface
                    )
                ) {
                    Icon(
                        imageVector = if (serviceRunning) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = if (serviceRunning) "Detener escucha" else "Escuchar",
                        modifier = Modifier.size(22.dp),
                        tint = if (serviceRunning) Color.White else mutedIcon
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 2. Record toggle (center)
                Button(
                    onClick = {
                        if (phoneActive) return@Button
                        if (isRecording) {
                            RecordingController.stopRecording(context)
                        } else {
                            WatchServiceController.startRecording(context)
                        }
                    },
                    modifier = Modifier.size(42.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (isRecording) recRed else surface
                    )
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop
                                      else Icons.Default.FiberManualRecord,
                        contentDescription = if (isRecording) "Parar grabación" else "Grabar",
                        modifier = Modifier.size(18.dp),
                        tint = if (isRecording) Color.White else recRed
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 3. Transfer (right)
                Button(
                    onClick = {
                        if (phoneActive) {
                            // Take back from phone — start keyword locally
                            WatchServiceController.start(context)
                        } else if (serviceRunning || isRecording) {
                            // Transfer active mode to phone
                            WatchServiceController.transferToPhone(context)
                        }
                    },
                    modifier = Modifier.size(42.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (phoneActive) phoneBlue else surface
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = if (phoneActive) "Traer de vuelta" else "Transferir al móvil",
                        modifier = Modifier.size(20.dp),
                        tint = if (phoneActive) Color.White else mutedIcon
                    )
                }
            }
        }

        // Status label
        item {
            Text(
                text = when {
                    isRecording -> "Grabando..."
                    serviceRunning -> "Escuchando..."
                    phoneActive -> "Móvil activo"
                    else -> "Inactivo"
                },
                style = MaterialTheme.typography.caption2,
                color = when {
                    isRecording -> recRed
                    serviceRunning -> teal
                    phoneActive -> phoneBlue
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
