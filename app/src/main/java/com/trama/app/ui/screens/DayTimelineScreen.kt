package com.trama.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.trama.app.service.EntryProcessingState
import com.trama.app.ui.SettingsDataStore
import com.trama.app.ui.theme.TimelineAccentConfig
import com.trama.app.ui.theme.timelineAccentColor
import com.trama.shared.data.DatabaseProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayTimelineScreen(
    dayStartMillis: Long,
    onBack: () -> Unit,
    onEntryClick: (Long) -> Unit,
    onRecordingClick: (Long) -> Unit,
    onPlaceClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { DatabaseProvider.getRepository(context) }
    val settings = remember { SettingsDataStore(context) }
    val dayEndMillis = remember(dayStartMillis) {
        com.trama.shared.util.DayRange.of(dayStartMillis).endExclusiveMs
    }
    val entries by repository.byDateRange(dayStartMillis, dayEndMillis).collectAsState(initial = emptyList())
    val recordings by repository.getAllRecordings().collectAsState(initial = emptyList())
    val storedTimelineEvents by repository.getTimelineEventsByDateRange(
        dayStartMillis,
        dayEndMillis
    ).collectAsState(initial = emptyList())
    val processingEntryIds by EntryProcessingState.processingIds.collectAsState()
    val processingBackends by EntryProcessingState.processingBackends.collectAsState()
    val pendingColorIndex by settings.timelineColorPending.collectAsState(
        initial = SettingsDataStore.DEFAULT_TIMELINE_COLOR_PENDING
    )
    val completedColorIndex by settings.timelineColorCompleted.collectAsState(
        initial = SettingsDataStore.DEFAULT_TIMELINE_COLOR_COMPLETED
    )
    val recordingColorIndex by settings.timelineColorRecording.collectAsState(
        initial = SettingsDataStore.DEFAULT_TIMELINE_COLOR_RECORDING
    )
    val placeColorIndex by settings.timelineColorPlace.collectAsState(
        initial = SettingsDataStore.DEFAULT_TIMELINE_COLOR_PLACE
    )
    val calendarColorIndex by settings.timelineColorCalendar.collectAsState(
        initial = SettingsDataStore.DEFAULT_TIMELINE_COLOR_CALENDAR
    )
    val timelineAccentConfig = remember(
        pendingColorIndex,
        completedColorIndex,
        recordingColorIndex,
        placeColorIndex,
        calendarColorIndex
    ) {
        TimelineAccentConfig(
            pending = timelineAccentColor(pendingColorIndex),
            completed = timelineAccentColor(completedColorIndex),
            recording = timelineAccentColor(recordingColorIndex),
            place = timelineAccentColor(placeColorIndex),
            calendar = timelineAccentColor(calendarColorIndex)
        )
    }
    val timelineEvents = remember(
        entries,
        recordings,
        dayStartMillis,
        dayEndMillis,
        storedTimelineEvents
    ) {
        buildTimelineEvents(
            createdEntries = entries.filter { it.createdAt in dayStartMillis until dayEndMillis },
            completedEntries = entries.filter { (it.completedAt ?: -1L) in dayStartMillis until dayEndMillis },
            recordings = recordings.filter { it.createdAt in dayStartMillis until dayEndMillis },
            storedEvents = storedTimelineEvents
        )
    }
    val title = remember(dayStartMillis) {
        SimpleDateFormat("EEEE d 'de' MMMM", Locale("es")).format(Date(dayStartMillis))
            .replaceFirstChar { it.uppercase() }
    }
    val monthLabel = remember(dayStartMillis) {
        SimpleDateFormat("MMMM yyyy", Locale("es")).format(Date(dayStartMillis))
            .replaceFirstChar { it.uppercase() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            monthLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors()
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = timelineAccentConfig.place.copy(alpha = 0.09f)
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        "Archivo del dia",
                        style = MaterialTheme.typography.labelLarge,
                        color = timelineAccentConfig.place
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Aqui ves solo lo que realmente ocurrio ese dia, en orden temporal y sin mezclarlo con pendientes posteriores.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TimelineList(
                events = timelineEvents,
                processingEntryIds = processingEntryIds,
                processingBackends = processingBackends,
                accentConfig = timelineAccentConfig,
                onEntryClick = onEntryClick,
                onRecordingClick = onRecordingClick,
                onPlaceClick = onPlaceClick,
                onToggleComplete = null,
                modifier = Modifier.padding(top = 4.dp),
                emptyTitle = "Nada registrado",
                emptyBody = "Ese día no tiene eventos todavía."
            )
        }
    }
}
