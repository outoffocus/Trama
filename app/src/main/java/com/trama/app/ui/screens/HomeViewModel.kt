package com.trama.app.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trama.app.sync.PhoneToWatchSyncer
import com.trama.app.ui.SettingsDataStore
import com.trama.shared.data.DiaryRepository
import com.trama.shared.model.Recording
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.Source
import com.trama.shared.model.StatusSyncEntry
import com.trama.shared.model.TimelineEvent
import com.trama.shared.util.DayRange
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val startOfDay: Long = DayRange.today().startMs,
    val endOfDay: Long = DayRange.today().endInclusiveMs,
    val allPendingEntries: List<DiaryEntry> = emptyList(),
    val pendingEntries: List<DiaryEntry> = emptyList(),
    val suggestedEntries: List<DiaryEntry> = emptyList(),
    val duplicateEntries: List<DiaryEntry> = emptyList(),
    val completedEntries: List<DiaryEntry> = emptyList(),
    val olderEntries: List<DiaryEntry> = emptyList(),
    val completedTodayEntries: List<DiaryEntry> = emptyList(),
    val recordings: List<Recording> = emptyList(),
    val timelineEvents: List<TimelineEventUi> = emptyList(),
    val timelineColorPending: Int = SettingsDataStore.DEFAULT_TIMELINE_COLOR_PENDING,
    val timelineColorCompleted: Int = SettingsDataStore.DEFAULT_TIMELINE_COLOR_COMPLETED,
    val timelineColorRecording: Int = SettingsDataStore.DEFAULT_TIMELINE_COLOR_RECORDING,
    val timelineColorPlace: Int = SettingsDataStore.DEFAULT_TIMELINE_COLOR_PLACE,
    val timelineColorCalendar: Int = SettingsDataStore.DEFAULT_TIMELINE_COLOR_CALENDAR,
    val showOldEntriesExpanded: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: DiaryRepository,
    private val settings: SettingsDataStore,
    @ApplicationContext context: Context
) : ViewModel() {

    private val watchSyncer = PhoneToWatchSyncer(context, repository)
    private val todayRange = DayRange.today()

    private data class HomeData(
        val allPendingEntries: List<DiaryEntry>,
        val suggestedEntries: List<DiaryEntry>,
        val duplicateEntries: List<DiaryEntry>,
        val completedEntries: List<DiaryEntry>,
        val recordings: List<Recording>,
        val storedTimelineEvents: List<TimelineEvent>
    )

    private data class HomeSettings(
        val timelineColorPending: Int,
        val timelineColorCompleted: Int,
        val timelineColorRecording: Int,
        val timelineColorPlace: Int,
        val timelineColorCalendar: Int,
        val showOldEntriesExpanded: Boolean
    )

    private val homeData = combine(
        repository.getPending(),
        repository.getSuggested(),
        repository.getDuplicates(),
        repository.getCompleted(),
        repository.getAllRecordings(),
        repository.getTimelineEventsByDateRange(todayRange.startMs, todayRange.endInclusiveMs)
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        HomeData(
            allPendingEntries = values[0] as List<DiaryEntry>,
            suggestedEntries = values[1] as List<DiaryEntry>,
            duplicateEntries = values[2] as List<DiaryEntry>,
            completedEntries = values[3] as List<DiaryEntry>,
            recordings = values[4] as List<Recording>,
            storedTimelineEvents = values[5] as List<TimelineEvent>
        )
    }

    private val timelineColors = combine(
        settings.timelineColorPending,
        settings.timelineColorCompleted,
        settings.timelineColorRecording,
        settings.timelineColorPlace,
        settings.timelineColorCalendar,
    ) { pending, completed, recording, place, calendar ->
        intArrayOf(pending, completed, recording, place, calendar)
    }

    private val homeSettings = combine(
        timelineColors,
        settings.showOldEntriesExpanded
    ) { colors, showOldEntriesExpanded ->
        HomeSettings(
            timelineColorPending = colors[0],
            timelineColorCompleted = colors[1],
            timelineColorRecording = colors[2],
            timelineColorPlace = colors[3],
            timelineColorCalendar = colors[4],
            showOldEntriesExpanded = showOldEntriesExpanded
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(homeData, homeSettings) { data, settings ->
        val duplicateIds = data.duplicateEntries.map { it.id }.toSet()
        val pendingEntries = data.allPendingEntries.filter { it.id !in duplicateIds }
        val pastDayEntries = pendingEntries.filter { entry ->
            val due = entry.dueDate
            if (due != null) due < todayRange.startMs else entry.createdAt < todayRange.startMs
        }
        val pastDayIds = pastDayEntries.map { it.id }.toSet()
        val todayEntries = pendingEntries.filter { entry ->
            val due = entry.dueDate
            entry.id !in pastDayIds && (
                entry.createdAt >= todayRange.startMs ||
                    (due != null && due <= todayRange.endInclusiveMs)
            )
        }
        val todayRecordings = data.recordings.filter { it.createdAt >= todayRange.startMs }
        HomeUiState(
            isLoading = false,
            startOfDay = todayRange.startMs,
            endOfDay = todayRange.endInclusiveMs,
            allPendingEntries = data.allPendingEntries,
            pendingEntries = pendingEntries,
            suggestedEntries = data.suggestedEntries,
            duplicateEntries = data.duplicateEntries,
            completedEntries = data.completedEntries,
            olderEntries = pastDayEntries,
            completedTodayEntries = data.completedEntries.filter { (it.completedAt ?: 0L) >= todayRange.startMs },
            recordings = data.recordings,
            timelineEvents = buildTimelineEvents(
                createdEntries = todayEntries,
                completedEntries = emptyList(),
                recordings = todayRecordings,
                storedEvents = data.storedTimelineEvents
            ),
            timelineColorPending = settings.timelineColorPending,
            timelineColorCompleted = settings.timelineColorCompleted,
            timelineColorRecording = settings.timelineColorRecording,
            timelineColorPlace = settings.timelineColorPlace,
            timelineColorCalendar = settings.timelineColorCalendar,
            showOldEntriesExpanded = settings.showOldEntriesExpanded
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    fun syncAllToWatch() {
        viewModelScope.launch(Dispatchers.IO) {
            watchSyncer.syncAllToWatch()
        }
    }

    fun syncCompleted(entry: DiaryEntry) {
        syncCompleted(listOf(entry))
    }

    fun syncCompleted(entries: List<DiaryEntry>) {
        if (entries.isEmpty()) return
        viewModelScope.launch {
            watchSyncer.syncStatusChange(
                completed = entries.map { StatusSyncEntry(it.createdAt, it.text) }
            )
        }
    }

    fun syncDeleted(entries: List<DiaryEntry>) {
        if (entries.isEmpty()) return
        viewModelScope.launch {
            watchSyncer.syncStatusChange(
                deleted = entries.map { StatusSyncEntry(it.createdAt, it.text) }
            )
        }
    }

    fun addManualEntry(text: String, categoryId: String, categoryLabel: String) {
        viewModelScope.launch {
            repository.insert(
                DiaryEntry(
                    text = text,
                    keyword = categoryId,
                    category = categoryLabel,
                    confidence = 1.0f,
                    source = Source.PHONE,
                    duration = 0,
                    isManual = true,
                    cleanText = text
                )
            )
        }
    }

    fun deleteSelection(
        entryIds: List<Long>,
        recordingIds: List<Long>,
        eventIds: List<Long>,
        entriesToDelete: List<DiaryEntry>,
        onDone: suspend () -> Unit
    ) {
        viewModelScope.launch {
            if (entryIds.isNotEmpty()) {
                repository.deleteByIds(entryIds)
                syncDeleted(entriesToDelete)
            }
            if (recordingIds.isNotEmpty()) repository.deleteRecordingsByIds(recordingIds)
            if (eventIds.isNotEmpty()) repository.deleteTimelineEventsByIds(eventIds)
            onDone()
        }
    }

    fun completeEntries(entries: List<DiaryEntry>, onDone: suspend () -> Unit = {}) {
        if (entries.isEmpty()) return
        viewModelScope.launch {
            repository.markCompletedByIds(entries.map { it.id })
            syncCompleted(entries)
            onDone()
        }
    }

    fun acceptSuggestion(entry: DiaryEntry) {
        viewModelScope.launch {
            repository.markPending(entry.id)
        }
    }

    fun discardEntry(entry: DiaryEntry) {
        viewModelScope.launch {
            repository.markDiscarded(entry.id)
            syncDeleted(listOf(entry))
        }
    }

    fun keepDuplicate(entry: DiaryEntry) {
        viewModelScope.launch {
            repository.clearDuplicate(entry.id)
        }
    }

    fun deleteEntry(entry: DiaryEntry) {
        viewModelScope.launch {
            repository.deleteById(entry.id)
            syncDeleted(listOf(entry))
        }
    }

    fun markCompleted(entry: DiaryEntry) {
        viewModelScope.launch {
            repository.markCompleted(entry.id)
            syncCompleted(entry)
        }
    }

    fun restorePending(entry: DiaryEntry) {
        viewModelScope.launch {
            repository.markPending(entry.id)
            repository.updateDueDate(entry.id, entry.dueDate)
        }
    }

    fun updateDueDate(entryId: Long, dueDate: Long?) {
        viewModelScope.launch {
            repository.updateDueDate(entryId, dueDate)
        }
    }
}
