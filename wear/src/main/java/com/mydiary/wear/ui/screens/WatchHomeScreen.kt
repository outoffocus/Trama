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
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import com.mydiary.shared.model.CategoryInfo
import com.mydiary.shared.model.DiaryEntry
import com.mydiary.wear.service.WatchServiceController
import com.mydiary.wear.ui.DatabaseProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WatchHomeScreen(
    onEntryClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { DatabaseProvider.getRepository(context) }
    val serviceRunning by WatchServiceController.isRunning.collectAsState()

    val entries by repository.getAll().collectAsState(initial = emptyList())
    val recentEntries = entries.take(10)

    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
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

        if (recentEntries.isEmpty()) {
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
        } else {
            items(recentEntries, key = { it.id }) { entry ->
                WatchEntryCard(
                    entry = entry,
                    timeFormat = timeFormat,
                    onClick = { onEntryClick(entry.id) }
                )
            }
        }
    }
}

@Composable
private fun WatchEntryCard(
    entry: DiaryEntry,
    timeFormat: SimpleDateFormat,
    onClick: () -> Unit
) {
    val catInfo = CategoryInfo.DEFAULTS.find { it.id == entry.category }
    val categoryLabel = catInfo?.label?.take(4)?.uppercase() ?: entry.category.take(4)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "$categoryLabel ${timeFormat.format(Date(entry.createdAt))}",
            style = MaterialTheme.typography.caption2
        )
        Text(
            text = entry.text,
            style = MaterialTheme.typography.body2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
