package com.mydiary.wear.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import com.mydiary.wear.service.WatchServiceController

@Composable
fun WatchSettingsScreen() {
    val context = LocalContext.current
    var serviceRunning by remember { mutableStateOf(WatchServiceController.isRunning(context)) }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Ajustes",
                style = MaterialTheme.typography.title3
            )
        }

        item {
            ToggleChip(
                checked = serviceRunning,
                onCheckedChange = {
                    if (serviceRunning) {
                        WatchServiceController.stop(context)
                    } else {
                        WatchServiceController.start(context)
                    }
                    serviceRunning = !serviceRunning
                },
                label = { Text("Servicio") },
                secondaryLabel = {
                    Text(if (serviceRunning) "Activo" else "Inactivo")
                },
                toggleControl = {
                    androidx.wear.compose.material.Switch(checked = serviceRunning)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Chip(
                onClick = { /* Duration configured from phone */ },
                label = { Text("Duración") },
                secondaryLabel = { Text("Configurar desde teléfono") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Chip(
                onClick = { /* Sync status */ },
                label = { Text("Sincronización") },
                secondaryLabel = { Text("Automática") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
