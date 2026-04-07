package com.trama.wear.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.trama.shared.model.DiaryEntry
import com.trama.shared.data.DatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WatchEntryDetailScreen(entryId: Long) {
    val context = LocalContext.current

    // Defer database access to background thread
    var entry by remember { mutableStateOf<DiaryEntry?>(null) }
    LaunchedEffect(entryId) {
        val repo = withContext(Dispatchers.IO) { DatabaseProvider.getRepository(context) }
        repo.getById(entryId).collect { entry = it }
    }

    val dateFormat = SimpleDateFormat("dd MMM HH:mm", Locale("es"))

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        val currentEntry = entry
        if (currentEntry != null) {
            item {
                Text(
                    text = currentEntry.keyword,
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
                    text = currentEntry.displayText,
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
