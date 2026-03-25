package com.mydiary.wear.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.mydiary.wear.speech.WatchSpeakerEnrollment

@Composable
fun WatchSettingsScreen(
    onEnrollClick: () -> Unit
) {
    val context = LocalContext.current
    val enrollment = remember { WatchSpeakerEnrollment(context) }
    val isEnrolled = enrollment.isEnrolled()

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
            Chip(
                onClick = onEnrollClick,
                label = { Text("Registro de voz") },
                secondaryLabel = {
                    Text(if (isEnrolled) "Registrado" else "No registrado")
                },
                colors = if (isEnrolled) ChipDefaults.secondaryChipColors()
                         else ChipDefaults.primaryChipColors(),
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
