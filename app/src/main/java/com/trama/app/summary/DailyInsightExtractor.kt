package com.trama.app.summary

import com.trama.shared.model.EntryStatus
import com.trama.shared.model.TimelineEvent

class DailyInsightExtractor {

    fun extract(snapshot: DailyReviewSnapshot): DailyInsights {
        val dwellEvents = snapshot.timelineEvents
            .filter { it.placeId != null }
            .sortedBy { it.timestamp }

        val placeDurations = dwellEvents
            .groupBy { event -> event.placeId to event.title }
            .map { (key, events) ->
                PlaceDurationInsight(
                    placeId = key.first,
                    placeName = key.second,
                    durationMinutes = events.sumOf { durationMinutes(it) },
                    visitCount = events.size
                )
            }
            .sortedByDescending { it.durationMinutes }

        val firstPlace = dwellEvents.firstOrNull()
        val lastPlace = dwellEvents.lastOrNull()

        val openTaskCount = snapshot.tasksToReview.count { it.status == EntryStatus.PENDING && it !in snapshot.postponed }

        return DailyInsights(
            totalTrackedMinutes = dwellEvents.sumOf { durationMinutes(it) },
            firstPlaceName = firstPlace?.title,
            firstPlaceArrival = firstPlace?.timestamp,
            lastPlaceName = lastPlace?.title,
            lastPlaceArrival = lastPlace?.timestamp,
            createdTaskCount = snapshot.entriesToday.size,
            completedTaskCount = snapshot.completedToday.size,
            postponedTaskCount = snapshot.postponed.size,
            openTaskCount = openTaskCount,
            placeDurations = placeDurations
        )
    }

    private fun durationMinutes(event: TimelineEvent): Long =
        (((event.endTimestamp ?: event.timestamp) - event.timestamp) / 60_000L).coerceAtLeast(0L)
}
