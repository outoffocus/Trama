package com.trama.app.ui.screens

import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.rememberTooltipState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trama.app.service.EntryProcessingState
import com.trama.app.service.RecordingState
import com.trama.app.service.ServiceController
import com.trama.app.audio.OfflineDictationCapture
import com.trama.app.audio.SherpaWhisperAsrEngine
import com.trama.app.summary.ActionExecutor
import com.trama.app.summary.ActionType
import com.trama.app.summary.CalendarHelper
import com.trama.app.summary.EntryActionBridge
import com.trama.app.summary.GoogleCalendarSyncManager
import com.trama.app.summary.SuggestedAction
import com.trama.app.ui.SettingsDataStore
import com.trama.app.ui.components.CalendarActionDialog
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
import com.trama.shared.model.Source
import com.trama.shared.model.TimelineEventType
import com.trama.shared.sync.MicCoordinator
import com.trama.shared.util.DayRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    onSearchClick: () -> Unit = {},
    onRecordingsListClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onAgendaClick: () -> Unit = {},
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val actions: CalendarActionsViewModel = hiltViewModel()
    val settings = remember { SettingsDataStore(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    fun launchMutation(block: suspend () -> Unit) {
        scope.launch {
            try { block() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { snackbarHostState.showSnackbar("No se ha podido guardar el cambio") }
        }
    }

    LaunchedEffect(Unit) {
        GoogleCalendarSyncManager(context).syncSelectedCalendars()
    }

    var todayStart by remember { mutableStateOf(DayRange.today().startMs) }

    val initialDayStart = remember(initialSelectedDayStart, todayStart) {
        initialSelectedDayStart ?: todayStart
    }

    var selectedDayStart by rememberSaveable { mutableStateOf(initialDayStart) }
    var displayMonth by rememberSaveable {
        mutableStateOf(
            Calendar.getInstance().apply {
                timeInMillis = initialDayStart
                set(Calendar.DAY_OF_MONTH, 1)
            }
        )
    }
    var showMonthSheet by remember { mutableStateOf(false) }
    val monthSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        while (true) {
            val currentTodayStart = DayRange.today().startMs
            if (currentTodayStart != todayStart) {
                val wasShowingToday = selectedDayStart == todayStart
                todayStart = currentTodayStart
                if (wasShowingToday) {
                    selectedDayStart = currentTodayStart
                    displayMonth = Calendar.getInstance().apply {
                        timeInMillis = currentTodayStart
                        set(Calendar.DAY_OF_MONTH, 1)
                    }
                }
            }

            val now = System.currentTimeMillis()
            val nextDayStart = DayRange.of(now).endExclusiveMs
            delay((nextDayStart - now + 1_000L).coerceIn(1_000L, 60 * 60 * 1000L))
        }
    }

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

    LaunchedEffect(selectedDayStart, monthStart, monthEnd) {
        viewModel.selectRange(selectedDayStart, monthStart, monthEnd)
    }
    val monthEntriesState by viewModel.monthEntries.collectAsStateWithLifecycle()
    val monthStoredEventsState by viewModel.monthEvents.collectAsStateWithLifecycle()
    val placesState by viewModel.places.collectAsStateWithLifecycle()
    val selectedDayEventsState by viewModel.dayEvents.collectAsStateWithLifecycle()
    val pendingOnDayState by viewModel.pendingOnDay.collectAsStateWithLifecycle()
    val pendingFromOtherDaysState by viewModel.pendingOtherDays.collectAsStateWithLifecycle()
    val duplicateEntriesState by viewModel.duplicates.collectAsStateWithLifecycle()
    val allPendingState by viewModel.pending.collectAsStateWithLifecycle()
    val completedOnDayState by viewModel.completedOnDay.collectAsStateWithLifecycle()
    val recordingsState by viewModel.recordings.collectAsStateWithLifecycle()
    val processingEntryIds by EntryProcessingState.processingIds.collectAsState()
    val processingBackends by EntryProcessingState.processingBackends.collectAsState()
    val captureState by ServiceController.captureState.collectAsState()
    val serviceRunning = captureState.listeningActive
    val triggerRecognized = captureState.triggerRecognized
    val isRecording = captureState.recording
    val isRecordingProcessing = captureState.processing
    val recordingElapsed = captureState.elapsedSeconds
    val watchActive = captureState.watchActive
    val transferInProgress = captureState.transferring
    val locationRunning by ServiceController.isLocationRunning.collectAsState()
    val showListeningStatusOnHome by settings.listeningStatusOnHome.collectAsState(initialValue = false)
    val asrStatus by settings.asrDebugStatus.collectAsState(initialValue = "sin datos")
    val watchStatus by settings.watchDebugStatus.collectAsState(initialValue = "")
    val pendingColorIndex by settings.timelineColorPending.collectAsState(
        initialValue = SettingsDataStore.DEFAULT_TIMELINE_COLOR_PENDING
    )
    val completedColorIndex by settings.timelineColorCompleted.collectAsState(
        initialValue = SettingsDataStore.DEFAULT_TIMELINE_COLOR_COMPLETED
    )
    val recordingColorIndex by settings.timelineColorRecording.collectAsState(
        initialValue = SettingsDataStore.DEFAULT_TIMELINE_COLOR_RECORDING
    )
    val placeColorIndex by settings.timelineColorPlace.collectAsState(
        initialValue = SettingsDataStore.DEFAULT_TIMELINE_COLOR_PLACE
    )
    val calendarColorIndex by settings.timelineColorCalendar.collectAsState(
        initialValue = SettingsDataStore.DEFAULT_TIMELINE_COLOR_CALENDAR
    )

    val monthEntries = monthEntriesState ?: emptyList()
    val monthStoredEvents = monthStoredEventsState ?: emptyList()
    val selectedDayEvents = selectedDayEventsState ?: emptyList()
    val pendingOnDay = pendingOnDayState ?: emptyList()
    val pendingFromOtherDays = pendingFromOtherDaysState ?: emptyList()
    val storedDuplicateEntries = duplicateEntriesState ?: emptyList()
    var locallyDismissedDuplicateIds by remember { mutableStateOf(emptySet<Long>()) }
    val duplicateEntries = storedDuplicateEntries.filter {
        it.sourceRecordingId == null && it.id !in locallyDismissedDuplicateIds
    }
    // Keep locally hidden duplicates out of the timeline while Room persists the change.
    val duplicateIds = remember(storedDuplicateEntries) { storedDuplicateEntries.map { it.id }.toSet() }
    val allPendingForOriginalLookup = allPendingState ?: emptyList()
    val visiblePendingOnDay = remember(pendingOnDay, pendingFromOtherDays, duplicateIds) {
        (pendingOnDay + pendingFromOtherDays)
            .distinctBy { it.id }
            .filter { it.id !in duplicateIds }
    }
    var locallyDismissedSuggestionIds by remember { mutableStateOf(emptySet<Long>()) }
    val suggestedEntries = timelineSuggestions(visiblePendingOnDay, locallyDismissedSuggestionIds)
    val acceptedPendingOnDay = visiblePendingOnDay.filter { it.status == EntryStatus.PENDING }
    var reviewExpanded by rememberSaveable { mutableStateOf(false) }
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
    val entriesCreatedOnDay = remember(
        monthEntries,
        allPendingForOriginalLookup,
        storedDuplicateEntries,
        processingEntryIds,
        selectedDayStart,
        selectedDayEnd
    ) {
        projectEntriesForDay(
            sourceEntries = monthEntries.filter {
                it.status != EntryStatus.SUGGESTED &&
                    it.status != EntryStatus.DISCARDED &&
                    it.status != EntryStatus.COMPLETED
            },
            openActions = allPendingForOriginalLookup + storedDuplicateEntries,
            dayStart = selectedDayStart,
            dayEnd = selectedDayEnd,
            processingSourceIds = processingEntryIds
        )
    }
    val pendingOtherDays = remember(acceptedPendingOnDay, selectedDayStart, selectedDayEnd) {
        pendingFromOtherDaysForDisplay(
            entries = acceptedPendingOnDay,
            dayStart = selectedDayStart,
            dayEnd = selectedDayEnd
        )
    }
    val todayPendingOccurrences = remember(
        pendingOnDay,
        entriesCreatedOnDay,
        duplicateIds,
        selectedDayStart,
        selectedDayEnd
    ) {
        pendingOccurrencesForDay(
            pendingOnDay = pendingOnDay.filter { it.id !in duplicateIds },
            representedEntryIds = entriesCreatedOnDay.mapTo(mutableSetOf()) { it.id },
            dayStart = selectedDayStart,
            dayEnd = selectedDayEnd
        )
    }
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
    val upcomingThisWeek = remember(allPendingForOriginalLookup, todayStart, endOfThisWeek, duplicateIds) {
        allPendingForOriginalLookup.filter {
            it.id !in duplicateIds && (it.dueDate ?: Long.MIN_VALUE) in todayStart..endOfThisWeek
        }.sortedBy { it.dueDate ?: 0L }
    }
    val todayTimelineEvents = remember(
        entriesCreatedOnDay,
        todayPendingOccurrences,
        dayRecordings,
        activeSelectedDayEvents,
        duplicateIds
    ) {
        buildTimelineEvents(
            createdEntries = entriesCreatedOnDay.filter { it.id !in duplicateIds },
            pendingEntryOccurrences = todayPendingOccurrences,
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
    val showAddDialog by viewModel.captureOpen.collectAsStateWithLifecycle()
    val captureDraft by viewModel.draft.collectAsStateWithLifecycle()
    val captureSaving by viewModel.saving.collectAsStateWithLifecycle()
    val captureError by viewModel.error.collectAsStateWithLifecycle()
    val savedCaptureId by viewModel.savedEntryId.collectAsStateWithLifecycle()
    var selectionMode by remember { mutableStateOf(false) }
    var selectedEntryIds by remember { mutableStateOf(setOf<Long>()) }
    var selectedRecordingIds by remember { mutableStateOf(setOf<Long>()) }
    var selectedEventIds by remember { mutableStateOf(setOf<Long>()) }
    var otherDaysExpanded by remember { mutableStateOf(false) }
    var todayExpanded by remember { mutableStateOf(true) }
    var completedExpanded by remember { mutableStateOf(false) }
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

    val learnFromDeletions by settings.learnFromDeletions.collectAsState(initialValue = false)
    var pendingBulkDelete by remember { mutableStateOf(false) }

    fun performBulkDelete(reason: com.trama.app.summary.DeletionFeedbackStore.Reason?) {
        val entryIds = selectedEntryIds.toList()
        val recordingIds = selectedRecordingIds.toList()
        val eventIds = selectedEventIds.toList()
        if (entryIds.isEmpty() && recordingIds.isEmpty() && eventIds.isEmpty()) return

        launchMutation {
            actions.run { deleteSelection(entryIds, recordingIds, eventIds, learnFromDeletions, reason) }
            exitSelectionMode()
            pendingBulkDelete = false
        }
    }

    fun deleteSelected() {
        if (selectedEntryIds.isEmpty() && selectedRecordingIds.isEmpty() && selectedEventIds.isEmpty()) return
        // Only show the reason picker when learning is enabled AND at least
        // one diary entry is involved. Recordings / calendar events are not
        // quality signals, so deleting only those skips the dialog.
        if (learnFromDeletions && selectedEntryIds.isNotEmpty()) {
            pendingBulkDelete = true
        } else {
            performBulkDelete(reason = null)
        }
    }

    if (pendingBulkDelete) {
        com.trama.app.ui.components.DeleteReasonDialog(
            entryCount = selectedEntryIds.size,
            onDismiss = { pendingBulkDelete = false },
            onConfirm = { reason -> performBulkDelete(reason) },
            onConfirmWithoutReason = { performBulkDelete(reason = null) }
        )
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


    fun markEntryCompleted(entry: DiaryEntry) {
        launchMutation {
            if (entry.status == EntryStatus.SUGGESTED) {
                actions.run { confirmSuggested(entry.id, com.trama.shared.model.EntryVerificationSource.CALENDAR) }
                if (learnFromDeletions) {
                    val text = entry.displayText.ifBlank { entry.text }
                    com.trama.app.summary.DeletionFeedbackStore.recordAccepted(
                        context = context,
                        text = text,
                        source = "suggested_accept_calendar"
                    )
                }
                snackbarHostState.showSnackbar(
                    message = "Añadida a tareas",
                    duration = SnackbarDuration.Short
                )
                return@launchMutation
            }
            actions.run { markCompleted(entry.id) }
            val result = snackbarHostState.showSnackbar(
                message = "Marcada como hecha",
                actionLabel = "Deshacer",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                actions.run { markPending(entry.id) }
            } else if (learnFromDeletions) {
                val text = entry.displayText.ifBlank { entry.text }
                com.trama.app.summary.DeletionFeedbackStore.recordAccepted(
                    context = context,
                    text = text,
                    source = "marked_completed"
                )
            }
        }
    }

    fun reopenEntry(entry: DiaryEntry) {
        launchMutation {
            actions.run { markPending(entry.id) }
        }
    }

    fun dismissSuggested(entry: DiaryEntry) {
        locallyDismissedSuggestionIds = locallyDismissedSuggestionIds + entry.id
        scope.launch {
            try {
                actions.run { markDiscarded(entry.id) }
                val result = snackbarHostState.showSnackbar(
                    message = "Sugerencia descartada",
                    actionLabel = "Deshacer",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    actions.run { restoreDiscardedSuggestion(entry.id) }
                    locallyDismissedSuggestionIds = locallyDismissedSuggestionIds - entry.id
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                locallyDismissedSuggestionIds = locallyDismissedSuggestionIds - entry.id
                snackbarHostState.showSnackbar("No se ha podido descartar la sugerencia")
            }
        }
    }

    fun keepDuplicate(entry: DiaryEntry) {
        launchMutation { actions.run { clearDuplicate(entry.id) } }
    }

    fun deleteDuplicate(entry: DiaryEntry) {
        locallyDismissedDuplicateIds = locallyDismissedDuplicateIds + entry.id
        scope.launch {
            try {
                actions.run { deleteDuplicate(entry) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                locallyDismissedDuplicateIds = locallyDismissedDuplicateIds - entry.id
                snackbarHostState.showSnackbar("No se ha podido descartar la sugerencia")
            }
        }
    }

    fun postponeEntry(entry: DiaryEntry, dueDate: Long) {
        launchMutation {
            val previousDue = entry.dueDate
            actions.run { updateDueDate(entry.id, dueDate) }
            val result = snackbarHostState.showSnackbar(
                message = "Fecha de la tarea actualizada",
                actionLabel = "Deshacer",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                actions.run { updateDueDate(entry.id, previousDue) }
            }
        }
    }

    fun toggleCalendarEventCompleted(eventId: Long, completed: Boolean) {
        launchMutation {
            if (completed) {
                actions.run { markTimelineEventCompleted(eventId) }
            } else {
                actions.run { markTimelineEventPending(eventId) }
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
            isRecordingProcessing -> Unit
            isRecording -> RecordingState.stopRecording(context)
            watchActive -> {
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
        if (isRecording || isRecordingProcessing) return
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        ServiceController.startRecording(context)
    }

    fun transferListeningToWatch() {
        if (transferInProgress) return
        ServiceController.transferToWatch(context) { success ->
            if (!success) {
                scope.launch {
                    Toast.makeText(context, "No se ha podido conectar con el reloj", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun reclaimListeningFromWatch() {
        if (transferInProgress) return
        ServiceController.reclaimFromWatch(context)
    }

    LaunchedEffect(savedCaptureId) {
        val id = savedCaptureId ?: return@LaunchedEffect
        viewModel.acknowledgeSaved()
        if (snackbarHostState.showSnackbar("Guardado", "Ver") == SnackbarResult.ActionPerformed) {
            onEntryClick(id)
        }
    }
    if (showAddDialog) {
        ManualCaptureDialog(
            text = captureDraft,
            saving = captureSaving,
            error = captureError,
            onTextChange = viewModel::editDraft,
            onDismiss = viewModel::dismissCapture,
            onSave = viewModel::saveCapture
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
                    isRecording || isRecordingProcessing -> TramaStatus.Recording
                    watchActive -> TramaStatus.Watch
                    triggerRecognized -> TramaStatus.TriggerRecognized
                    serviceRunning && asrStatus.isListeningErrorStatus() ->
                        TramaStatus.Error
                    serviceRunning -> TramaStatus.Listening
                    else -> TramaStatus.Idle
                }
                val headerStatusLabel = when {
                    isRecording -> formatRecordingElapsed(recordingElapsed)
                    isRecordingProcessing -> "Transcribiendo..."
                    else -> homeListeningLabel(
                        showDetails = showListeningStatusOnHome,
                        watchActive = watchActive,
                        serviceRunning = serviceRunning,
                        triggerRecognized = triggerRecognized,
                        asrStatus = asrStatus,
                        watchStatus = watchStatus
                    )
                }
                HomeHeader(
                    heroDayTitle = heroDayTitle,
                    status = headerStatus,
                    statusLabel = headerStatusLabel,
                    locationRunning = locationRunning,
                    completedTaskCount = completedTasks.size,
                    totalTaskCount = completedTasks.size + todayTimelineEvents
                        .mapNotNull { (it as? TimelineEventUi.EntryCreated)?.entry }
                        .filter { it.status == EntryStatus.PENDING }
                        .distinctBy { it.id }
                        .size,
                    onAddClick = { viewModel.openCapture(selectedDayStart) },
                    onSearchClick = onSearchClick,
                    onRecordingsListClick = onRecordingsListClick,
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
                    monthLabel = monthFormat.format(Date(selectedDayStart))
                        .replaceFirstChar { it.uppercase() },
                    upcomingThisWeekCount = upcomingThisWeek.size,
                    onUpcomingPeekClick = onAgendaClick,
                    onNavigateDay = { offset -> navigateDay(offset) },
                    onOpenMonthPicker = { showMonthSheet = true },
                    onToday = { goToToday() },
                    onDaySelected = { ms -> selectDay(ms) }
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                VerticalQuickActionFabs(
                    serviceRunning = serviceRunning,
                    isRecording = isRecording,
                    isRecordingProcessing = isRecordingProcessing,
                    recordingElapsed = recordingElapsed,
                    watchActive = watchActive,
                    transferInProgress = transferInProgress,
                    onListeningClick = { handleMicClick() },
                    onRecordingClick = {
                        if (isRecording) RecordingState.stopRecording(context)
                        else startContinuousRecording()
                    },
                    onDeviceClick = {
                        if (watchActive) reclaimListeningFromWatch()
                        else transferListeningToWatch()
                    }
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
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = if (selectionMode) 24.dp else 210.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item("today_header") {
                        CollapsibleSectionHeader(
                            title = if (isSelectedToday) "Hoy" else "Ese día",
                            count = todayTimelineEvents.size,
                            expanded = todayExpanded,
                            onClick = { todayExpanded = !todayExpanded }
                        )
                    }
                    if (todayExpanded) {
                        if (todayTimelineEvents.isEmpty()) {
                            item("today_empty") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CalendarEmptyHint(
                                        if (isSelectedToday) "Tu día empieza aquí. Añade algo que quieras recordar."
                                        else "No hay actividad registrada para este día."
                                    )
                                    TextButton(onClick = { viewModel.openCapture(selectedDayStart) }) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Añadir recuerdo")
                                    }
                                }
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
                    if (completedTimelineEvents.isNotEmpty()) {
                        item("completed_header") {
                            CollapsibleSectionHeader(
                                title = if (isSelectedToday) "Completado hoy" else "Completado ese día",
                                count = completedTasks.size + completedCalendarEvents.size,
                                expanded = completedExpanded,
                                onClick = { completedExpanded = !completedExpanded }
                            )
                        }
                        if (completedExpanded) {
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
                    if (pendingOtherDays.isNotEmpty()) {
                        item("other_days_header") {
                            CollapsibleSectionHeader(
                                title = "Pendiente de otros días",
                                count = pendingOtherDays.size,
                                expanded = otherDaysExpanded,
                                onClick = { otherDaysExpanded = !otherDaysExpanded }
                            )
                        }
                        if (otherDaysExpanded) {
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
                    }
                    if (duplicateEntries.isNotEmpty() || suggestedEntries.isNotEmpty()) {
                        item("review_header") {
                            CollapsibleSectionHeader(
                                title = "Por revisar", count = duplicateEntries.size + suggestedEntries.size,
                                expanded = reviewExpanded, onClick = { reviewExpanded = !reviewExpanded }
                            )
                        }
                        if (reviewExpanded) {
                            item("review_explanation") {
                                Text(
                                    "Añade solo lo que quieras convertir en tarea. Todavía no está confirmado.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 2.dp)
                                )
                            }
                            items(duplicateEntries, key = { "dup_${it.id}" }) { entry ->
                                DuplicateCard(entry = entry,
                                    originalText = allPendingForOriginalLookup.find { it.id == entry.duplicateOfId }?.displayText,
                                    onKeep = { keepDuplicate(entry) }, onDelete = { deleteDuplicate(entry) })
                            }
                            items(suggestedEntries, key = { "suggestion_${it.id}" }) { entry ->
                                SuggestedReviewCard(
                                    entry = entry,
                                    onOpen = { onEntryClick(entry.id) },
                                    onAccept = { markEntryCompleted(entry) },
                                    onDismiss = { dismissSuggested(entry) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun homeListeningLabel(
    showDetails: Boolean,
    watchActive: Boolean,
    serviceRunning: Boolean,
    triggerRecognized: Boolean,
    asrStatus: String,
    watchStatus: String
): String {
    if (watchActive) {
        return if (showDetails) watchStatus
            .takeIf { it.isMeaningfulListeningStatus() }
            ?.toDisplayListeningStatus()
            ?: "Escucha en reloj"
        else "Escucha en reloj"
    }
    if (!serviceRunning) return "Escucha desactivada"
    if (triggerRecognized) return "Palabra clave reconocida"
    return if (showDetails) asrStatus
        .takeIf { it.isMeaningfulListeningStatus() }
        ?.toDisplayListeningStatus()
        ?: "Escuchando"
    else "Escuchando"
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
            .heightIn(min = 48.dp)
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
    val context = LocalContext.current
    val actionScope = rememberCoroutineScope()
    val quickAction = remember(
        entry.id,
        entry.actionType,
        entry.displayText,
        entry.dueDate,
        entry.status
    ) {
        EntryActionBridge.build(entry)
    }
    var editingCalendarAction by remember(entry.id) { mutableStateOf<SuggestedAction?>(null) }
    var pendingCalendarAction by remember(entry.id) { mutableStateOf<SuggestedAction?>(null) }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        editingCalendarAction = pendingCalendarAction
        pendingCalendarAction = null
    }

    editingCalendarAction?.let { action ->
        com.trama.app.ui.components.EntryCalendarActionDialog(
            entryId = entry.id,
            action = action,
            onDismiss = { editingCalendarAction = null }
        )
    }

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
            quickActionLabel = quickAction?.label,
            quickActionIcon = quickAction?.icon,
            onQuickActionClick = if (!selectionMode) {
                quickAction?.let { action ->
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
                }
            } else null,
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
            onToggleComplete = if (
                !selectionMode &&
                (entry.status == EntryStatus.PENDING || entry.status == EntryStatus.SUGGESTED)
            ) {
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
private fun VerticalQuickActionFabs(
    serviceRunning: Boolean,
    isRecording: Boolean,
    isRecordingProcessing: Boolean,
    recordingElapsed: Long,
    watchActive: Boolean,
    transferInProgress: Boolean,
    onListeningClick: () -> Unit,
    onRecordingClick: () -> Unit,
    onDeviceClick: () -> Unit
) {
    val t = LocalTramaColors.current
    val presentation = homeQuickActionPresentation(
        serviceRunning = serviceRunning,
        isRecording = isRecording,
        isRecordingProcessing = isRecordingProcessing,
        recordingElapsed = recordingElapsed,
        watchActive = watchActive,
        transferInProgress = transferInProgress
    )
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ThumbFabAction(
            label = if (watchActive) {
                "Escucha en el reloj · cambiar al teléfono"
            } else {
                "Escucha en el teléfono · cambiar al reloj"
            },
            icon = Icons.Default.Watch,
            enabled = presentation.deviceEnabled,
            selected = watchActive,
            accent = t.watch,
            onClick = onDeviceClick,
            loading = transferInProgress
        )
        ThumbFabAction(
            label = if (isRecording || isRecordingProcessing) presentation.recordingLabel else "Grabar reunión",
            icon = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
            enabled = presentation.recordingEnabled,
            selected = isRecording,
            accent = t.red,
            onClick = onRecordingClick,
            loading = isRecordingProcessing
        )
        ThumbFabAction(
            label = if (serviceRunning) "Pausar escucha continua" else "Activar escucha continua",
            icon = if (serviceRunning) Icons.Default.Mic else Icons.Default.MicOff,
            enabled = presentation.listeningEnabled,
            selected = serviceRunning,
            accent = t.amber,
            onClick = onListeningClick
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThumbFabAction(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    loading: Boolean = false
) {
    val t = LocalTramaColors.current
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState()
    ) {
        Surface(
            modifier = Modifier.size(54.dp).semantics {
                contentDescription = label
                stateDescription = when {
                    loading -> "En curso"
                    !enabled -> "No disponible durante la operación actual"
                    selected -> "Activo"
                    else -> "Inactivo"
                }
            },
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            color = if (selected) accent else t.surface2,
            shadowElevation = 10.dp,
            border = BorderStroke(
                width = if (selected) 0.dp else 1.dp,
                color = if (selected) Color.Transparent else accent.copy(alpha = 0.4f)
            )
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(23.dp),
                        color = accent,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = when {
                            selected -> Color.White
                            enabled -> accent
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

internal data class HomeQuickActionPresentation(
    val listeningLabel: String,
    val listeningEnabled: Boolean,
    val recordingLabel: String,
    val recordingEnabled: Boolean,
    val deviceLabel: String,
    val deviceEnabled: Boolean
)

internal fun homeQuickActionPresentation(
    serviceRunning: Boolean,
    isRecording: Boolean,
    isRecordingProcessing: Boolean,
    recordingElapsed: Long,
    watchActive: Boolean,
    transferInProgress: Boolean
): HomeQuickActionPresentation {
    val blocked = isRecording || isRecordingProcessing || transferInProgress
    val elapsedLabel = "%d:%02d".format(recordingElapsed / 60, recordingElapsed % 60)
    return HomeQuickActionPresentation(
        listeningLabel = when {
            transferInProgress -> "Espera…"
            serviceRunning -> "Pausar"
            else -> "Escuchar"
        },
        listeningEnabled = !blocked && !watchActive,
        recordingLabel = when {
            isRecording -> "Detener $elapsedLabel"
            isRecordingProcessing -> "Procesando…"
            else -> "Reunión"
        },
        recordingEnabled = !isRecordingProcessing && !transferInProgress && !watchActive,
        deviceLabel = when {
            transferInProgress -> "Transfiriendo…"
            watchActive -> "Recuperar"
            else -> "Reloj"
        },
        deviceEnabled = !blocked || watchActive && !transferInProgress
    )
}

@Composable
private fun ManualCaptureDialog(
    text: String,
    saving: Boolean,
    error: String?,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val whisper = remember { SherpaWhisperAsrEngine(context) }
    var activeCapture by remember { mutableStateOf<OfflineDictationCapture?>(null) }
    var isDictating by remember { mutableStateOf(false) }
    var isTranscribing by remember { mutableStateOf(false) }
    var dictationError by remember { mutableStateOf<String?>(null) }

    fun startDictation() {
        if (!whisper.isAvailable) {
            dictationError = "El dictado no está disponible ahora mismo."
            return
        }
        dictationError = null
        val capture = OfflineDictationCapture(context)
        activeCapture = capture
        scope.launch {
            try {
                isDictating = true
                val window = capture.capture()
                isDictating = false
                activeCapture = null
                if (window == null || window.durationMs() < 300L) {
                    dictationError = "No he captado audio suficiente."
                    return@launch
                }
                isTranscribing = true
                val transcript = withContext(Dispatchers.IO) {
                    whisper.transcribe(window, languageTag = "es")?.text?.trim()
                }
                if (transcript.isNullOrBlank()) {
                    dictationError = "No he podido transcribirlo."
                } else {
                    onTextChange(
                        listOf(text.trim(), transcript)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                    )
                }
            } finally {
                isDictating = false
                isTranscribing = false
                activeCapture = null
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startDictation()
        else dictationError = "Necesito permiso de micrófono para dictar."
    }

    DisposableEffect(Unit) {
        onDispose { activeCapture?.requestStop() }
    }

    val busy = saving || isDictating || isTranscribing
    val visibleError = error ?: dictationError
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Añadir") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    enabled = !busy,
                    isError = visibleError != null,
                    supportingText = { visibleError?.let { Text(it) } },
                    label = { Text("¿Qué quieres recordar?") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp)
                )
                TextButton(
                    onClick = {
                        if (isDictating) {
                            activeCapture?.requestStop()
                        } else if (
                            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            startDictation()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    enabled = !saving && !isTranscribing
                ) {
                    Icon(
                        if (isDictating) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            isTranscribing -> "Transcribiendo…"
                            isDictating -> "Detener dictado"
                            else -> "Dictar"
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = text.isNotBlank() && !busy,
                shape = RoundedCornerShape(10.dp)
            ) { Text(if (saving) "Guardando…" else "Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cerrar") }
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
    completedTaskCount: Int,
    totalTaskCount: Int,
    onAddClick: () -> Unit,
    onSearchClick: () -> Unit,
    onRecordingsListClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val t = LocalTramaColors.current
    var overflowExpanded by remember { mutableStateOf(false) }
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = heroDayTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("Trama", style = MaterialTheme.typography.labelMedium, color = t.mutedText)
                }
                HeaderIconButton(
                    icon = Icons.Default.Search,
                    contentDescription = "Buscar en Trama",
                    onClick = onSearchClick,
                    tint = t.teal
                )
                HeaderIconButton(
                    icon = Icons.Default.Add,
                    contentDescription = "Añadir",
                    onClick = onAddClick,
                    tint = t.teal
                )
                Box {
                    HeaderIconButton(
                        icon = Icons.Default.MoreVert,
                        contentDescription = "Más opciones",
                        onClick = { overflowExpanded = true },
                        tint = t.mutedText
                    )
                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Ver grabaciones") },
                            leadingIcon = { Icon(Icons.Default.Mic, contentDescription = null) },
                            onClick = {
                                overflowExpanded = false
                                onRecordingsListClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Ajustes") },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            onClick = {
                                overflowExpanded = false
                                onSettingsClick()
                            }
                        )
                    }
                }
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
            if (totalTaskCount > 0) {
                DayProgressIndicator(
                    completed = completedTaskCount,
                    total = totalTaskCount,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun DayProgressIndicator(
    completed: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val t = LocalTramaColors.current
    val safeTotal = total.coerceAtLeast(1)
    val safeCompleted = completed.coerceIn(0, safeTotal)
    val progress = safeCompleted.toFloat() / safeTotal.toFloat()
    val percentage = (progress * 100).toInt()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Tareas · $safeCompleted/$safeTotal",
                style = MaterialTheme.typography.labelMedium,
                color = t.teal,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$percentage%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(t.surface3)
        ) {
            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(t.teal.copy(alpha = 0.86f), t.teal)
                            )
                        )
                )
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
                    Text("Conservar ambas")
                }
            }
        }
    }
}

@Composable
private fun SuggestedReviewCard(
    entry: DiaryEntry,
    onOpen: () -> Unit,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    val t = LocalTramaColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = t.surface2),
        border = BorderStroke(0.5.dp, t.softBorder)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = entry.displayText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable(onClick = onOpen)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Descartar") }
                TextButton(onClick = onAccept) { Text("Añadir a tareas") }
            }
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
                                .size(48.dp)
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
                                contentDescription = "$star ${if (star == 1) "estrella" else "estrellas"}",
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
