package com.trama.app.ui.screens

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trama.app.capture.SaveManualCapture
import com.trama.app.service.EntryProcessingState
import com.trama.app.summary.ActionItemProcessor
import com.trama.shared.data.DiaryRepository
import com.trama.shared.model.EntryContentKind
import com.trama.shared.util.DayRange
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

/** Owns Home queries and manual capture independently of the composable lifetime. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: DiaryRepository,
    private val savedState: SavedStateHandle,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {
    private val day = MutableStateFlow(DayRange.today())
    private val month = MutableStateFlow(DayRange.today())
    private fun <T> Flow<T>.observed(): StateFlow<T?> =
        map<T, T?> { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val now = flow {
        while (true) {
            emit(System.currentTimeMillis())
            kotlinx.coroutines.delay(60_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())
    val upcomingEvents = now.map { DayRange.of(it).startMs }.distinctUntilChanged().flatMapLatest { start ->
        val end = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.DAY_OF_YEAR, 60) }.timeInMillis
        repository.getCalendarEventsOverlapping(start, end)
    }.observed()

    val monthEntries = month.flatMapLatest { repository.byDateRange(it.startMs, it.endInclusiveMs) }.observed()
    val monthEvents = month.flatMapLatest { repository.getTimelineEventsByDateRange(it.startMs, it.endInclusiveMs) }.observed()
    val places = repository.getPlaces().observed()
    val dayEvents = day.flatMapLatest { repository.getTimelineEventsByDateRange(it.startMs, it.endInclusiveMs) }.observed()
    val pendingOnDay = day.flatMapLatest { repository.getPendingForDay(it.startMs, it.endInclusiveMs) }.observed()
    val pendingOtherDays = day.flatMapLatest { repository.getPendingFromOtherDays(it.startMs, it.endInclusiveMs) }.observed()
    val duplicates = repository.getDuplicates().observed()
    val pending = repository.getPending().observed()
    val completedOnDay = day.flatMapLatest { repository.getCompletedByCompletedAt(it.startMs, it.endInclusiveMs) }.observed()
    val recordings = day.flatMapLatest { repository.getRecordingsByDateRange(it.startMs, it.endInclusiveMs) }.observed()

    fun selectRange(dayStart: Long, monthStart: Long, monthEndInclusive: Long) {
        day.value = DayRange.of(dayStart)
        month.value = DayRange(monthStart, monthEndInclusive + 1)
    }

    val draft = savedState.getStateFlow("captureDraft", "")
    val captureOpen = savedState.getStateFlow("captureOpen", false)
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _savedEntryId = MutableStateFlow<Long?>(null)
    val savedEntryId = _savedEntryId.asStateFlow()

    fun openCapture(dayStart: Long) {
        if (!savedState.contains("captureAt")) {
            val now = System.currentTimeMillis()
            savedState["captureAt"] = if (now in DayRange.of(dayStart)) now else {
                Calendar.getInstance().apply {
                    timeInMillis = dayStart
                    set(Calendar.HOUR_OF_DAY, 12)
                }.timeInMillis
            }
        }
        savedState["captureOpen"] = true
    }

    fun editDraft(text: String) {
        if (_saving.value) return
        savedState["captureDraft"] = text
        _error.value = null
    }

    fun dismissCapture() {
        if (_saving.value) return
        savedState["captureOpen"] = false
        // Retain nonempty drafts; reopening an empty capture should use the new day.
        if (draft.value.isBlank()) savedState.remove<Long>("captureAt")
    }

    fun acknowledgeSaved() { _savedEntryId.value = null }

    fun saveCapture() {
        if (_saving.value || draft.value.isBlank()) return
        val text = draft.value
        val capturedAt = savedState.get<Long>("captureAt") ?: System.currentTimeMillis().also {
            savedState["captureAt"] = it
        }
        _saving.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val save = SaveManualCapture(insert = { entry ->
                    repository.withTransaction {
                        // A restored draft can be retried after a commit whose UI acknowledgment was lost.
                        getByCreatedAtAndText(entry.createdAt, entry.text)
                            ?.takeIf { it.isManual }?.id ?: insert(entry)
                    }
                })
                val id = save(text, capturedAt)
                savedState["captureDraft"] = ""
                savedState["captureOpen"] = false
                savedState.remove<Long>("captureAt")
                _savedEntryId.value = id
                if (repository.getByIdOnce(id)?.contentKind != EntryContentKind.MEMORY) {
                    return@launch
                }
                EntryProcessingState.markProcessing(id)
                viewModelScope.launch {
                    try {
                        ActionItemProcessor(appContext).process(id, text, repository)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // The source entry is already durable and remains editable.
                    } finally {
                        EntryProcessingState.markFinished(id)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _error.value = "No se ha podido guardar. Tu texto sigue aquí; vuelve a intentarlo."
            } finally {
                _saving.value = false
            }
        }
    }
}
