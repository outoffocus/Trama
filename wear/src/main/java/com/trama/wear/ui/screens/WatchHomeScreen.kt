package com.trama.wear.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
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
    val triggerRecognized by WatchServiceController.triggerRecognized.collectAsState()
    val isRecording by RecordingController.isRecording.collectAsState()
    val elapsedSeconds by RecordingController.elapsedSeconds.collectAsState()
    val recordingKind by RecordingController.recordingKind.collectAsState()
    val batteryPct = rememberBatteryPercentage(context)
    val batteryLow = batteryPct in 1..20
    val isDirectCapture = isRecording && recordingKind == com.trama.wear.service.WatchRecordingService.KIND_DIRECT_CAPTURE

    val listenColor = Color(0xFFD48A52)
    val recordColor = Color(0xFFE06A5C)
    val directColor = Color(0xFF79B8A6)
    val transferColor = Color(0xFF6EA1FF)
    val idleSurface = Color(0xFF171A1C)
    val mutedIcon = Color(0xFF9A958C)

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
                        isDirectCapture -> "Captura rápida"
                        isRecording -> "Reunión"
                        triggerRecognized -> "Palabra clave reconocida"
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
                            triggerRecognized -> append("Escuchando tu petición")
                            serviceRunning -> append("Escuchando en el reloj")
                            phoneActive -> append("El teléfono está escuchando")
                            else -> append("Elige un modo")
                        }
                        if (batteryPct > 0) append(" · batería $batteryPct%")
                    },
                    style = MaterialTheme.typography.caption2,
                    color = when {
                        isDirectCapture -> directColor
                        isRecording -> recordColor
                        triggerRecognized -> directColor
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

        if (isRecording) {
            item {
                Chip(
                    onClick = { RecordingController.stopRecording(context) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    label = {
                        Text(
                            if (isDirectCapture) "Detener captura" else "Detener reunión"
                        )
                    },
                    secondaryLabel = { Text("%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)) },
                    icon = { Icon(Icons.Default.Stop, contentDescription = null) },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = recordColor,
                        contentColor = Color.White,
                        secondaryContentColor = Color.White
                    )
                )
            }
        } else {
            item {
                Chip(
                    onClick = {
                        when {
                            phoneActive -> WatchServiceController.reclaimFromPhone(context)
                            serviceRunning -> WatchServiceController.stopByUser(context)
                            else -> WatchServiceController.start(context)
                        }
                    },
                    enabled = !batteryLow,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    label = { Text(if (serviceRunning) "Pausar escucha" else "Escuchar aquí") },
                    icon = {
                        Icon(
                            if (serviceRunning) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = null
                        )
                    },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = if (serviceRunning) listenColor else idleSurface,
                        contentColor = if (serviceRunning) Color.White else mutedIcon
                    )
                )
            }
            item {
                Chip(
                    onClick = { WatchServiceController.startRecording(context) },
                    enabled = !phoneActive,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    label = { Text("Grabar reunión") },
                    icon = { Icon(Icons.Default.FiberManualRecord, contentDescription = null) },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = idleSurface,
                        contentColor = recordColor
                    )
                )
            }
            if (!phoneActive) item {
                Chip(
                    onClick = { WatchServiceController.transferToPhone(context) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    label = { Text("Usar el teléfono") },
                    icon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = idleSurface,
                        contentColor = transferColor
                    )
                )
            }
        }
    }
}

@Composable
private fun rememberBatteryPercentage(context: Context): Int {
    var batteryPct by remember { mutableIntStateOf(readBatteryPercentage(context)) }

    DisposableEffect(context) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                val nextPct = intent.toBatteryPercentage()
                if (nextPct > 0) {
                    batteryPct = nextPct
                }
            }
        }

        val sticky = context.registerReceiver(receiver, filter)
        val stickyPct = sticky.toBatteryPercentage()
        if (stickyPct > 0) {
            batteryPct = stickyPct
        }

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    return batteryPct
}

private fun readBatteryPercentage(context: Context): Int {
    val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val stickyPct = sticky.toBatteryPercentage()
    if (stickyPct > 0) return stickyPct

    return context.getSystemService(BatteryManager::class.java)
        ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        ?: -1
}

private fun Intent?.toBatteryPercentage(): Int {
    val level = this?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = this?.getIntExtra(BatteryManager.EXTRA_SCALE, -1)?.coerceAtLeast(1) ?: 1
    return if (level >= 0) (level * 100) / scale else -1
}
