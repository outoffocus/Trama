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
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.mydiary.shared.model.CategoryInfo
import com.mydiary.wear.ui.DatabaseProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WatchEntryDetailScreen(entryId: Long) {
    val context = LocalContext.current
    val repository = remember { DatabaseProvider.getRepository(context) }
    val entry by repository.getById(entryId).collectAsState(initial = null)

    val dateFormat = SimpleDateFormat("dd MMM HH:mm", Locale("es"))

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        val currentEntry = entry
        if (currentEntry != null) {
            val catInfo = CategoryInfo.DEFAULTS.find { it.id == currentEntry.category }
            val categoryLabel = catInfo?.label ?: currentEntry.category

            item {
                Text(
                    text = categoryLabel,
                    style = MaterialTheme.typography.caption1,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            item {
                Text(
                    text = dateFormat.format(Date(currentEntry.createdAt)),
                    style = MaterialTheme.typography.caption2,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                Text(
                    text = currentEntry.text,
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            item {
                Text(
                    text = "Cargando...",
                    style = MaterialTheme.typography.body2
                )
            }
        }
    }
}
