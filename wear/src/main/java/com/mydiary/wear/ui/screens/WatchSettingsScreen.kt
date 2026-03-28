package com.mydiary.wear.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.InlineSlider
import androidx.wear.compose.material.InlineSliderDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import com.google.android.gms.wearable.Wearable
import com.mydiary.wear.speech.WatchSpeakerEnrollment
import com.mydiary.wear.ui.DatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "watch_settings"
private const val KEY_SPEAKER_VERIFICATION = "speaker_verification_enabled"
private const val KEY_SPEAKER_THRESHOLD = "speaker_threshold"

@Composable
fun WatchSettingsScreen(
    onEnrollClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enrollment = remember { WatchSpeakerEnrollment(context) }
    val isEnrolled = enrollment.isEnrolled()

    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var speakerVerificationEnabled by remember {
        mutableStateOf(prefs.getBoolean(KEY_SPEAKER_VERIFICATION, false))
    }
    var threshold by remember {
        mutableFloatStateOf(prefs.getFloat(KEY_SPEAKER_THRESHOLD, 0.45f))
    }
    var syncStatus by remember { mutableStateOf("Automática") }

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

        // Only show verification options if enrolled
        if (isEnrolled) {
            item {
                ToggleChip(
                    checked = speakerVerificationEnabled,
                    onCheckedChange = { enabled ->
                        speakerVerificationEnabled = enabled
                        prefs.edit().putBoolean(KEY_SPEAKER_VERIFICATION, enabled).apply()
                    },
                    label = { Text("Verificar mi voz") },
                    secondaryLabel = {
                        Text(if (speakerVerificationEnabled) "Solo tu voz" else "Cualquier voz")
                    },
                    toggleControl = {
                        androidx.wear.compose.material.Switch(checked = speakerVerificationEnabled)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Threshold slider — only when verification is on
            if (speakerVerificationEnabled) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = "Sensibilidad: ${"%.0f".format(threshold * 100)}%",
                            style = MaterialTheme.typography.caption1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = if (threshold < 0.3f) "Muy permisivo"
                                   else if (threshold < 0.45f) "Permisivo"
                                   else if (threshold < 0.6f) "Equilibrado"
                                   else if (threshold < 0.75f) "Estricto"
                                   else "Muy estricto",
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        InlineSlider(
                            value = threshold,
                            onValueChange = { newValue ->
                                threshold = newValue
                                prefs.edit().putFloat(KEY_SPEAKER_THRESHOLD, newValue).apply()
                            },
                            valueRange = 0.2f..0.9f,
                            steps = 13,
                            increaseIcon = { InlineSliderDefaults.Increase },
                            decreaseIcon = { InlineSliderDefaults.Decrease },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        item {
            Chip(
                onClick = {
                    syncStatus = "Sincronizando..."
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val db = DatabaseProvider.getDatabase(context)
                                db.clearAllTables()
                            }

                            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
                            if (nodes.isEmpty()) {
                                syncStatus = "Sin conexión"
                                return@launch
                            }
                            for (node in nodes) {
                                Wearable.getMessageClient(context)
                                    .sendMessage(node.id, "/mydiary/request-full-sync", byteArrayOf())
                                    .await()
                            }
                            syncStatus = "✓ Sincronizado"
                        } catch (e: Exception) {
                            syncStatus = "Error"
                        }
                    }
                },
                label = { Text("Forzar sincronización") },
                secondaryLabel = { Text(syncStatus) },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
