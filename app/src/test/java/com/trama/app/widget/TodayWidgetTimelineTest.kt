package com.trama.app.widget

import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryStatus
import com.trama.shared.model.Source
import com.trama.shared.util.DayRange
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayWidgetTimelineTest {
    private val noon = DayRange.of(1_800_000_000_000L).startMs + 12 * HOUR

    @Test
    fun `today contains every open and completed action without other event types`() {
        val day = DayRange.of(noon)
        val overdue = entry(1, day.startMs - HOUR, dueDate = day.startMs - HOUR)
        val today = entry(2, day.startMs + 13 * HOUR, dueDate = day.startMs + 13 * HOUR)
        val completed = entry(3, day.startMs + HOUR, dueDate = null).copy(
            status = EntryStatus.COMPLETED,
            completedAt = day.startMs + 10 * HOUR
        )

        val snapshot = TodayWidgetTimeline.build(
            now = noon,
            pending = listOf(today, overdue),
            completed = listOf(completed),
            maxItems = Int.MAX_VALUE
        )

        assertEquals(3, snapshot.totalCount)
        assertEquals(listOf("Pend.", "10:00", "13:00"), snapshot.items.map { it.timeLabel })
        assertEquals(listOf("TAREA", "HECHO", "TAREA"), snapshot.items.map { it.label })
    }

    @Test
    fun `future actions remain visible after analysis assigns their date`() {
        val day = DayRange.of(noon)
        val tomorrow = entry(4, day.startMs, dueDate = day.endInclusiveMs + HOUR)

        val snapshot = TodayWidgetTimeline.build(noon, listOf(tomorrow), emptyList())

        assertEquals(1, snapshot.totalCount)
        assertEquals("Acción 4", snapshot.items.single().title)
        assertEquals("TAREA", snapshot.items.single().label)
    }

    @Test
    fun `derived action is published only after its source finishes processing`() {
        val provisional = entry(5, noon, dueDate = null).copy(parentEntryId = 99)

        val whileProcessing = TodayWidgetTimeline.build(
            now = noon,
            pending = listOf(provisional),
            completed = emptyList(),
            processingSourceIds = setOf(99),
            maxItems = Int.MAX_VALUE
        )
        val finished = TodayWidgetTimeline.build(
            now = noon,
            pending = listOf(provisional),
            completed = emptyList(),
            processingSourceIds = emptySet(),
            maxItems = Int.MAX_VALUE
        )

        assertEquals(0, whileProcessing.totalCount)
        assertEquals(listOf("Acción 5"), finished.items.map { it.title })
    }

    private fun entry(id: Long, createdAt: Long, dueDate: Long?) = DiaryEntry(
        id = id,
        text = "Acción $id",
        keyword = "recordatorios",
        category = "Acción",
        confidence = 1f,
        createdAt = createdAt,
        source = Source.PHONE,
        duration = 0,
        dueDate = dueDate
    )

    companion object {
        private const val HOUR = 60 * 60 * 1000L
    }
}
