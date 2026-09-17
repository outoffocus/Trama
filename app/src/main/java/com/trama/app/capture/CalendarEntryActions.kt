package com.trama.app.capture

import android.content.Context
import com.trama.app.diagnostics.CaptureLog
import com.trama.app.summary.DeletionFeedbackStore
import com.trama.app.summary.RecordingDeletion
import com.trama.shared.data.DiaryRepository
import com.trama.shared.model.DiaryEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Local diary mutations; external calendar synchronization has its own adapter. */
class CalendarEntryActions @Inject constructor(
    private val repository: DiaryRepository,
    @param:ApplicationContext private val context: Context
) {
    suspend fun deleteSelection(
        entryIds: List<Long>, recordingIds: List<Long>, eventIds: List<Long>,
        learn: Boolean, reason: DeletionFeedbackStore.Reason?
    ) {
        val deleted = entryIds.mapNotNull { repository.getByIdOnce(it) }
        if (recordingIds.isNotEmpty()) {
            RecordingDeletion.delete(context, repository, recordingIds) {
                if (entryIds.isNotEmpty()) deleteByIds(entryIds)
                if (eventIds.isNotEmpty()) deleteTimelineEventsByIds(eventIds)
            }
        } else {
            repository.withTransaction {
                if (entryIds.isNotEmpty()) deleteByIds(entryIds)
                if (eventIds.isNotEmpty()) deleteTimelineEventsByIds(eventIds)
            }
        }
        // Only record negative feedback for a deletion that actually committed.
        deleted.forEach { entry ->
            logDelete(entry, "selection_bulk", reason, learn)
            if (learn && reason != null) {
                runCatching { DeletionFeedbackStore.record(context, entry.displayText, reason) }
            }
        }
    }

    suspend fun confirmSuggested(id: Long, source: String) = repository.confirmSuggested(id, source)
    suspend fun markCompleted(id: Long) = repository.markCompleted(id)
    suspend fun markPending(id: Long) = repository.markPending(id)
    suspend fun markDiscarded(id: Long) = check(repository.markDiscarded(id) == 1) {
        "Suggestion $id was not discarded"
    }
    suspend fun restoreDiscardedSuggestion(id: Long) = check(repository.restoreDiscardedSuggestion(id) == 1) {
        "Suggestion $id was not restored"
    }
    suspend fun clearDuplicate(id: Long) = repository.clearDuplicate(id)
    suspend fun updateDueDate(id: Long, dueDate: Long?) = repository.updateDueDate(id, dueDate)
    suspend fun markTimelineEventCompleted(id: Long) = repository.markTimelineEventCompleted(id)
    suspend fun markTimelineEventPending(id: Long) = repository.markTimelineEventPending(id)

    suspend fun deleteDuplicate(entry: DiaryEntry) {
        // A duplicate card is still a captured entry. Preserve it as discarded for
        // diagnostics and prevent it from resurfacing as a pending task.
        check(repository.discardDuplicate(entry.id) == 1) {
            "Duplicate entry ${entry.id} was not found"
        }
        logDelete(entry, "duplicate_card", null, false)
    }

    private fun logDelete(entry: DiaryEntry, source: String, reason: DeletionFeedbackStore.Reason?, learn: Boolean) {
        CaptureLog.logUserDelete(
            entryId = entry.id, text = entry.displayText.ifBlank { entry.text },
            createdAtMs = entry.createdAt, status = entry.status, actionType = entry.actionType,
            isManual = entry.isManual, wasCompleted = entry.completedAt != null,
            hadDueDate = entry.dueDate != null, source = source,
            reason = reason?.storageKey, learningEnabled = learn
        )
    }
}
