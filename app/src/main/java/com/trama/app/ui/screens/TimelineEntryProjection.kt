package com.trama.app.ui.screens

import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryStatus

/** Meeting actions stay in their recording's review flow until accepted there. */
internal fun timelineSuggestions(
    entries: List<DiaryEntry>,
    dismissedIds: Set<Long> = emptySet()
): List<DiaryEntry> = entries.filter { entry ->
    entry.status == EntryStatus.SUGGESTED &&
        entry.sourceRecordingId == null &&
        entry.id !in dismissedIds
}

/**
 * Replaces a captured source row with its derived action in the day timeline.
 * Suggested and duplicate actions are rendered by their review sections, so
 * their source capture must not appear as a second copy in "Hoy".
 */
internal fun projectEntriesForDay(
    sourceEntries: List<DiaryEntry>,
    openActions: List<DiaryEntry>,
    dayStart: Long,
    dayEnd: Long,
    processingSourceIds: Set<Long> = emptySet()
): List<DiaryEntry> {
    val representedSourceIds = openActions.mapNotNullTo(mutableSetOf()) { it.parentEntryId }
    val sourcesWithoutAction = sourceEntries.filter { entry ->
        entry.createdAt in dayStart..dayEnd &&
            entry.id !in representedSourceIds &&
            entry.id !in processingSourceIds
    }
    val acceptedDerivedActions = openActions.filter { action ->
        action.parentEntryId != null &&
            action.parentEntryId !in processingSourceIds &&
            action.duplicateOfId == null &&
            action.status == EntryStatus.PENDING &&
            action.createdAt in dayStart..dayEnd
    }

    return (sourcesWithoutAction + acceptedDerivedActions)
        .distinctBy { it.id }
        .sortedBy { it.createdAt }
}

/** Pending tasks assigned to this day that are not already represented in its timeline. */
internal fun pendingOccurrencesForDay(
    pendingOnDay: List<DiaryEntry>,
    representedEntryIds: Set<Long>,
    dayStart: Long,
    dayEnd: Long
): List<Pair<DiaryEntry, Long>> = pendingOnDay
    .asSequence()
    .filter { it.status == EntryStatus.PENDING && it.id !in representedEntryIds }
    .mapNotNull { entry ->
        val occurrence = entry.dueDate ?: entry.createdAt
        (entry to occurrence).takeIf { occurrence in dayStart..dayEnd }
    }
    .distinctBy { it.first.id }
    .sortedBy { it.second }
    .toList()

/** Older open tasks remain in their own section unless they are explicitly due this day. */
internal fun pendingFromOtherDaysForDisplay(
    entries: List<DiaryEntry>,
    dayStart: Long,
    dayEnd: Long
): List<DiaryEntry> = entries.filter { entry ->
    entry.status == EntryStatus.PENDING &&
        entry.createdAt < dayStart &&
        entry.dueDate?.let { it !in dayStart..dayEnd } != false
}
