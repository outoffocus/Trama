package com.trama.app.summary

import com.trama.shared.model.*

/** Upcoming means scheduled or in progress, never inferred from a suggestion. */
object UpcomingCommitments {
    data class Item(val title: String, val at: Long, val entry: DiaryEntry? = null, val event: TimelineEvent? = null)

    fun select(entries: List<DiaryEntry>, events: List<TimelineEvent>, now: Long, end: Long, limit: Int = 2): List<Item> =
        (entries.filter {
            it.status == EntryStatus.PENDING && it.duplicateOfId == null && it.dueDate != null && it.dueDate!! in now..end
        }.map { Item(it.displayText, it.dueDate!!, entry = it) } +
            events.filter {
                it.type == TimelineEventType.CALENDAR && it.completedAt == null &&
                    it.timestamp <= end && (it.endTimestamp ?: it.timestamp) >= now
            }.map { Item(it.title, it.timestamp, event = it) })
            .distinctBy { it.entry?.let { e -> "entry:${e.id}" } ?: "event:${it.event!!.id}" }
            .sortedWith(compareBy<Item> { it.at }.thenBy { it.title })
            .take(limit.coerceAtLeast(0))

    /** Preview for the home screen that cannot repeat items in today's timeline. */
    fun selectAfterDay(
        entries: List<DiaryEntry>,
        events: List<TimelineEvent>,
        dayEnd: Long,
        end: Long,
        limit: Int = 2
    ): List<Item> =
        (entries.filter {
            it.status == EntryStatus.PENDING &&
                it.duplicateOfId == null &&
                it.dueDate != null &&
                it.dueDate!! > dayEnd &&
                it.dueDate!! <= end
        }.map { Item(it.displayText, it.dueDate!!, entry = it) } +
            events.filter {
                it.type == TimelineEventType.CALENDAR &&
                    it.completedAt == null &&
                    it.timestamp > dayEnd &&
                    it.timestamp <= end
            }.map { Item(it.title, it.timestamp, event = it) })
            .distinctBy { it.entry?.let { e -> "entry:${e.id}" } ?: "event:${it.event!!.id}" }
            .sortedWith(compareBy<Item> { it.at }.thenBy { it.title })
            .take(limit.coerceAtLeast(0))
}
