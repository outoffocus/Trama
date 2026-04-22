// TODO: Extract repository access into a ViewModel. Currently DatabaseProvider.getRepository(context)
//  is called directly inside the composable via remember {}. A ViewModel would provide proper
//  lifecycle-aware state management and enable testability. Requires dependency injection setup (Hilt).
package com.trama.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.trama.app.summary.ActionExecutor
import com.trama.app.summary.EntryActionBridge
import com.trama.app.summary.ActionType
import com.trama.app.summary.CalendarHelper
import com.trama.app.service.RecordingState
import com.trama.app.service.EntryProcessingState
import com.trama.app.service.ServiceController
import com.trama.app.sync.PhoneToWatchSyncer
import com.trama.app.ui.SettingsDataStore
import com.trama.shared.data.DatabaseProvider
import com.trama.app.ui.components.EntryCard
import com.trama.app.ui.components.CalendarActionDialog
import com.trama.app.ui.components.RecordingCard
import com.trama.app.ui.components.RecordingIndicatorBar
import com.trama.app.ui.components.SwipeableReminderCard
import com.trama.app.ui.theme.TimelineAccentConfig
import com.trama.app.ui.theme.timelineAccentColor
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryStatus
import com.trama.shared.model.Source
import com.trama.shared.model.StatusSyncEntry
import com.trama.shared.sync.MicCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Categories available for manual entry.
 */
private data class EntryCategory(
    val id: String,
    val emoji: String,
    val label: String
)

private val MANUAL_CATEGORIES = listOf(
    EntryCategory("pendiente", "\uD83D\uDCCB", "Pendiente"),
    EntryCategory("idea", "\uD83D\uDCA1", "Idea"),
    EntryCategory("compra", "\uD83D\uDED2", "Compra"),
    EntryCategory("gasto", "\uD83D\uDCB0", "Gasto"),
    EntryCategory("llamada", "\uD83D\uDCDE", "Llamada"),
    EntryCategory("nota", "\uD83D\uDCDD", "Nota")
)


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onEntryClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onCalendarClick: () -> Unit = {},
    onChatClick: () -> Unit = {},
    onRecordingClick: (Long) -> Unit = {},
    onPlaceClick: (Long) -> Unit = {},
    onRecordingsListClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { DatabaseProvider.getRepository(context) }
    val settings = remember { SettingsDataStore(context) }
    val watchSyncer = remember { PhoneToWatchSyncer(context, repository) }
    val scope = rememberCoroutineScope()

    // Full sync to watch on first open — ensures watch has current statuses
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            watchSyncer.syncAllToWatch()
        }
    }
    val serviceRunning by ServiceController.isRunning.collectAsState()
    val watchActive by ServiceController.isWatchActive.collectAsState()
    val locationRunning by ServiceController.isLocationRunning.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Recording state
    val isRecording by RecordingState.isRecording.collectAsState()
    val recElapsed by RecordingState.elapsedSeconds.collectAsState()
    val savedRecordingId by RecordingState.savedRecordingId.collectAsState()
    val lastError by RecordingState.lastError.collectAsState()
    val processingEntryIds by EntryProcessingState.processingIds.collectAsState()
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
    val showOldEntriesExpanded by settings.showOldEntriesExpanded.collectAsState(initial = false)
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

    // Navigate to recording detail when saved
    LaunchedEffect(savedRecordingId) {
        val id = savedRecordingId
        if (id != null) {
            RecordingState.clearSaved()
            onRecordingClick(id)
        }
    }

    // Show processing errors as snackbar
    LaunchedEffect(lastError) {
        val error = lastError
        if (error != null) {
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Long
            )
            RecordingState.clearError()
        }
    }

    // Unified selection mode
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }       // DiaryEntry ids
    var selectedRecIds by remember { mutableStateOf(setOf<Long>()) }    // Recording ids
    var selectedEventIds by remember { mutableStateOf(setOf<Long>()) }  // TimelineEvent ids

    fun exitSelectionMode() {
        selectionMode = false
        selectedIds = emptySet()
        selectedRecIds = emptySet()
        selectedEventIds = emptySet()
    }

    androidx.activity.compose.BackHandler(enabled = selectionMode) {
        exitSelectionMode()
    }

    // Add manual entry dialog
    var showAddDialog by remember { mutableStateOf(false) }

    var duplicatesExpanded by remember { mutableStateOf(true) }
    var olderExpanded by remember(showOldEntriesExpanded) { mutableStateOf(showOldEntriesExpanded) }
    var todayExpanded by remember { mutableStateOf(true) }
    var completedExpanded by remember { mutableStateOf(false) }
    var quickActionsVisible by remember { mutableStateOf(false) }
    var resumeListeningAfterRecording by remember { mutableStateOf(false) }

    LaunchedEffect(isRecording) {
        if (!isRecording && resumeListeningAfterRecording) {
            ServiceController.start(context)
            resumeListeningAfterRecording = false
        }
    }

    LaunchedEffect(serviceRunning, isRecording, watchActive) {
        if (isRecording || watchActive || !serviceRunning) {
            quickActionsVisible = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) ServiceController.start(context)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        scope.launch {
            settings.setLocationEnabled(granted)
        }
        if (granted) {
            ServiceController.startLocationTracking(context)
        }
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) RecordingState.startRecording(context)
    }

    // Data
    val allPendingEntriesState by repository.getPending().collectAsState(initial = null)
    val duplicateEntriesState by repository.getDuplicates().collectAsState(initial = null)
    val completedEntriesState by repository.getCompleted().collectAsState(initial = null)
    val recordingsState by repository.getAllRecordings().collectAsState(initial = null)
    val allPendingEntries = allPendingEntriesState ?: emptyList()
    val duplicateEntries = duplicateEntriesState ?: emptyList()
    val completedEntries = completedEntriesState ?: emptyList()
    val recordings = recordingsState ?: emptyList()

    // Filter duplicates out of the main pending list
    val duplicateIds = duplicateEntries.map { it.id }.toSet()
    val pendingEntries = allPendingEntries.filter { it.id !in duplicateIds }

    // Stats
    val todayRange = remember { com.trama.shared.util.DayRange.today() }
    val startOfDay = todayRange.startMs
    val endOfDay = todayRange.endInclusiveMs
    val storedTimelineEventsState by repository.getTimelineEventsByDateRange(
        startOfDay,
        endOfDay
    ).collectAsState(initial = null)
    val storedTimelineEvents = storedTimelineEventsState ?: emptyList()
    var todayCalendarEvents by remember(startOfDay) {
        mutableStateOf<List<CalendarHelper.CalendarEvent>?>(null)
    }
    LaunchedEffect(startOfDay) {
        todayCalendarEvents = withContext(Dispatchers.IO) {
            CalendarHelper.getEventsForRange(
                context = context,
                startMillis = startOfDay,
                endMillis = endOfDay
            )
        }
    }
    val resolvedCalendarEvents = todayCalendarEvents ?: emptyList()
    val visiblePendingEntries = pendingEntries
    val pastDayEntries = visiblePendingEntries.filter { entry ->
        val due = entry.dueDate
        entry.createdAt < startOfDay || (due != null && due < startOfDay)
    }
    val pastDayIds = pastDayEntries.map { it.id }.toSet()
    val todayEntries = visiblePendingEntries.filter { it.createdAt >= startOfDay && it.id !in pastDayIds }
    val olderEntries = pastDayEntries
    val completedTodayEntries = completedEntries.filter { (it.completedAt ?: 0L) >= startOfDay }
    val todayRecordings = recordings.filter { it.createdAt >= startOfDay }
    val timelineEvents = remember(
        todayEntries,
        todayRecordings,
        resolvedCalendarEvents,
        storedTimelineEvents
    ) {
        buildTimelineEvents(
            createdEntries = todayEntries,
            completedEntries = emptyList(),
            recordings = todayRecordings,
            calendarEvents = resolvedCalendarEvents.filter { it.startMillis in startOfDay..endOfDay },
            storedEvents = storedTimelineEvents
        )
    }

    val isInitialLoading = allPendingEntriesState == null ||
        duplicateEntriesState == null ||
        completedEntriesState == null ||
        recordingsState == null ||
        storedTimelineEventsState == null ||
        todayCalendarEvents == null

    val dateFormat = SimpleDateFormat("dd MMM", Locale("es"))
    val heroDayTitle = remember(startOfDay) {
        SimpleDateFormat("EEEE d 'de' MMMM", Locale("es")).format(Date(startOfDay))
            .replaceFirstChar { it.uppercase() }
    }
    // FAB colors
    val teal = Color(0xFF00897B)
    val recRed = Color(0xFFD32F2F)

    // Helper: sync a completed entry to watch
    fun syncCompleted(entry: DiaryEntry) {
        scope.launch {
            watchSyncer.syncStatusChange(
                completed = listOf(StatusSyncEntry(entry.createdAt, entry.text))
            )
        }
    }

    // Helper: sync a deleted entry to watch
    fun syncDeleted(entry: DiaryEntry) {
        scope.launch {
            watchSyncer.syncStatusChange(
                deleted = listOf(StatusSyncEntry(entry.createdAt, entry.text))
            )
        }
    }

    // Delete all selected (entries + recordings + timeline events)
    fun deleteSelected() {
        val entryIds = selectedIds.toList()
        val recIds = selectedRecIds.toList()
        val eventIds = selectedEventIds.toList()
        val entriesToDelete = (pendingEntries + completedEntries).filter { it.id in selectedIds }
        val total = entryIds.size + recIds.size + eventIds.size
        scope.launch {
            if (entryIds.isNotEmpty()) {
                repository.deleteByIds(entryIds)
                watchSyncer.syncStatusChange(
                    deleted = entriesToDelete.map { StatusSyncEntry(it.createdAt, it.text) }
                )
            }
            if (recIds.isNotEmpty()) repository.deleteRecordingsByIds(recIds)
            if (eventIds.isNotEmpty()) repository.deleteTimelineEventsByIds(eventIds)
            exitSelectionMode()
            snackbarHostState.showSnackbar(
                "$total ${if (total == 1) "elemento borrado" else "elementos borrados"}"
            )
        }
    }

    fun completeSelected() {
        val ids = selectedIds.toList()
        val entriesToComplete = (pendingEntries + completedEntries).filter { it.id in ids }
        scope.launch {
            repository.markCompletedByIds(ids)
            watchSyncer.syncStatusChange(
                completed = entriesToComplete.map { StatusSyncEntry(it.createdAt, it.text) }
            )
            exitSelectionMode()
            snackbarHostState.showSnackbar("${ids.size} completadas")
        }
    }

    fun markEntryDoneWithUndo(entry: DiaryEntry) {
        scope.launch {
            repository.markCompleted(entry.id)
            syncCompleted(entry)
            val result = snackbarHostState.showSnackbar(
                message = "\"${entry.displayText}\" marcada como hecha",
                actionLabel = "Deshacer",
                duration = SnackbarDuration.Short
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                repository.markPending(entry.id)
                repository.updateDueDate(entry.id, entry.dueDate)
            }
        }
    }

    fun postponeEntryWithUndo(entry: DiaryEntry, newDueDate: Long, label: String) {
        scope.launch {
            val previousDueDate = entry.dueDate
            repository.updateDueDate(entry.id, newDueDate)
            val result = snackbarHostState.showSnackbar(
                message = "\"${entry.displayText}\" pospuesta a $label",
                actionLabel = "Deshacer",
                duration = SnackbarDuration.Short
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                repository.updateDueDate(entry.id, previousDueDate)
            }
        }
    }


    // Add manual entry dialog
    if (showAddDialog) {
        AddEntryDialog(
            onDismiss = { showAddDialog = false },
            onSave = { text, categoryId ->
                val cat = MANUAL_CATEGORIES.find { it.id == categoryId } ?: MANUAL_CATEGORIES.last()
                scope.launch {
                    repository.insert(
                        DiaryEntry(
                            text = text,
                            keyword = cat.id,
                            category = cat.label,
                            confidence = 1.0f,
                            source = Source.PHONE,
                            duration = 0,
                            isManual = true,
                            cleanText = text
                        )
                    )
                }
                showAddDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            val totalSelected = selectedIds.size + selectedRecIds.size + selectedEventIds.size
            if (selectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            if (totalSelected == 0) "Selecciona elementos"
                            else "$totalSelected ${if (totalSelected == 1) "seleccionado" else "seleccionados"}"
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { exitSelectionMode() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancelar")
                        }
                    },
                    actions = {
                        if (selectedIds.isNotEmpty() && selectedRecIds.isEmpty() && selectedEventIds.isEmpty()) {
                            // Only entries selected — allow "complete"
                            IconButton(onClick = { completeSelected() }) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Completar",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(onClick = { deleteSelected() }, enabled = totalSelected > 0) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Borrar",
                                tint = if (totalSelected > 0) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "Trama",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                heroDayTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        // Add note
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Añadir nota")
                        }
                        IconButton(onClick = onChatClick) {
                            Icon(Icons.Default.Chat, contentDescription = "Asistente")
                        }
                        IconButton(onClick = onSearchClick) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar")
                        }
                        IconButton(onClick = onCalendarClick) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Calendario")
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!selectionMode) {
                MicControlFabGroup(
                    serviceRunning = serviceRunning,
                    isRecording = isRecording,
                    watchActive = watchActive,
                    quickActionsVisible = quickActionsVisible,
                    onQuickActionsVisibleChange = { quickActionsVisible = it },
                    onPrimaryClick = {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!hasPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            return@MicControlFabGroup
                        }
                        when {
                            isRecording -> {
                                quickActionsVisible = false
                                RecordingState.stopRecording(context)
                            }
                            watchActive -> {
                                quickActionsVisible = false
                                scope.launch(Dispatchers.IO) { MicCoordinator.sendPause(context) }
                                ServiceController.notifyWatchInactive()
                            }
                            serviceRunning -> {
                                quickActionsVisible = false
                                ServiceController.stop(context)
                            }
                            else -> {
                                quickActionsVisible = false
                                ServiceController.start(context)
                            }
                        }
                    },
                    onStartRecording = {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!hasPermission) {
                            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            return@MicControlFabGroup
                        }
                        quickActionsVisible = false
                        resumeListeningAfterRecording = serviceRunning
                        ServiceController.startRecording(context)
                    },
                    onTransferToWatch = {
                        quickActionsVisible = false
                        ServiceController.transferToWatch(context)
                    }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Recording indicator bar (visible while recording)
            AnimatedVisibility(
                visible = isRecording,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                RecordingIndicatorBar(
                    elapsedSeconds = recElapsed,
                    onStop = { RecordingState.stopRecording(context) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (!selectionMode && (locationRunning || serviceRunning || isRecording || watchActive)) {
                StatusOverviewRow(
                    serviceRunning = serviceRunning,
                    isRecording = isRecording,
                    watchActive = watchActive,
                    locationRunning = locationRunning,
                    onLocationToggle = {
                        quickActionsVisible = false
                        if (locationRunning) {
                            scope.launch {
                                settings.setLocationEnabled(false)
                            }
                            ServiceController.stopLocationTracking(context)
                        } else {
                            val hasLocationPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasLocationPermission) {
                                scope.launch {
                                    settings.setLocationEnabled(true)
                                }
                                ServiceController.startLocationTracking(context)
                            } else {
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        }
                    },
                    onMicroToggle = {
                        quickActionsVisible = false
                        val isMicroActive = isRecording || watchActive || serviceRunning
                        if (isMicroActive) {
                            ServiceController.stop(context)
                            if (watchActive) {
                                scope.launch(Dispatchers.IO) { MicCoordinator.sendPause(context) }
                                ServiceController.notifyWatchInactive()
                            }
                            if (isRecording) {
                                RecordingState.stopRecording(context)
                            }
                        } else {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPermission) {
                                ServiceController.start(context)
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }

            if (isInitialLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Cargando tu día...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (pendingEntries.isEmpty() && completedEntries.isEmpty() && recordings.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Edit, contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No hay tareas pendientes",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Toca + para añadir una o activa el micro",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (duplicateEntries.isNotEmpty()) {
                        item(key = "header_duplicates") {
                            SectionHeader(
                                "Posibles duplicados",
                                duplicateEntries.size,
                                MaterialTheme.colorScheme.tertiary,
                                expanded = duplicatesExpanded,
                                onToggle = { duplicatesExpanded = !duplicatesExpanded },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        if (duplicatesExpanded) {
                            items(duplicateEntries, key = { "dup_${it.id}" }) { entry ->
                                val originalEntry = allPendingEntries.find { it.id == entry.duplicateOfId }
                                DuplicateCard(
                                    entry = entry,
                                    originalText = originalEntry?.displayText,
                                    onKeep = { scope.launch { repository.clearDuplicate(entry.id) } },
                                    onDelete = { scope.launch { repository.deleteById(entry.id); syncDeleted(entry) } },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }

                    if (olderEntries.isNotEmpty()) {
                        item(key = "header_older") {
                            SectionHeader(
                                "Días pasados",
                                olderEntries.size,
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                expanded = olderExpanded,
                                onToggle = { olderExpanded = !olderExpanded },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        if (olderExpanded) {
                            items(olderEntries, key = { "older_${it.id}" }) { entry ->
                                EntryCardItem(
                                    entry = entry,
                                    accentColor = timelineAccentConfig.pending,
                                    selectionMode = selectionMode,
                                    selectedIds = selectedIds,
                                    onEntryClick = onEntryClick,
                                    isProcessing = entry.id in processingEntryIds,
                                    onToggleComplete = { markEntryDoneWithUndo(entry) },
                                    onPostpone = { dueDate, label -> postponeEntryWithUndo(entry, dueDate, label) },
                                    onSelectionChange = { id, sel ->
                                        selectedIds = if (sel) selectedIds + id else selectedIds - id
                                        if (selectedIds.isEmpty()) selectionMode = false
                                    },
                                    onEnterSelectionMode = {
                                        selectionMode = true
                                        selectedIds = setOf(entry.id)
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }

                    item(key = "header_timeline") {
                        SectionHeader(
                            "Hoy",
                            timelineEvents.size,
                            timelineAccentConfig.pending,
                            expanded = todayExpanded,
                            onToggle = { todayExpanded = !todayExpanded },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    if (todayExpanded) {
                        timelineListContent(
                            events = timelineEvents,
                            processingEntryIds = processingEntryIds,
                            hourFormat = SimpleDateFormat("HH:mm", Locale.getDefault()),
                            accentConfig = timelineAccentConfig,
                            itemModifier = Modifier.padding(horizontal = 16.dp),
                            keyPrefix = "today_",
                            onEntryClick = onEntryClick,
                            onRecordingClick = onRecordingClick,
                            onPlaceClick = onPlaceClick,
                            onToggleComplete = if (!selectionMode) { entry -> markEntryDoneWithUndo(entry) } else null,
                            onPostponeEntry = if (!selectionMode) { entry, dueDate, label -> postponeEntryWithUndo(entry, dueDate, label) } else null,
                            isSelectionMode = selectionMode,
                            selectedEntryIds = selectedIds,
                            onEntrySelectionChange = { id, sel ->
                                selectedIds = if (sel) selectedIds + id else selectedIds - id
                                if (selectedIds.isEmpty() && selectedRecIds.isEmpty() && selectedEventIds.isEmpty()) selectionMode = false
                            },
                            onEnterEntrySelectionMode = { entryId ->
                                selectionMode = true
                                selectedIds = setOf(entryId)
                            },
                            selectedRecordingIds = selectedRecIds,
                            onRecordingSelectionChange = { id, sel ->
                                selectedRecIds = if (sel) selectedRecIds + id else selectedRecIds - id
                                if (selectedIds.isEmpty() && selectedRecIds.isEmpty() && selectedEventIds.isEmpty()) selectionMode = false
                            },
                            onEnterRecordingSelectionMode = { recId ->
                                selectionMode = true
                                selectedRecIds = setOf(recId)
                            },
                            selectedEventIds = selectedEventIds,
                            onEventSelectionChange = { id, sel ->
                                selectedEventIds = if (sel) selectedEventIds + id else selectedEventIds - id
                                if (selectedIds.isEmpty() && selectedRecIds.isEmpty() && selectedEventIds.isEmpty()) selectionMode = false
                            },
                            onEnterEventSelectionMode = { eventId ->
                                selectionMode = true
                                selectedEventIds = setOf(eventId)
                            }
                        )
                    }

                    if (completedTodayEntries.isNotEmpty()) {
                        item(key = "header_completed") {
                            SectionHeader(
                                "Completadas",
                                completedTodayEntries.size,
                                timelineAccentConfig.completed,
                                expanded = completedExpanded,
                                onToggle = { completedExpanded = !completedExpanded },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        if (completedExpanded) {
                            items(completedTodayEntries, key = { "completed_${it.id}" }) { entry ->
                                EntryCard(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    entry = entry,
                                    accentColor = timelineAccentConfig.completed,
                                    isProcessing = entry.id in processingEntryIds,
                                    onClick = {
                                        if (selectionMode) {
                                            val sel = entry.id !in selectedIds
                                            selectedIds = if (sel) selectedIds + entry.id else selectedIds - entry.id
                                            if (selectedIds.isEmpty() && selectedRecIds.isEmpty() && selectedEventIds.isEmpty()) selectionMode = false
                                        } else {
                                            onEntryClick(entry.id)
                                        }
                                    },
                                    onLongClick = if (!selectionMode) {
                                        { selectionMode = true; selectedIds = setOf(entry.id) }
                                    } else null,
                                    isSelectionMode = selectionMode,
                                    isSelected = entry.id in selectedIds
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}

// ── Entry Card Item wrapper ──────────────────────────────────────────────────

@Composable
private fun EntryCardItem(
    entry: DiaryEntry,
    accentColor: Color,
    selectionMode: Boolean,
    selectedIds: Set<Long>,
    onEntryClick: (Long) -> Unit,
    isProcessing: Boolean,
    onToggleComplete: () -> Unit,
    onPostpone: (Long, String) -> Unit,
    onSelectionChange: (Long, Boolean) -> Unit,
    onEnterSelectionMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val quickAction = remember(entry.id, entry.actionType, entry.displayText, entry.dueDate, entry.status) {
        EntryActionBridge.build(entry)
    }
    var editingCalendarAction by remember(entry.id) { mutableStateOf<com.trama.app.summary.SuggestedAction?>(null) }
    var pendingCalendarAction by remember(entry.id) { mutableStateOf<com.trama.app.summary.SuggestedAction?>(null) }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.READ_CALENDAR] == true &&
            result[Manifest.permission.WRITE_CALENDAR] == true
        if (granted) {
            editingCalendarAction = pendingCalendarAction
        }
        pendingCalendarAction = null
    }

    editingCalendarAction?.let { action ->
        val isReminder = action.type == ActionType.REMINDER
        CalendarActionDialog(
            action = action,
            dialogTitle = if (isReminder) "Crear recordatorio" else "Añadir al calendario",
            confirmLabel = if (isReminder) "Crear" else "Añadir",
            onDismiss = { editingCalendarAction = null },
            onConfirm = { title, description, date, time, calendarId ->
                val datetime = "${date}T${time}"
                val updatedAction = action.copy(title = title, description = description, datetime = datetime)
                val startMillis = try {
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()).parse(datetime)?.time
                } catch (_: Exception) { null }

                if (startMillis != null && calendarId != null) {
                    CalendarHelper.insertEventInCalendar(
                        context = context,
                        calendarId = calendarId,
                        title = title,
                        description = description.ifBlank { null },
                        startMillis = startMillis,
                        reminderMinutes = if (isReminder) 15 else 0
                    )
                } else {
                    CalendarHelper.insertEventFromAction(context, updatedAction, isReminder = isReminder)
                }
                editingCalendarAction = null
            }
        )
    }

    SwipeableReminderCard(
        entry = entry,
        enabled = entry.status == EntryStatus.PENDING && !selectionMode,
        onMarkDone = onToggleComplete,
        onPostponeSelected = onPostpone
    ) {
        EntryCard(
            modifier = modifier,
            entry = entry,
            accentColor = accentColor,
            quickActionLabel = quickAction?.label,
            quickActionIcon = quickAction?.icon,
            isSelectionMode = selectionMode,
            isSelected = entry.id in selectedIds,
            isProcessing = isProcessing,
            onToggleComplete = onToggleComplete,
            onQuickActionClick = quickAction?.let { action ->
                {
                    if (action.action.type == ActionType.CALENDAR_EVENT || action.action.type == ActionType.REMINDER) {
                        if (CalendarHelper.hasWriteCalendarPermission(context)) {
                            editingCalendarAction = action.action
                        } else {
                            pendingCalendarAction = action.action
                            calendarPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_CALENDAR,
                                    Manifest.permission.WRITE_CALENDAR
                                )
                            )
                        }
                    } else {
                        ActionExecutor.execute(context, action.action)
                    }
                }
            },
            onClick = {
                if (selectionMode) onSelectionChange(entry.id, entry.id !in selectedIds)
                else onEntryClick(entry.id)
            },
            onLongClick = { if (!selectionMode) onEnterSelectionMode() }
        )
    }
}

// ── Section Header ───────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 2.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colored dot accent
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(color)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = color.copy(alpha = 0.85f),
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
        )
    }
}

// ── Duplicate Card ──────────────────────────────────────────────────────────

@Composable
private fun StatusOverviewRow(
    serviceRunning: Boolean,
    isRecording: Boolean,
    watchActive: Boolean,
    locationRunning: Boolean,
    onLocationToggle: () -> Unit,
    onMicroToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabledStatusColor = Color(0xFF2F7D4A)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusIconChip(
                active = locationRunning,
                activeColor = enabledStatusColor,
                icon = Icons.Default.Place,
                contentDescription = if (locationRunning) "Desactivar ubicación" else "Activar ubicación",
                onClick = onLocationToggle
            )
            StatusIconChip(
                active = isRecording || watchActive || serviceRunning,
                activeColor = enabledStatusColor,
                icon = when {
                    isRecording -> Icons.Default.Mic
                    watchActive -> Icons.Default.Watch
                    serviceRunning -> Icons.Default.Mic
                    else -> Icons.Default.MicOff
                },
                contentDescription = when {
                    isRecording -> "Desactivar micro"
                    watchActive -> "Desactivar micro"
                    serviceRunning -> "Desactivar micro"
                    else -> "Activar micro"
                },
                onClick = onMicroToggle
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MicControlFabGroup(
    serviceRunning: Boolean,
    isRecording: Boolean,
    watchActive: Boolean,
    quickActionsVisible: Boolean,
    onQuickActionsVisibleChange: (Boolean) -> Unit,
    onPrimaryClick: () -> Unit,
    onStartRecording: () -> Unit,
    onTransferToWatch: () -> Unit
) {
    val activeFabContainer = Color(0xFFDDF3E3)
    val activeFabContent = Color(0xFF2F7D4A)
    val recordingFabContainer = Color(0xFFD32F2F)
    val recordingFabContent = Color.White
    val inactiveFabContainer = MaterialTheme.colorScheme.surfaceVariant
    val inactiveFabContent = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AnimatedVisibility(
            visible = quickActionsVisible && !isRecording && !watchActive,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniFabIcon(
                    icon = Icons.Default.Watch,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.95f),
                    contentColor = MaterialTheme.colorScheme.tertiary,
                    contentDescription = "Transferir al reloj",
                    onClick = onTransferToWatch
                )
                MiniFabIcon(
                    icon = Icons.Default.FiberManualRecord,
                    containerColor = Color(0xFFFDE7E9),
                    contentColor = Color(0xFFD32F2F),
                    contentDescription = "Grabar continuamente",
                    onClick = onStartRecording
                )
            }
        }

        val fabInteractionSource = remember { MutableInteractionSource() }
        Surface(
            modifier = Modifier.combinedClickable(
                interactionSource = fabInteractionSource,
                indication = null,
                onClick = onPrimaryClick,
                onLongClick = {
                    if (!isRecording && !watchActive) {
                        onQuickActionsVisibleChange(!quickActionsVisible)
                    }
                }
            ),
            shape = CircleShape,
            color = when {
                isRecording -> recordingFabContainer
                watchActive || serviceRunning -> activeFabContainer
                else -> inactiveFabContainer
            },
            shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier.size(58.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isRecording -> Icons.Default.Stop
                        watchActive -> Icons.Default.Watch
                        serviceRunning -> Icons.Default.Mic
                        else -> Icons.Default.MicOff
                    },
                    contentDescription = when {
                        isRecording -> "Parar grabación"
                        watchActive -> "Recuperar control del reloj"
                        serviceRunning -> "Desactivar escucha"
                        else -> "Activar escucha"
                    },
                    modifier = Modifier.size(26.dp),
                    tint = when {
                        isRecording -> recordingFabContent
                        watchActive || serviceRunning -> activeFabContent
                        else -> inactiveFabContent
                    }
                )
            }
        }
    }
}

@Composable
private fun MiniFabIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = contentColor
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StatusIconChip(
    active: Boolean,
    activeColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val inactiveContainer = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    val inactiveBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val inactiveIcon = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    val activeContainer = activeColor.copy(alpha = 0.2f)
    val activeBorder = activeColor.copy(alpha = 0.85f)
    val chipInteractionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = if (onClick != null) {
            Modifier.combinedClickable(
                interactionSource = chipInteractionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
        } else {
            Modifier
        },
        shape = CircleShape,
        color = if (active) activeContainer else inactiveContainer,
        border = androidx.compose.foundation.BorderStroke(
            if (active) 1.5.dp else 1.dp,
            if (active) activeBorder else inactiveBorder
        )
    ) {
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(17.dp),
                tint = if (active) activeColor else inactiveIcon
            )
        }
    }
}

@Composable
private fun DuplicateCard(
    entry: DiaryEntry,
    originalText: String?,
    onKeep: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = entry.displayText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (originalText != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Similar a: $originalText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onKeep) {
                    Text("No es duplicado")
                }
            }
        }
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
        }
    }
}

// ── Add Entry Dialog ─────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddEntryDialog(
    onDismiss: () -> Unit,
    onSave: (text: String, categoryId: String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("nota") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva tarea") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Descripcion") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp)
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MANUAL_CATEGORIES.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat.id,
                            onClick = { selectedCategory = cat.id },
                            label = { Text("${cat.emoji} ${cat.label}", style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(text.trim(), selectedCategory) },
                enabled = text.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
        shape = RoundedCornerShape(24.dp)
    )
}
