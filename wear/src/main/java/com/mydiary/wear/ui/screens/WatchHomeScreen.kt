package com.mydiary.wear.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import com.mydiary.wear.service.WatchServiceController
import com.mydiary.wear.ui.DatabaseProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WatchHomeScreen(
    onEntryClick: (Long) -> Unit,
    onViewAll: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val serviceRunning by WatchServiceController.isRunning.collectAsState()

    // Lightweight queries — only fetch last entry + count, not all entries
    val repository = remember { DatabaseProvider.getRepository(context) }
    val lastEntry by repository.getLatest().collectAsState(initial = null)
    val totalCount by repository.countAll().collectAsState(initial = 0)

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        // Toggle: start/stop listening
        item {
            ToggleChip(
                checked = serviceRunning,
                onCheckedChange = {
                    if (serviceRunning) {
                        WatchServiceController.stopByUser(context)
                    } else {
                        WatchServiceController.start(context)
                    }
                },
                label = {
                    Text(if (serviceRunning) "Escuchando" else "Detenido")
                },
                toggleControl = {
                    androidx.wear.compose.material.Switch(checked = serviceRunning)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Show only the last captured entry
        val entry = lastEntry
        if (entry != null) {
            item {
                Text(
                    text = "Última captura",
                    style = MaterialTheme.typography.caption2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 2.dp)
                )
            }

            item {
                Card(
                    onClick = { onEntryClick(entry.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = timeFormat.format(Date(entry.createdAt)),
                        style = MaterialTheme.typography.caption2
                    )
                    Text(
                        text = entry.displayText,
                        style = MaterialTheme.typography.body2,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // "Ver todo" button — only if there are more entries
            if (totalCount > 1) {
                item {
                    Chip(
                        onClick = onViewAll,
                        label = {
                            Text("Ver todo ($totalCount)")
                        },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            item {
                Text(
                    text = "Sin entradas",
                    style = MaterialTheme.typography.body2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                )
            }
        }

        // Settings button
        item {
            Chip(
                onClick = onSettingsClick,
                label = { Text("Ajustes") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
