package com.trama.app.summary

import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryStatus
import com.trama.shared.model.Place
import com.trama.shared.model.Recording
import com.trama.shared.model.Source
import com.trama.shared.model.TimelineEvent
import com.trama.shared.model.TimelineEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailyInsightExtractorTest {

    private val extractor = DailyInsightExtractor()

    @Test
    fun `extracts first last place and durations`() {
        val snapshot = DailyReviewSnapshot(
            dayStartMillis = 0L,
            date = "2026-04-25",
            entriesToday = listOf(entry("Comprar pan")),
            tasksToReview = listOf(entry("Comprar pan")),
            duplicatesToReview = emptyList(),
            completedToday = listOf(entry("Enviar mail")),
            postponed = emptyList(),
            placesVisited = listOf(place(1L, "Casa"), place(2L, "Oficina")),
            placesToReview = emptyList(),
            recordings = emptyList(),
            timelineEvents = listOf(
                dwell(1L, "Casa", 0L, 60 * 60 * 1000L),
                dwell(2L, "Oficina", 2 * 60 * 60 * 1000L, 5 * 60 * 60 * 1000L)
            ),
            calendarEvents = emptyList(),
            summaryEntries = emptyList()
        )

        val insights = extractor.extract(snapshot)

        assertEquals("Casa", insights.firstPlaceName)
        assertEquals("Oficina", insights.lastPlaceName)
        assertEquals(240L, insights.totalTrackedMinutes)
        assertEquals("Oficina", insights.placeDurations.first().placeName)
        assertEquals(180L, insights.placeDurations.first().durationMinutes)
    }

    @Test
    fun `extracts empty insights without dwell events`() {
        val snapshot = DailyReviewSnapshot(
            dayStartMillis = 0L,
            date = "2026-04-25",
            entriesToday = emptyList(),
            tasksToReview = emptyList(),
            duplicatesToReview = emptyList(),
            completedToday = emptyList(),
            postponed = emptyList(),
            placesVisited = emptyList(),
            placesToReview = emptyList(),
            recordings = emptyList(),
            timelineEvents = emptyList(),
            calendarEvents = emptyList(),
            summaryEntries = emptyList()
        )

        val insights = extractor.extract(snapshot)

        assertNull(insights.firstPlaceName)
        assertEquals(0L, insights.totalTrackedMinutes)
        assertEquals(emptyList<PlaceDurationInsight>(), insights.placeDurations)
    }

    private fun entry(text: String) = DiaryEntry(
        id = 1L,
        text = text,
        keyword = "",
        category = "",
        confidence = 1f,
        source = Source.PHONE,
        duration = 0,
        status = EntryStatus.PENDING
    )

    private fun place(id: Long, name: String) = Place(id = id, name = name, latitude = 0.0, longitude = 0.0)

    private fun dwell(placeId: Long, title: String, start: Long, end: Long) = TimelineEvent(
        id = placeId,
        type = TimelineEventType.DWELL,
        timestamp = start,
        endTimestamp = end,
        title = title,
        placeId = placeId
    )
}
