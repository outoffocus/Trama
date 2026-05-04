package com.trama.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import com.trama.app.service.EntryProcessingState
import com.trama.app.service.RecordingState
import com.trama.app.service.ServiceController
import com.trama.app.summary.CalendarHelper
import com.trama.app.summary.GoogleCalendarSyncManager
import com.trama.app.ui.SettingsDataStore
import com.trama.app.ui.components.EntryCard
import com.trama.app.ui.components.StatusPill
import com.trama.app.ui.components.SwipeableReminderCard
import com.trama.app.ui.components.TramaStatus
import com.trama.app.ui.theme.LocalTramaColors
import com.trama.app.ui.theme.TimelineAccentConfig
import com.trama.app.ui.theme.timelineAccentColor
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryActionType
import com.trama.shared.model.EntryStatus
import com.trama.shared.model.Place
import com.trama.shared.model.Recording
import com.trama.shared.model.DailyPage
import com.trama.shared.model.Source
import com.trama.shared.model.TimelineEventType
import com.trama.shared.sync.MicCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CalendarScreen(
    initialSelectedDayStart: Long? = null,
    onEntryClick: (Long) -> Unit,
    onRecordingClick: (Long) -> Unit = {},
    onPlaceClick: (Long) -> Unit = {},
    onChatClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { DatabaseProvider.getRepository(context) }
    val settings = remember { SettingsDataStore(context) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        GoogleCalendarSyncManager(context).syncSelectedCalendars()
    }

    val today = remember { Calendar.getInstance() }

    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val initialDayStart = remember(initialSelectedDayStart, todayStart) {
        initialSelectedDayStart ?: todayStart
    }

    var selectedDayStart by remember { mutableStateOf(initialDayStart) }
    var displayMonth by remember {
        mutableStateOf(
            Calendar.getInstance().apply {
                timeInMillis = initialDayStart
                set(Calendar.DAY_OF_MONTH, 1)
            }
        )
    }
    var showMonthSheet by remember { mutableStateOf(false) }
    val monthSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val monthStart = remember(displayMonth) {
        (displayMonth.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val monthEnd = remember(displayMonth) {
        (displayMonth.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }
    val selectedDayEnd = remember(selectedDayStart) {
        com.trama.shared.util.DayRange.of(selectedDayStart).endInclusiveMs
    }

    // Data
    val monthEntriesState by repository.byDateRange(monthStart, monthEnd).collectAsState(initial = null)
    val monthStoredEventsState by repository.getTimelineEventsByDateRange(monthStart, monthEnd).collectAsState(initial = null)
    val placesState by repository.getPlaces().collectAsState(initial = null)
    val selectedDayEventsState by repository.getTimelineEventsByDateRange(selectedDayStart, selectedDayEnd).collectAsState(initial = null)
    val selectedDailyPageState by repository.getDailyPage(selectedDayStart).collectAsState(initial = null)
    val pendingOnDayState by repository.getPendingForDay(selectedDayStart, selectedDayEnd).collectAsState(initial = null)
    val pendingFromOtherDaysState by repository.getPendingFromOtherDays(selectedDayStart, selectedDayEnd).collectAsState(initial = null)
    val duplicateEntriesState by repository.getDuplicates().collectAsState(initial = null)
    val allPendingState by repository.getPending().collectAsState(initial = null)
    val completedOnDayState by repository.getCompletedByCompletedAt(selectedDayStart, selectedDayEnd).collectAsState(initial = null)
    val recordingsState by repository.getAllRecordings().collectAsState(initial = null)
    val processingEntryIds by EntryProcessingState.processingIds.collectAsState()
    val processingBackends by EntryProcessingState.processingBackends.collectAsState()
    val serviceRunning by ServiceController.isRunning.collectAsState()
    val isRecording by RecordingState.isRecording.collectAsState()
    val recordingElapsed by RecordingState.elapsedSeconds.collectAsState()
    val watchActive by ServiceController.isWatchActive.collectAsState()
    val locationRunning by ServiceController.isLocationRunning.collectAsState()
    val showListeningStatusOnHome by settings.listeningStatusOnHome.collectAsState(initial = false)
    val asrStatus by settings.asrDebugStatus.collectAsState(initial = "sin datos")
    val watchStatus by settings.watchDebugStatus.collectAsState(initial = "")
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

    val monthEntries = monthEntriesState ?: emptyList()
    val monthStoredEvents = monthStoredEventsState ?: emptyList()
    val selectedDayEvents = selectedDayEventsState ?: emptyList()
    val selectedDailyPage = selectedDailyPageState
    val pendingOnDay = pendingOnDayState ?: emptyList()
    val pendingFromOtherDays = pendingFromOtherDaysState ?: emptyList()
    val duplicateEntries = duplicateEntriesState ?: emptyList()
    val duplicateIds = remember(duplicateEntries) { duplicateEntries.map { it.id }.toSet() }
    val allPendingForOriginalLookup = allPendingState ?: emptyList()
    val visiblePendingOnDay = remember(pendingOnDay, pendingFromOtherDays, duplicateIds) {
        (pendingOnDay + pendingFromOtherDays)
            .distinctBy { it.id }
            .filter { it.id !in duplicateIds }
    }
    val acceptedPendingOnDay = visiblePendingOnDay
    val completedTasks = completedOnDayState ?: emptyList()
    val activeSelectedDayEvents = remember(selectedDayEvents) {
        selectedDayEvents.filter { it.type != TimelineEventType.CALENDAR || it.completedAt == null }
    }
    val completedCalendarEvents = remember(selectedDayEvents) {
        selectedDayEvents.filter { it.type == TimelineEventType.CALENDAR && it.completedAt != null }
    }
    val dayRecordings = recordingsState
        ?.filter { it.createdAt in selectedDayStart..selectedDayEnd }
        ?.sortedBy { it.createdAt }
        ?: emptyList()
    val entriesCreatedOnDay = remember(monthEntries, selectedDayStart, selectedDayEnd) {
        monthEntries
            .filter { it.createdAt in selectedDayStart..selectedDayEnd }
            .sortedBy { it.createdAt }
    }
    val pendingOtherDays = remember(acceptedPendingOnDay, selectedDayStart) {
        acceptedPendingOnDay.filter { it.createdAt < selectedDayStart }
    }
    val todayEnd = remember(todayStart) { com.trama.shared.util.DayRange.of(todayStart).endInclusiveMs }
    val endOfThisWeek = remember(todayStart) {
        val cal = Calendar.getInstance().apply { timeInMillis = todayStart }
        // Week ends on Sunday (locale-friendly: roll forward until SUNDAY).
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        cal.timeInMillis
    }
    val endOfNextWeek = remember(endOfThisWeek) { endOfThisWeek + 7L * 24 * 3600 * 1000 }
    val upcomingThisWeek = remember(allPendingForOriginalLookup, todayStart, endOfThisWeek, duplicateIds) {
        allPendingForOriginalLookup.filter {
            it.id !in duplicateIds && (it.dueDate ?: Long.MIN_VALUE) in todayStart..endOfThisWeek
        }.sortedBy { it.dueDate ?: 0L }
    }
    val upcomingNextWeek = remember(allPendingForOriginalLookup, endOfThisWeek, endOfNextWeek, duplicateIds) {
        allPendingForOriginalLookup.filter {
            it.id !in duplicateIds && (it.dueDate ?: Long.MIN_VALUE) in (endOfThisWeek + 1)..endOfNextWeek
        }.sortedBy { it.dueDate ?: 0L }
    }
    val upcomingLater = remember(allPendingForOriginalLookup, endOfNextWeek, duplicateIds) {
        allPendingForOriginalLookup.filter {
            it.id !in duplicateIds && (it.dueDate ?: Long.MIN_VALUE) > endOfNextWeek
        }.sortedBy { it.dueDate ?: 0L }
    }
    val upcomingTotal = upcomingThisWeek.size + upcomingNextWeek.size + upcomingLater.size
    val todayTimelineEvents = remember(
        entriesCreatedOnDay,
        dayRecordings,
        activeSelectedDayEvents,
        duplicateIds
    ) {
        buildTimelineEvents(
            createdEntries = entriesCreatedOnDay.filter { it.id !in duplicateIds },
            completedEntries = emptyList(),
            recordings = dayRecordings,
            storedEvents = activeSelectedDayEvents
        )
    }
    val completedTimelineEvents = remember(completedTasks, completedCalendarEvents) {
        buildTimelineEvents(
            createdEntries = emptyList(),
            completedEntries = completedTasks,
            recordings = emptyList(),
            storedEvents = completedCalendarEvents
        )
    }
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
    val hourFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val isLoading = monthEntriesState == null ||
        monthStoredEventsState == null ||
        placesState == null ||
        selectedDayEventsState == null ||
        pendingOnDayState == null ||
        pendingFromOtherDaysState == null ||
        completedOnDayState == null ||
        recordingsState == null ||
        duplicateEntriesState == null

    // Month grid dot data
    val entriesByDay = remember(monthEntries) {
        monthEntries.groupBy {
            Calendar.getInstance().apply { timeInMillis = it.createdAt }.get(Calendar.DAY_OF_MONTH)
        }
    }
    val eventEntriesByDay = remember(monthStoredEvents) {
        monthStoredEvents.groupBy {
            Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.DAY_OF_MONTH)
        }
    }
    val completedByDay = remember(monthEntries) {
        monthEntries
            .filter { it.status == EntryStatus.COMPLETED }
            .groupBy {
                val cal = Calendar.getInstance()
                cal.timeInMillis = it.completedAt ?: it.createdAt
                cal.get(Calendar.DAY_OF_MONTH)
            }
    }

    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale("es")) }
    val t = LocalTramaColors.current
    val selectedDayLabel = remember(selectedDayStart) {
        SimpleDateFormat("EEEE d 'de' MMMM", Locale("es")).format(selectedDayStart)
            .replaceFirstChar { it.uppercase() }
    }

    val isSelectedToday = selectedDayStart == todayStart
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) ServiceController.start(context)
    }
    val recordPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) ServiceController.startRecording(context)
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var micActionsVisible by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedEntryIds by remember { mutableStateOf(setOf<Long>()) }
    var selectedRecordingIds by remember { mutableStateOf(setOf<Long>()) }
    var selectedEventIds by remember { mutableStateOf(setOf<Long>()) }
    var otherDaysExpanded by remember { mutableStateOf(true) }
    var todayExpanded by remember { mutableStateOf(true) }
    var completedExpanded by remember { mutableStateOf(true) }
    var upcomingExpanded by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    fun exitSelectionMode() {
        selectionMode = false
        selectedEntryIds = emptySet()
        selectedRecordingIds = emptySet()
        selectedEventIds = emptySet()
    }

    BackHandler(enabled = selectionMode) {
        exitSelectionMode()
    }

    BackHandler(enabled = !selectionMode && micActionsVisible) {
        micActionsVisible = false
    }

    fun deleteSelected() {
        val entryIds = selectedEntryIds.toList()
        val recordingIds = selectedRecordingIds.toList()
        val eventIds = selectedEventIds.toList()
        if (entryIds.isEmpty() && recordingIds.isEmpty() && eventIds.isEmpty()) return

        scope.launch {
            if (entryIds.isNotEmpty()) {
                entryIds.forEach { id ->
                    repository.getByIdOnce(id)?.let { e ->
                        com.trama.app.diagnostics.CaptureLog.logUserDelete(
                            entryId = e.id,
                            text = e.displayText.ifBlank { e.text },
                            createdAtMs = e.createdAt,
                            status = e.status,
                            actionType = e.actionType,
                            isManual = e.isManual,
                            wasCompleted = e.completedAt != null,
                            hadDueDate = e.dueDate != null,
                            source = "selection_bulk",
                            extra = mapOf("batchSize" to entryIds.size)
                        )
                    }
                }
                repository.deleteByIds(entryIds)
            }
            if (recordingIds.isNotEmpty()) repository.deleteRecordingsByIds(recordingIds)
            if (eventIds.isNotEmpty()) repository.deleteTimelineEventsByIds(eventIds)
            exitSelectionMode()
        }
    }

    fun toggleEntrySelection(id: Long, selected: Boolean) {
        selectedEntryIds = if (selected) selectedEntryIds + id else selectedEntryIds - id
        if (selectedEntryIds.isEmpty() && selectedRecordingIds.isEmpty() && selectedEventIds.isEmpty()) {
            selectionMode = false
        }
    }

    fun enterEntrySelection(id: Long) {
        selectionMode = true
        selectedEntryIds = setOf(id)
    }

    val snackbarHostState = remember { SnackbarHostState() }

    fun markEntryCompleted(entry: DiaryEntry) {
        scope.launch {
            repository.markCompleted(entry.id)
            val result = snackbarHostState.showSnackbar(
                message = "Marcada como hecha",
                actionLabel = "Deshacer",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                repository.markPending(entry.id)
            }
        }
    }

    fun reopenEntry(entry: DiaryEntry) {
        scope.launch {
            repository.markPending(entry.id)
        }
    }

    fun keepDuplicate(entry: DiaryEntry) {
        scope.launch { repository.clearDuplicate(entry.id) }
    }

    fun deleteDuplicate(entry: DiaryEntry) {
        scope.launch {
            com.trama.app.diagnostics.CaptureLog.logUserDelete(
                entryId = entry.id,
                text = entry.displayText.ifBlank { entry.text },
                createdAtMs = entry.createdAt,
                status = entry.status,
                actionType = entry.actionType,
                isManual = entry.isManual,
                wasCompleted = entry.completedAt != null,
                hadDueDate = entry.dueDate != null,
                source = "duplicate_card",
                extra = mapOf("duplicateOfId" to (entry.duplicateOfId ?: -1L))
            )
            repository.deleteById(entry.id)
        }
    }

    fun postponeEntry(entry: DiaryEntry, dueDate: Long) {
        scope.launch {
            val previousDue = entry.dueDate
            repository.updateDueDate(entry.id, dueDate)
            val result = snackbarHostState.showSnackbar(
                message = "Pospuesta",
                actionLabel = "Deshacer",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                repository.updateDueDate(entry.id, previousDue)
            }
        }
    }

    fun toggleCalendarEventCompleted(eventId: Long, completed: Boolean) {
        scope.launch {
            if (completed) {
                repository.markTimelineEventCompleted(eventId)
            } else {
                repository.markTimelineEventPending(eventId)
            }
        }
    }

    fun navigateDay(offset: Int) {
        exitSelectionMode()
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDayStart }
        cal.add(Calendar.DAY_OF_YEAR, offset)
        val newStart = cal.apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        selectedDayStart = newStart
        if (cal.get(Calendar.MONTH) != displayMonth.get(Calendar.MONTH) ||
            cal.get(Calendar.YEAR) != displayMonth.get(Calendar.YEAR)
        ) {
            displayMonth = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        }
    }

    fun selectDay(ms: Long, closeMonthSheet: Boolean = false) {
        exitSelectionMode()
        selectedDayStart = ms
        val newCal = Calendar.getInstance().apply { timeInMillis = ms }
        if (newCal.get(Calendar.MONTH) != displayMonth.get(Calendar.MONTH) ||
            newCal.get(Calendar.YEAR) != displayMonth.get(Calendar.YEAR)
        ) {
            displayMonth = (newCal.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1)
            }
        }
        if (closeMonthSheet) showMonthSheet = false
    }

    fun goToToday(closeMonthSheet: Boolean = false) {
        selectDay(todayStart, closeMonthSheet = closeMonthSheet)
    }

    fun navigateDisplayMonth(offset: Int) {
        displayMonth = (displayMonth.clone() as Calendar).apply {
            add(Calendar.MONTH, offset)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }

    fun handleMicClick() {
        when {
            isRecording -> RecordingState.stopRecording(context)
            watchActive -> {
                micActionsVisible = false
                scope.launch(Dispatchers.IO) { MicCoordinator.sendPause(context) }
                ServiceController.notifyWatchInactive()
            }
            serviceRunning -> ServiceController.stop(context, reason = "calendar_floating_stop")
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> ServiceController.start(context)
            else -> micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun startContinuousRecording() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        micActionsVisible = false
        ServiceController.startRecording(context)
    }

    fun transferListeningToWatch() {
        micActionsVisible = false
        ServiceController.transferToWatch(context)
    }

    if (showAddDialog) {
        CategorizedManualEntryDialog(
            onDismiss = { showAddDialog = false },
            onSave = { text, categoryId, categoryLabel ->
                scope.launch {
                    repository.insert(
                        DiaryEntry(
                            text = text,
                            keyword = categoryId,
                            category = categoryLabel,
                            confidence = 1.0f,
                            source = Source.PHONE,
                            duration = 0,
                            isManual = true,
                            cleanText = text,
                            createdAt = if (isSelectedToday) System.currentTimeMillis() else selectedDayStart + 12 * 60 * 60 * 1000
                        )
                    )
                    showAddDialog = false
                }
            }
        )
    }

    if (showMonthSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMonthSheet = false },
            sheetState = monthSheetState,
            containerColor = t.surface
        ) {
            MonthPickerSheet(
                selectedDayStart = selectedDayStart,
                todayStart = todayStart,
                entriesByDay = entriesByDay,
                eventEntriesByDay = eventEntriesByDay,
                completedByDay = completedByDay,
                displayMonth = displayMonth,
                monthLabel = monthFormat.format(displayMonth.time).replaceFirstChar { it.uppercase() },
                onPreviousMonth = { navigateDisplayMonth(-1) },
                onNextMonth = { navigateDisplayMonth(1) },
                onToday = { goToToday(closeMonthSheet = true) },
                onDaySelected = { ms -> selectDay(ms, closeMonthSheet = true) }
            )
        }
    }

    Scaffold(
        topBar = {
            val totalSelected = selectedEntryIds.size + selectedRecordingIds.size + selectedEventIds.size
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
                        IconButton(onClick = { deleteSelected() }, enabled = totalSelected > 0) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Borrar",
                                tint = if (totalSelected > 0) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else {
                val heroDayTitle = remember(selectedDayStart, isSelectedToday) {
                    if (isSelectedToday) {
                        SimpleDateFormat("EEEE d 'de' MMMM", Locale("es"))
                            .format(Date(selectedDayStart))
                            .replaceFirstChar { it.uppercase() }
                    } else {
                        selectedDayLabel
                    }
                }
                val headerStatus = when {
                    isRecording -> TramaStatus.Recording
                    watchActive -> TramaStatus.Watch
                    showListeningStatusOnHome && serviceRunning && asrStatus.isListeningErrorStatus() ->
                        TramaStatus.Error
                    serviceRunning -> TramaStatus.Listening
                    else -> TramaStatus.Idle
                }
                val headerStatusLabel = when {
                    isRecording -> formatRecordingElapsed(recordingElapsed)
                    showListeningStatusOnHome -> listeningStatusLabel(
                        isRecording = isRecording,
                        watchActive = watchActive,
                        serviceRunning = serviceRunning,
                        asrStatus = asrStatus,
                        watchStatus = watchStatus
                    )
                    else -> null
                }
                HomeHeader(
                    heroDayTitle = heroDayTitle,
                    status = headerStatus,
                    statusLabel = headerStatusLabel,
                    locationRunning = locationRunning,
                    onAddClick = { showAddDialog = true },
                    onChatClick = onChatClick,
                    onSettingsClick = onSettingsClick,
                )
            }
        },
        bottomBar = {
            if (!selectionMode) {
                UnifiedDayBottomBar(
                    selectedDayStart = selectedDayStart,
                    todayStart = todayStart,
                    entriesByDay = entriesByDay,
                    eventEntriesByDay = eventEntriesByDay,
                    completedByDay = completedByDay,
                    displayMonth = displayMonth,
                    selectedDayLabel = selectedDayLabel,
                    monthLabel = monthFormat.format(Date(selectedDayStart)).replaceFirstChar { it.uppercase() },
                    upcomingThisWeekCount = upcomingThisWeek.size,
                    onUpcomingPeekClick = {
                        upcomingExpanded = true
                        scope.launch { listState.animateScrollToItem(Int.MAX_VALUE) }
                    },
                    onNavigateDay = { offset -> navigateDay(offset) },
                    onOpenMonthPicker = { showMonthSheet = true },
                    onToday = { goToToday() },
                    onDaySelected = { ms -> selectDay(ms) },
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingMicButton(
                    serviceRunning = serviceRunning,
                    isRecording = isRecording,
                    watchActive = watchActive,
                    actionsVisible = micActionsVisible,
                    onActionsVisibleChange = { micActionsVisible = it },
                    onClick = { handleMicClick() },
                    onStartRecording = { startContinuousRecording() },
                    onTransferToWatch = { transferListeningToWatch() }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Content ───────────────────────────────────────────────────
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 64.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (duplicateEntries.isNotEmpty()) {
                        item("duplicates_header") {
                            CollapsibleSectionHeader(
                                title = "Posibles duplicados",
                                count = duplicateEntries.size,
                                expanded = true,
                                onClick = {}
                            )
                        }
                        items(duplicateEntries, key = { "dup_${it.id}" }) { entry ->
                            val originalEntry = allPendingForOriginalLookup.find { it.id == entry.duplicateOfId }
                            DuplicateCard(
                                entry = entry,
                                originalText = originalEntry?.displayText,
                                onKeep = { keepDuplicate(entry) },
                                onDelete = { deleteDuplicate(entry) }
                            )
                        }
                    }
                    item("other_days_header") {
                        CollapsibleSectionHeader(
                            title = "Pendiente otros días",
                            count = pendingOtherDays.size,
                            expanded = otherDaysExpanded,
                            onClick = { otherDaysExpanded = !otherDaysExpanded }
                        )
                    }
                    if (otherDaysExpanded) {
                        if (pendingOtherDays.isEmpty()) {
                            item("other_days_empty") {
                                CalendarEmptyHint("No hay pendientes arrastradas.")
                            }
                        }
                        pendingEntrySection(
                            keyPrefix = "pending_other_days_",
                            entries = pendingOtherDays,
                            selectedEntryIds = selectedEntryIds,
                            selectionMode = selectionMode,
                            processingEntryIds = processingEntryIds,
                            processingBackends = processingBackends,
                            accentColor = timelineAccentConfig.pending,
                            onEntryClick = onEntryClick,
                            onToggleSelection = { id, selected -> toggleEntrySelection(id, selected) },
                            onEnterSelection = { id -> enterEntrySelection(id) },
                            onComplete = { entry -> markEntryCompleted(entry) },
                            onPostpone = { entry, dueDate -> postponeEntry(entry, dueDate) }
                        )
                    }
                    item("today_header") {
                        CollapsibleSectionHeader(
                            title = "Hoy",
                            count = todayTimelineEvents.size,
                            expanded = todayExpanded,
                            onClick = { todayExpanded = !todayExpanded }
                        )
                    }
                    if (todayExpanded) {
                        if (todayTimelineEvents.isEmpty()) {
                            item("today_empty") {
                                CalendarEmptyHint("Sin actividad registrada hoy.")
                            }
                        } else {
                            timelineListContent(
                                events = todayTimelineEvents,
                                processingEntryIds = processingEntryIds,
                                processingBackends = processingBackends,
                                hourFormat = hourFormat,
                                accentConfig = timelineAccentConfig,
                                itemModifier = Modifier,
                                keyPrefix = "calendar_today_",
                                onEntryClick = onEntryClick,
                                onRecordingClick = onRecordingClick,
                                onPlaceClick = onPlaceClick,
                                onToggleComplete = if (!selectionMode) { entry -> markEntryCompleted(entry) } else null,
                                onPostponeEntry = if (!selectionMode) { entry, dueDate, _ -> postponeEntry(entry, dueDate) } else null,
                                onToggleCalendarComplete = if (!selectionMode) {
                                    { event -> toggleCalendarEventCompleted(event.id, completed = true) }
                                } else null,
                                isSelectionMode = selectionMode,
                                selectedEntryIds = selectedEntryIds,
                                onEntrySelectionChange = { id, selected -> toggleEntrySelection(id, selected) },
                                onEnterEntrySelectionMode = { id -> enterEntrySelection(id) },
                                selectedRecordingIds = selectedRecordingIds,
                                onRecordingSelectionChange = { id, selected ->
                                    selectedRecordingIds = if (selected) selectedRecordingIds + id else selectedRecordingIds - id
                                    if (selectedEntryIds.isEmpty() && selectedRecordingIds.isEmpty() && selectedEventIds.isEmpty()) {
                                        selectionMode = false
                                    }
                                },
                                onEnterRecordingSelectionMode = { id ->
                                    selectionMode = true
                                    selectedRecordingIds = setOf(id)
                                },
                                selectedEventIds = selectedEventIds,
                                onEventSelectionChange = { id, selected ->
                                    selectedEventIds = if (selected) selectedEventIds + id else selectedEventIds - id
                                    if (selectedEntryIds.isEmpty() && selectedRecordingIds.isEmpty() && selectedEventIds.isEmpty()) {
                                        selectionMode = false
                                    }
                                },
                                onEnterEventSelectionMode = { id ->
                                    selectionMode = true
                                    selectedEventIds = setOf(id)
                                }
                            )
                        }
                    }
                    item("completed_header") {
                        CollapsibleSectionHeader(
                            title = "Completado hoy",
                            count = completedTasks.size + completedCalendarEvents.size,
                            expanded = completedExpanded,
                            onClick = { completedExpanded = !completedExpanded }
                        )
                    }
                    if (completedExpanded) {
                        if (completedTimelineEvents.isEmpty()) {
                            item("completed_empty") {
                                CalendarEmptyHint("Todavía no has cerrado tareas este día.")
                            }
                        } else {
                            timelineListContent(
                                events = completedTimelineEvents,
                                processingEntryIds = processingEntryIds,
                                processingBackends = processingBackends,
                                hourFormat = hourFormat,
                                accentConfig = timelineAccentConfig,
                                itemModifier = Modifier,
                                keyPrefix = "calendar_completed_",
                                onEntryClick = onEntryClick,
                                onRecordingClick = onRecordingClick,
                                onPlaceClick = onPlaceClick,
                                onToggleComplete = null,
                                onReopenEntry = if (!selectionMode) { entry -> reopenEntry(entry) } else null,
                                onToggleCalendarComplete = if (!selectionMode) {
                                    { event -> toggleCalendarEventCompleted(event.id, completed = false) }
                                } else null,
                                isSelectionMode = selectionMode,
                                selectedEntryIds = selectedEntryIds,
                                onEntrySelectionChange = { id, selected -> toggleEntrySelection(id, selected) },
                                onEnterEntrySelectionMode = { id -> enterEntrySelection(id) },
                                selectedRecordingIds = selectedRecordingIds,
                                onRecordingSelectionChange = { id, selected ->
                                    selectedRecordingIds = if (selected) selectedRecordingIds + id else selectedRecordingIds - id
                                    if (selectedEntryIds.isEmpty() && selectedRecordingIds.isEmpty() && selectedEventIds.isEmpty()) {
                                        selectionMode = false
                                    }
                                },
                                onEnterRecordingSelectionMode = { id ->
                                    selectionMode = true
                                    selectedRecordingIds = setOf(id)
                                },
                                selectedEventIds = selectedEventIds,
                                onEventSelectionChange = { id, selected ->
                                    selectedEventIds = if (selected) selectedEventIds + id else selectedEventIds - id
                                    if (selectedEntryIds.isEmpty() && selectedRecordingIds.isEmpty() && selectedEventIds.isEmpty()) {
                                        selectionMode = false
                                    }
                                },
                                onEnterEventSelectionMode = { id ->
                                    selectionMode = true
                                    selectedEventIds = setOf(id)
                                }
                            )
                        }
                    }

                    if (upcomingTotal > 0) {
                        item("upcoming_header") {
                            CollapsibleSectionHeader(
                                title = "Próximamente",
                                count = upcomingTotal,
                                expanded = upcomingExpanded,
                                onClick = { upcomingExpanded = !upcomingExpanded }
                            )
                        }
                        if (upcomingExpanded) {
                            upcomingGroup(
                                keyPrefix = "upcoming_thisweek_",
                                title = "Esta semana",
                                entries = upcomingThisWeek,
                                accentColor = timelineAccentConfig.pending,
                                selectionMode = selectionMode,
                                selectedEntryIds = selectedEntryIds,
                                processingEntryIds = processingEntryIds,
                                processingBackends = processingBackends,
                                onEntryClick = onEntryClick,
                                onToggleSelection = { id, sel -> toggleEntrySelection(id, sel) },
                                onEnterSelection = { id -> enterEntrySelection(id) },
                                onComplete = { entry -> markEntryCompleted(entry) },
                                onPostpone = { entry, due -> postponeEntry(entry, due) }
                            )
                            upcomingGroup(
                                keyPrefix = "upcoming_nextweek_",
                                title = "Próxima semana",
                                entries = upcomingNextWeek,
                                accentColor = timelineAccentConfig.pending,
                                selectionMode = selectionMode,
                                selectedEntryIds = selectedEntryIds,
                                processingEntryIds = processingEntryIds,
                                processingBackends = processingBackends,
                                onEntryClick = onEntryClick,
                                onToggleSelection = { id, sel -> toggleEntrySelection(id, sel) },
                                onEnterSelection = { id -> enterEntrySelection(id) },
                                onComplete = { entry -> markEntryCompleted(entry) },
                                onPostpone = { entry, due -> postponeEntry(entry, due) }
                            )
                            upcomingGroup(
                                keyPrefix = "upcoming_later_",
                                title = "Más adelante",
                                entries = upcomingLater,
                                accentColor = timelineAccentConfig.pending,
                                selectionMode = selectionMode,
                                selectedEntryIds = selectedEntryIds,
                                processingEntryIds = processingEntryIds,
                                processingBackends = processingBackends,
                                onEntryClick = onEntryClick,
                                onToggleSelection = { id, sel -> toggleEntrySelection(id, sel) },
                                onEnterSelection = { id -> enterEntrySelection(id) },
                                onComplete = { entry -> markEntryCompleted(entry) },
                                onPostpone = { entry, due -> postponeEntry(entry, due) }
                            )
                        }
                    }

                    item("daily_summary") {
                        DailyPageSummaryCard(
                            page = if (isSelectedToday) null else selectedDailyPage,
                            isToday = isSelectedToday
                        )
                    }
                }
            }
        }
    }
}

private fun listeningStatusLabel(
    isRecording: Boolean,
    watchActive: Boolean,
    serviceRunning: Boolean,
    asrStatus: String,
    watchStatus: String
): String? {
    if (isRecording) return null
    if (watchActive) {
        return watchStatus
            .takeIf { it.isMeaningfulListeningStatus() }
            ?.toDisplayListeningStatus()
    }
    if (!serviceRunning) return null
    return asrStatus
        .takeIf { it.isMeaningfulListeningStatus() }
        ?.toDisplayListeningStatus()
}

private fun String.isMeaningfulListeningStatus(): Boolean {
    val normalized = trim()
    return normalized.isNotBlank() &&
        !normalized.equals("sin datos", ignoreCase = true) &&
        normalized != "-"
}

private fun String.isListeningErrorStatus(): Boolean {
    val normalized = lowercase(Locale.getDefault())
    return listOf(
        "error",
        "fall",
        "crash",
        "no disponible",
        "stalled",
        "bloque",
        "deneg"
    ).any { normalized.contains(it) }
}

private fun formatRecordingElapsed(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

private fun String.toDisplayListeningStatus(): String =
    trim().replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
    }

@Composable
private fun CollapsibleSectionHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val t = LocalTramaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(top = 9.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = t.mutedText
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "($count)",
            style = MaterialTheme.typography.labelSmall,
            color = t.dimText
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(t.hairline)
        )
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = t.mutedText,
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun LazyListScope.upcomingGroup(
    keyPrefix: String,
    title: String,
    entries: List<DiaryEntry>,
    accentColor: Color,
    selectionMode: Boolean,
    selectedEntryIds: Set<Long>,
    processingEntryIds: Set<Long>,
    processingBackends: Map<Long, EntryProcessingState.Backend>,
    onEntryClick: (Long) -> Unit,
    onToggleSelection: (Long, Boolean) -> Unit,
    onEnterSelection: (Long) -> Unit,
    onComplete: (DiaryEntry) -> Unit,
    onPostpone: (DiaryEntry, Long) -> Unit
) {
    if (entries.isEmpty()) return
    item(key = "${keyPrefix}sub_header") {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = LocalTramaColors.current.mutedText,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
        )
    }
    items(entries, key = { "$keyPrefix${it.id}" }) { entry ->
        PendingEntryCard(
            entry = entry,
            selectedEntryIds = selectedEntryIds,
            selectionMode = selectionMode,
            processingEntryIds = processingEntryIds,
            processingBackends = processingBackends,
            accentColor = accentColor,
            onEntryClick = onEntryClick,
            onToggleSelection = onToggleSelection,
            onEnterSelection = onEnterSelection,
            onComplete = onComplete,
            onPostpone = onPostpone
        )
    }
}

private fun LazyListScope.pendingEntrySection(
    keyPrefix: String,
    entries: List<DiaryEntry>,
    selectedEntryIds: Set<Long>,
    selectionMode: Boolean,
    processingEntryIds: Set<Long>,
    processingBackends: Map<Long, EntryProcessingState.Backend>,
    accentColor: Color,
    onEntryClick: (Long) -> Unit,
    onToggleSelection: (Long, Boolean) -> Unit,
    onEnterSelection: (Long) -> Unit,
    onComplete: (DiaryEntry) -> Unit,
    onPostpone: (DiaryEntry, Long) -> Unit
) {
    items(entries, key = { "$keyPrefix${it.id}" }) { entry ->
        PendingEntryCard(
            entry = entry,
            selectedEntryIds = selectedEntryIds,
            selectionMode = selectionMode,
            processingEntryIds = processingEntryIds,
            processingBackends = processingBackends,
            accentColor = accentColor,
            onEntryClick = onEntryClick,
            onToggleSelection = onToggleSelection,
            onEnterSelection = onEnterSelection,
            onComplete = onComplete,
            onPostpone = onPostpone
        )
    }
}


@Composable
private fun PendingEntryCard(
    entry: DiaryEntry,
    selectedEntryIds: Set<Long>,
    selectionMode: Boolean,
    processingEntryIds: Set<Long>,
    processingBackends: Map<Long, EntryProcessingState.Backend>,
    accentColor: Color,
    onEntryClick: (Long) -> Unit,
    onToggleSelection: (Long, Boolean) -> Unit,
    onEnterSelection: (Long) -> Unit,
    onComplete: (DiaryEntry) -> Unit,
    onPostpone: (DiaryEntry, Long) -> Unit
) {
    SwipeableReminderCard(
        entry = entry,
        enabled = entry.status != EntryStatus.COMPLETED &&
            entry.status != EntryStatus.DISCARDED &&
            !selectionMode,
        onMarkDone = { onComplete(entry) },
        onPostponeSelected = { dueDate, _ -> onPostpone(entry, dueDate) }
    ) {
        EntryCard(
            entry = entry,
            accentColor = accentColor,
            onClick = {
                if (selectionMode) {
                    onToggleSelection(entry.id, entry.id !in selectedEntryIds)
                } else {
                    onEntryClick(entry.id)
                }
            },
            onLongClick = if (!selectionMode) {
                { onEnterSelection(entry.id) }
            } else null,
            onToggleComplete = if (!selectionMode && entry.status == EntryStatus.PENDING) {
                { onComplete(entry) }
            } else null,
            isSelectionMode = selectionMode,
            isSelected = entry.id in selectedEntryIds,
            isProcessing = entry.id in processingEntryIds,
            processingBackend = processingBackends[entry.id]
        )
    }
}

@Composable
private fun UnifiedDayBottomBar(
    selectedDayStart: Long,
    todayStart: Long,
    entriesByDay: Map<Int, List<com.trama.shared.model.DiaryEntry>>,
    eventEntriesByDay: Map<Int, List<com.trama.shared.model.TimelineEvent>>,
    completedByDay: Map<Int, List<com.trama.shared.model.DiaryEntry>>,
    displayMonth: java.util.Calendar,
    selectedDayLabel: String,
    monthLabel: String,
    upcomingThisWeekCount: Int,
    onUpcomingPeekClick: () -> Unit,
    onNavigateDay: (Int) -> Unit,
    onOpenMonthPicker: () -> Unit,
    onToday: () -> Unit,
    onDaySelected: (Long) -> Unit,
) {
    val t = LocalTramaColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = t.surface.copy(alpha = 0.98f),
        border = BorderStroke(0.5.dp, t.hairline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = 8.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BottomDateControls(
                selectedDayLabel = selectedDayLabel,
                monthLabel = monthLabel,
                isSelectedToday = selectedDayStart == todayStart,
                onPreviousDay = { onNavigateDay(-1) },
                onNextDay = { onNavigateDay(1) },
                onOpenMonthPicker = onOpenMonthPicker,
                onToday = onToday
            )
            if (upcomingThisWeekCount > 0) {
                UpcomingPeekRow(
                    count = upcomingThisWeekCount,
                    onClick = onUpcomingPeekClick
                )
            }
            WeekStrip(
                selectedDayStart = selectedDayStart,
                todayStart = todayStart,
                entriesByDay = entriesByDay,
                eventEntriesByDay = eventEntriesByDay,
                completedByDay = completedByDay,
                displayMonth = displayMonth,
                onDaySelected = onDaySelected
            )
        }
    }
}

@Composable
private fun UpcomingPeekRow(count: Int, onClick: () -> Unit) {
    val t = LocalTramaColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = t.amberBg,
        border = BorderStroke(0.5.dp, t.amber.copy(alpha = 0.3f)),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.Event,
                contentDescription = null,
                tint = t.amber,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "$count ${if (count == 1) "vence" else "vencen"} esta semana",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = t.amber
            )
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = t.amber.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun BottomDateControls(
    selectedDayLabel: String,
    monthLabel: String,
    isSelectedToday: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onOpenMonthPicker: () -> Unit,
    onToday: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreviousDay) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Día anterior")
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable { onOpenMonthPicker() }
                .padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = selectedDayLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!isSelectedToday) {
            TextButton(onClick = onToday) {
                Text("Hoy", style = MaterialTheme.typography.labelMedium)
            }
        }
        IconButton(onClick = onNextDay) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Día siguiente")
        }
    }
}

@Composable
private fun MonthPickerSheet(
    selectedDayStart: Long,
    todayStart: Long,
    entriesByDay: Map<Int, List<com.trama.shared.model.DiaryEntry>>,
    eventEntriesByDay: Map<Int, List<com.trama.shared.model.TimelineEvent>>,
    completedByDay: Map<Int, List<com.trama.shared.model.DiaryEntry>>,
    displayMonth: java.util.Calendar,
    monthLabel: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
    onDaySelected: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousMonth) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Mes anterior")
            }
            Text(
                text = monthLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onToday) {
                Text("Hoy")
            }
            IconButton(onClick = onNextMonth) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Mes siguiente")
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("L", "M", "X", "J", "V", "S", "D").forEach { d ->
                Text(
                    text = d,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        val calDays = remember(displayMonth) { buildCalendarDays(displayMonth) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 260.dp, max = 340.dp),
            userScrollEnabled = false,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(calDays) { day ->
                val todayCal = Calendar.getInstance().apply { timeInMillis = todayStart }
                val isToday = day.isCurrentMonth &&
                    day.dayOfMonth == todayCal.get(Calendar.DAY_OF_MONTH) &&
                    displayMonth.get(Calendar.MONTH) == todayCal.get(Calendar.MONTH) &&
                    displayMonth.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)
                val isSelected = day.isCurrentMonth && run {
                    val sel = Calendar.getInstance().apply { timeInMillis = selectedDayStart }
                    day.dayOfMonth == sel.get(Calendar.DAY_OF_MONTH) &&
                        displayMonth.get(Calendar.MONTH) == sel.get(Calendar.MONTH) &&
                        displayMonth.get(Calendar.YEAR) == sel.get(Calendar.YEAR)
                }
                val hasEntries = (entriesByDay[day.dayOfMonth]?.isNotEmpty() == true) ||
                    (eventEntriesByDay[day.dayOfMonth]?.isNotEmpty() == true)
                val hasCompleted = completedByDay[day.dayOfMonth]?.isNotEmpty() == true
                val entryCount = entriesByDay[day.dayOfMonth]?.size ?: 0
                CalendarDay(
                    day = day,
                    isToday = isToday,
                    isSelected = isSelected,
                    hasEntries = hasEntries,
                    hasCompleted = hasCompleted,
                    entryCount = entryCount,
                    onClick = {
                        val clickedCal = (displayMonth.clone() as Calendar).apply {
                            set(Calendar.DAY_OF_MONTH, day.dayOfMonth)
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        }
                        onDaySelected(clickedCal.timeInMillis)
                    }
                )
            }
        }
    }
}

@Composable
private fun FloatingMicButton(
    serviceRunning: Boolean,
    isRecording: Boolean,
    watchActive: Boolean,
    actionsVisible: Boolean,
    onActionsVisibleChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onStartRecording: () -> Unit,
    onTransferToWatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalTramaColors.current
    // Speed dial grows upward. Bottom-anchored arrangement keeps the main FAB
    // pinned in place while mini actions appear above it.
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)
    ) {
        AnimatedVisibility(
            visible = actionsVisible && !isRecording && !watchActive,
            enter = androidx.compose.animation.fadeIn() +
                androidx.compose.animation.expandVertically(expandFrom = Alignment.Bottom),
            exit = androidx.compose.animation.fadeOut() +
                androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Bottom)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Top: transfer to watch
                MiniMicAction(
                    icon = Icons.Default.Watch,
                    containerColor = t.watch,
                    contentColor = Color.White,
                    contentDescription = "Transferir escucha al reloj",
                    onClick = onTransferToWatch
                )
                // Middle: continuous recording (closer to the mic)
                MiniMicAction(
                    icon = Icons.Default.FiberManualRecord,
                    containerColor = t.red,
                    contentColor = Color.White,
                    contentDescription = "Iniciar grabación",
                    onClick = onStartRecording
                )
            }
        }
        val interaction = remember { MutableInteractionSource() }
        Surface(
            modifier = Modifier.combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    if (!isRecording && !watchActive) {
                        onActionsVisibleChange(!actionsVisible)
                    }
                }
            ),
            shape = CircleShape,
            color = when {
                isRecording -> t.red
                watchActive -> t.watch
                serviceRunning -> t.amber
                else -> t.dimText
            },
            shadowElevation = 12.dp,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.background)
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
                    tint = Color.White,
                    modifier = Modifier.size(25.dp)
                )
            }
        }
    }
}

@Composable
private fun MiniMicAction(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        shadowElevation = 8.dp,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier.size(46.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private data class ManualEntryCategory(
    val id: String,
    val emoji: String,
    val label: String
)

private val MANUAL_CATEGORIES = listOf(
    ManualEntryCategory("pendiente", "📋", "Pendiente"),
    ManualEntryCategory("idea", "💡", "Idea"),
    ManualEntryCategory("compra", "🛒", "Compra"),
    ManualEntryCategory("gasto", "💰", "Gasto"),
    ManualEntryCategory("llamada", "📞", "Llamada"),
    ManualEntryCategory("nota", "📝", "Nota")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorizedManualEntryDialog(
    onDismiss: () -> Unit,
    onSave: (text: String, categoryId: String, categoryLabel: String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("nota") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva tarea") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Descripción") },
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
                onClick = {
                    val trimmed = text.trim()
                    val cat = MANUAL_CATEGORIES.find { it.id == selectedCategory } ?: MANUAL_CATEGORIES.last()
                    if (trimmed.isNotBlank()) onSave(trimmed, cat.id, cat.label)
                },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeHeader(
    heroDayTitle: String,
    status: TramaStatus,
    statusLabel: String?,
    locationRunning: Boolean,
    onAddClick: () -> Unit,
    onChatClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val t = LocalTramaColors.current
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 10.dp, bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = t.tealBg
                    ) {
                        Text(
                            text = "T",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = t.teal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Trama",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = heroDayTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = t.mutedText,
                        )
                    }
                }
                HeaderIconButton(
                    icon = Icons.Default.Add,
                    contentDescription = "Añadir nota",
                    onClick = onAddClick,
                    tint = t.amber
                )
                HeaderIconButton(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "Asistente",
                    onClick = onChatClick,
                    tint = t.teal
                )
                HeaderIconButton(
                    icon = Icons.Default.Settings,
                    contentDescription = "Ajustes",
                    onClick = onSettingsClick,
                    tint = t.mutedText
                )
            }
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusPill(status = status, label = statusLabel)
                if (locationRunning) {
                    StatusPill(status = TramaStatus.Location)
                }
            }
        }
    }
}

@Composable
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color
) {
    Surface(
        modifier = Modifier.padding(start = 6.dp),
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = LocalTramaColors.current.surface2,
        border = BorderStroke(0.5.dp, LocalTramaColors.current.softBorder)
    ) {
        Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(19.dp)
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
    val t = LocalTramaColors.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = t.redBg),
        border = BorderStroke(0.5.dp, t.red.copy(alpha = 0.26f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
private fun DailyPageSummaryCard(
    page: DailyPage?,
    isToday: Boolean = false
) {
    val t = LocalTramaColors.current
    val markdown = page?.markdown?.trim().orEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = t.surface),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            t.amber.copy(alpha = 0.22f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CalendarHistoryHeader(title = "Página diaria")
            if (page == null) {
                Text(
                    text = if (isToday) {
                        "La página diaria de hoy aparecerá cuando cierre el día."
                    } else {
                        "Todavía no hay página diaria generada para este día."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                if (markdown.isNotBlank()) {
                    MarkdownPreview(markdown = markdown)
                } else {
                    Text(
                        text = "La página diaria existe, pero aún no tiene contenido .md guardado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownPreview(markdown: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        markdown
            .lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                val (text, style, weight) = when {
                    line.startsWith("## ") -> Triple(
                        line.removePrefix("## "),
                        MaterialTheme.typography.titleSmall,
                        FontWeight.SemiBold
                    )
                    line.startsWith("# ") -> Triple(
                        line.removePrefix("# "),
                        MaterialTheme.typography.titleMedium,
                        FontWeight.Bold
                    )
                    else -> Triple(
                        line,
                        MaterialTheme.typography.bodySmall,
                        FontWeight.Normal
                    )
                }
                Text(
                    text = text,
                    style = style,
                    fontWeight = weight,
                    fontFamily = if (line.startsWith("- ")) FontFamily.Monospace else null,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
    }
}

@Composable
private fun CalendarImportedEventCard(event: com.trama.shared.model.TimelineEvent) {
    val context = LocalContext.current
    val t = LocalTramaColors.current
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { CalendarHelper.openTimelineEvent(context, event) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = t.surface),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            t.amber.copy(alpha = 0.20f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = t.amber,
                modifier = Modifier.size(16.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(timeFormat.format(Date(event.timestamp)))
                        event.endTimestamp?.let {
                            append(" – ")
                            append(timeFormat.format(Date(it)))
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                event.subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ── History card (pending or completed) ─────────────────────────────────────

@Composable
private fun CalendarHistoryCard(
    entry: DiaryEntry,
    isCompleted: Boolean,
    onClick: () -> Unit
) {
    val t = LocalTramaColors.current
    val timeFormat = remember { SimpleDateFormat("d MMM · HH:mm", Locale("es")) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted)
                t.tealBg
            else
                t.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            if (isCompleted) t.teal.copy(alpha = 0.24f) else t.softBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val (icon, iconColor) = calendarActionVisual(entry.actionType, isCompleted, t)
            CalendarGlyph(
                icon = icon,
                tint = iconColor,
                background = iconColor.copy(alpha = if (isCompleted) 0.09f else 0.14f)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = entry.displayText.ifBlank { entry.text },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (isCompleted)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isCompleted) {
                        val closedAt = entry.completedAt ?: entry.createdAt
                        "Cerrada ${timeFormat.format(Date(closedAt))}"
                    } else {
                        "${EntryActionType.label(entry.actionType)} · creada ${timeFormat.format(Date(entry.createdAt))}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CalendarGlyph(
    icon: ImageVector,
    tint: Color,
    background: Color,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Surface(
        modifier = modifier.size(34.dp),
        shape = RoundedCornerShape(8.dp),
        color = background
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun calendarActionVisual(
    type: String,
    isCompleted: Boolean,
    t: com.trama.app.ui.theme.TramaColors
): Pair<ImageVector, Color> {
    val fallback = if (isCompleted) t.teal else t.amber
    return when (type) {
        EntryActionType.CALL -> Icons.Default.Phone to t.watch
        EntryActionType.BUY -> Icons.Default.ShoppingCart to t.amber
        EntryActionType.SEND -> Icons.Default.Send to t.teal
        EntryActionType.EVENT -> Icons.Default.Event to t.warn
        EntryActionType.REVIEW -> Icons.Default.Search to t.teal
        EntryActionType.TALK_TO -> Icons.Default.Forum to t.watch
        else -> Icons.Default.CheckCircle to fallback
    }
}

// ── Recording card ───────────────────────────────────────────────────────────

@Composable
private fun CalendarRecordingCard(
    recording: Recording,
    onClick: () -> Unit
) {
    val t = LocalTramaColors.current
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = t.surface),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            t.red.copy(alpha = 0.20f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint = t.red,
                modifier = Modifier.size(16.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recording.title?.ifBlank { null } ?: "Grabación",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = timeFormat.format(Date(recording.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Place card ───────────────────────────────────────────────────────────────

@Composable
private fun CalendarPlaceCard(
    place: Place,
    durationLabel: String?,
    onRate: (Int?) -> Unit,
    onOpenDetail: () -> Unit,
    onOpenMap: () -> Unit
) {
    val t = LocalTramaColors.current
    val scope = rememberCoroutineScope()
    var rating by remember(place.id, place.rating) { mutableStateOf(place.rating ?: 0) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = t.surface),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            t.teal.copy(alpha = 0.18f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(22.dp)
                        .background(t.teal, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    tint = t.teal,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LUGAR",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = t.teal
                    )
                    Text(
                        text = place.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = onOpenDetail) {
                    Text("Ficha", style = MaterialTheme.typography.labelMedium)
                }
            }
            durationLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = t.teal,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { star ->
                        val selected = star <= rating
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) t.warnBg
                                    else t.surface2
                                )
                                .clickable {
                                    scope.launch {
                                        rating = star
                                        onRate(star)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "$star",
                                tint = if (selected) t.warn else t.dimText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                TextButton(onClick = onOpenMap) {
                    Text("Mapa", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// ── Section header ───────────────────────────────────────────────────────────

@Composable
private fun CalendarHistoryHeader(title: String, subtitle: String = "") {
    val t = LocalTramaColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = t.mutedText,
        )
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = t.dimText
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(t.hairline)
        )
    }
}

@Composable
private fun CalendarEmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
    )
}

// ── Calendar day cell ────────────────────────────────────────────────────────

private data class CalDay(
    val dayOfMonth: Int,
    val isCurrentMonth: Boolean
)

@Composable
private fun CalendarDay(
    day: CalDay,
    isToday: Boolean,
    isSelected: Boolean,
    hasEntries: Boolean,
    hasCompleted: Boolean,
    entryCount: Int,
    onClick: () -> Unit
) {
    val t = LocalTramaColors.current
    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> t.amberBg
            isToday    -> t.surface2
            else       -> t.surface
        },
        label = "dayBg"
    )
    val textColor = when {
        !day.isCurrentMonth -> t.dimText
        isSelected -> t.amber
        isToday -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable(enabled = day.isCurrentMonth, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (day.isCurrentMonth) "${day.dayOfMonth}" else "",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
            if (day.isCurrentMonth && hasEntries) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(top = 1.dp)
                ) {
                    if (entryCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(t.amber.copy(alpha = 0.7f))
                        )
                    }
                    if (hasCompleted) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(t.teal.copy(alpha = 0.7f))
                        )
                    }
                }
            }
        }
    }
}

// ── Grid builder ─────────────────────────────────────────────────────────────

private fun buildCalendarDays(displayMonth: Calendar): List<CalDay> {
    val cal = displayMonth.clone() as Calendar
    cal.set(Calendar.DAY_OF_MONTH, 1)

    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val offset = when (firstDayOfWeek) {
        Calendar.MONDAY -> 0; Calendar.TUESDAY -> 1; Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY -> 3; Calendar.FRIDAY -> 4; Calendar.SATURDAY -> 5
        Calendar.SUNDAY -> 6; else -> 0
    }

    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val totalCells = ((offset + daysInMonth + 6) / 7) * 7

    return (0 until totalCells).map { index ->
        val dayNum = index - offset + 1
        CalDay(
            dayOfMonth = if (dayNum in 1..daysInMonth) dayNum else 0,
            isCurrentMonth = dayNum in 1..daysInMonth
        )
    }
}

private fun java.util.Calendar.weekdayLetter(): String {
    return when (get(java.util.Calendar.DAY_OF_WEEK)) {
        java.util.Calendar.MONDAY -> "L"
        java.util.Calendar.TUESDAY -> "M"
        java.util.Calendar.WEDNESDAY -> "X"
        java.util.Calendar.THURSDAY -> "J"
        java.util.Calendar.FRIDAY -> "V"
        java.util.Calendar.SATURDAY -> "S"
        java.util.Calendar.SUNDAY -> "D"
        else -> ""
    }
}

@Composable
private fun WeekStrip(
    selectedDayStart: Long,
    todayStart: Long,
    entriesByDay: Map<Int, List<com.trama.shared.model.DiaryEntry>>,
    eventEntriesByDay: Map<Int, List<com.trama.shared.model.TimelineEvent>>,
    completedByDay: Map<Int, List<com.trama.shared.model.DiaryEntry>>,
    displayMonth: java.util.Calendar,
    onDaySelected: (Long) -> Unit,
) {
    val t = com.trama.app.ui.theme.LocalTramaColors.current
    val selCal = java.util.Calendar.getInstance().apply {
        timeInMillis = selectedDayStart
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val weekStart = (selCal.clone() as java.util.Calendar).apply {
        add(java.util.Calendar.DAY_OF_YEAR, -3)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (i in 0..6) {
            val dayCal = (weekStart.clone() as java.util.Calendar).apply {
                add(java.util.Calendar.DAY_OF_YEAR, i)
            }
            val dayMs = dayCal.timeInMillis
            val dayOfMonth = dayCal.get(java.util.Calendar.DAY_OF_MONTH)
            val dayLabel = dayCal.weekdayLetter()
            val inDisplayMonth = dayCal.get(java.util.Calendar.MONTH) == displayMonth.get(java.util.Calendar.MONTH) &&
                dayCal.get(java.util.Calendar.YEAR) == displayMonth.get(java.util.Calendar.YEAR)
            val isSelected = dayMs == selCal.timeInMillis
            val isToday = dayMs == todayStart
            val hasEntries = inDisplayMonth && (
                (entriesByDay[dayOfMonth]?.isNotEmpty() == true) ||
                    (eventEntriesByDay[dayOfMonth]?.isNotEmpty() == true)
            )
            val hasCompleted = inDisplayMonth && (completedByDay[dayOfMonth]?.isNotEmpty() == true)

            val bg = when {
                isSelected -> t.amber
                isToday -> t.surface2
                else -> androidx.compose.ui.graphics.Color.Transparent
            }
            val fg = when {
                isSelected -> androidx.compose.ui.graphics.Color.White
                else -> MaterialTheme.colorScheme.onSurface
            }
            val labelFg = when {
                isSelected -> androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f)
                else -> t.mutedText
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .clickable { onDaySelected(dayMs) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelFg,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = fg,
                    fontWeight = if (isSelected || isToday) androidx.compose.ui.text.font.FontWeight.Bold
                        else androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                hasEntries -> if (isSelected) androidx.compose.ui.graphics.Color.White else t.amber
                                hasCompleted -> if (isSelected) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f) else t.teal
                                else -> androidx.compose.ui.graphics.Color.Transparent
                            }
                        )
                )
            }
        }
    }
}
