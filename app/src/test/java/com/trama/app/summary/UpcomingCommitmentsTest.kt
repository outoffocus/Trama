package com.trama.app.summary

import com.trama.shared.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class UpcomingCommitmentsTest {
    private fun task(id: Long, at: Long, status: String = EntryStatus.PENDING) =
        DiaryEntry(
            id = id,
            text = "Tarea $id",
            keyword = "manual",
            category = "Nota",
            confidence = 1f,
            source = Source.PHONE,
            duration = 0,
            dueDate = at,
            status = status
        )

    @Test
    fun `mixed preview keeps ongoing event and next task excluding proposals and done`() {
        val ongoing = TimelineEvent(
            id = 1,
            type = TimelineEventType.CALENDAR,
            title = "En curso",
            timestamp = 80,
            endTimestamp = 120
        )
        val done = ongoing.copy(id = 2, completedAt = 99)
        val result = UpcomingCommitments.select(
            entries = listOf(task(1, 130), task(2, 110, EntryStatus.SUGGESTED), task(3, 90)),
            events = listOf(ongoing, done),
            now = 100,
            end = 200
        )
        assertEquals(listOf("En curso", "Tarea 1"), result.map { it.title })
    }

    @Test
    fun `finished events and duplicate tasks do not displace commitments`() {
        val event = TimelineEvent(
            id = 1,
            type = TimelineEventType.CALENDAR,
            title = "Pasado",
            timestamp = 10,
            endTimestamp = 20
        )
        val items = UpcomingCommitments.select(
            listOf(task(1, 110).copy(duplicateOfId = 9), task(2, 150)),
            listOf(event),
            100,
            200
        )
        assertEquals(2L, items.single().entry?.id)
    }

    @Test
    fun `after day preview excludes todays events and keeps following days`() {
        val today = TimelineEvent(
            id = 1,
            type = TimelineEventType.CALENDAR,
            title = "Evento de hoy",
            timestamp = 150
        )
        val tomorrow = today.copy(id = 2, title = "Evento de mañana", timestamp = 250)

        val items = UpcomingCommitments.selectAfterDay(
            entries = listOf(task(1, 180), task(2, 260)),
            events = listOf(today, tomorrow),
            dayEnd = 200,
            end = 300
        )

        assertEquals(listOf("Evento de mañana", "Tarea 2"), items.map { it.title })
    }

    @Test
    fun `adding all day metadata does not change imported occurrence identity`() {
        val old = """{"calendarId":2,"eventId":3,"startMillis":4}"""
        val new = """{"allDay":true,"eventId":3,"startMillis":4,"calendarId":2}"""
        assertEquals(CalendarImportIdentity.key(old), CalendarImportIdentity.key(new))
        assertTrue(CalendarImportIdentity.allDay(new))
        assertFalse(CalendarImportIdentity.allDay(old))
    }
    @Test
    fun `all day date stays on the named date west of UTC`() {
        val utc = java.time.LocalDate.of(2026, 3, 29)
            .atStartOfDay(java.time.ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val zone = java.time.ZoneId.of("America/New_York")
        val local = CalendarImportIdentity.localAllDay(utc, TimeZone.getTimeZone(zone))
        assertEquals(
            java.time.LocalDate.of(2026, 3, 29),
            java.time.Instant.ofEpochMilli(local).atZone(zone).toLocalDate()
        )
    }
}
