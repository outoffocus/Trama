package com.trama.wear.ui.screens

import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.trama.wear.service.RecordingController
import com.trama.wear.service.WatchServiceController

@Composable
fun WatchHomeScreen() {
    val context = LocalContext.current
    val serviceRunning by WatchServiceController.isRunning.collectAsState()
    val phoneActive by WatchServiceController.isPhoneActive.collectAsState()
    val isRecording by RecordingController.isRecording.collectAsState()
    val elapsedSeconds by RecordingController.elapsedSeconds.collectAsState()

    val batteryManager = context.getSystemService(BatteryManager::class.java)
    val batteryPct = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    val batteryLow = batteryPct in 1..20

    val listenColor = Color(0xFF00897B)
    val recordColor = Color(0xFFD32F2F)
    val transferColor = Color(0xFF1E88E5)
    val idleSurface = Color(0xFF2C2C2C)
    val mutedIcon = Color(0xFF9E9E9E)

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = when {
                        isRecording -> "Grabadora"
                        serviceRunning -> "Escucha continua"
                        phoneActive -> "Control en teléfono"
                        else -> "Trama Watch"
                    },
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = buildString {
                        when {
                            isRecording -> {
                                val minutes = elapsedSeconds / 60
                                val seconds = elapsedSeconds % 60
                                append("Grabando ")
                                append("%02d:%02d".format(minutes, seconds))
                            }
                            serviceRunning -> append("Escuchando en el reloj")
                            phoneActive -> append("El teléfono está escuchando")
                            else -> append("Elige un modo")
                        }
                        if (batteryPct > 0) append(" · batería $batteryPct%")
                    },
                    style = MaterialTheme.typography.caption2,
                    color = when {
                        isRecording -> recordColor
                        serviceRunning -> listenColor
                        phoneActive -> transferColor
                        else -> Color.Gray
                    },
                    textAlign = TextAlign.Center
                )
            }
        }

        if (batteryLow && !isRecording) {
            item {
                Card(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Batería baja", color = Color(0xFFFFCC80))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "La escucha continua se desactiva para ahorrar batería.",
                            style = MaterialTheme.typography.caption2,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (phoneActive || batteryLow) return@Button
                        if (serviceRunning) WatchServiceController.stopByUser(context)
                        else WatchServiceController.start(context)
                    },
                    enabled = !phoneActive && !batteryLow,
                    modifier = Modifier.size(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (serviceRunning) listenColor else idleSurface
                    ),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = if (serviceRunning) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = if (serviceRunning) "Desactivar escucha continua" else "Activar escucha continua",
                        modifier = Modifier.size(24.dp),
                        tint = if (serviceRunning) Color.White else mutedIcon
                    )
                }

                Spacer(modifier = Modifier.size(10.dp))

                Button(
                    onClick = {
                        if (phoneActive) return@Button
                        if (isRecording) RecordingController.stopRecording(context)
                        else WatchServiceController.startRecording(context)
                    },
                    enabled = !phoneActive,
                    modifier = Modifier.size(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (isRecording) recordColor else idleSurface
                    ),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                        contentDescription = if (isRecording) "Parar grabadora" else "Iniciar grabadora",
                        modifier = Modifier.size(22.dp),
                        tint = if (isRecording) Color.White else recordColor
                    )
                }

                Spacer(modifier = Modifier.size(10.dp))

                Button(
                    onClick = {
                        if (phoneActive) WatchServiceController.reclaimFromPhone(context)
                        else WatchServiceController.transferToPhone(context)
                    },
                    modifier = Modifier.size(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (phoneActive) transferColor else idleSurface
                    ),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = if (phoneActive) "Recuperar desde el teléfono" else "Transferir al teléfono",
                        modifier = Modifier.size(22.dp),
                        tint = if (phoneActive) Color.White else mutedIcon
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ModeLegend("Escucha", listenColor)
                ModeLegend("Graba", recordColor)
                ModeLegend("Teléfono", transferColor)
            }
        }

        item {
            Text(
                text = "La escucha del reloj prioriza batería: captura ligera y transferencia al teléfono para el procesamiento serio.",
                style = MaterialTheme.typography.caption2,
                textAlign = TextAlign.Center,
                color = Color.Gray,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
            )
        }
    }
}

@Composable
private fun ModeLegend(label: String, color: Color) {
    Text(
        text = label,
        style = MaterialTheme.typography.caption3,
        color = color
    )
}
