package com.trama.app.ui.screens

import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryContentKind
import com.trama.shared.model.EntryStatus
import com.trama.shared.model.Source
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineEntryProjectionTest {

    @Test
    fun `reminder awaiting confirmation appears only as suggestion`() {
        val source = entry(
            id = 1,
            text = "Recordar ver la serie Adults",
            contentKind = EntryContentKind.MEMORY,
            status = EntryStatus.SAVED
        )
        val suggestion = entry(
            id = 2,
            text = "Ver la serie Adults",
            contentKind = EntryContentKind.ACTION,
            status = EntryStatus.SUGGESTED,
            parentEntryId = source.id
        )

        val timeline = projectEntriesForDay(listOf(source), listOf(suggestion), 0, 2_000)

        assertEquals(emptyList<DiaryEntry>(), timeline)
    }

    @Test
    fun `accepted reminder replaces source with one task`() {
        val source = entry(
            id = 1,
            text = "Recordar ver la serie Adults",
            contentKind = EntryContentKind.MEMORY,
            status = EntryStatus.SAVED
        )
        val accepted = entry(
            id = 2,
            text = "Ver la serie Adults",
            contentKind = EntryContentKind.ACTION,
            status = EntryStatus.PENDING,
            parentEntryId = source.id
        )

        val timeline = projectEntriesForDay(listOf(source), listOf(accepted), 0, 2_000)

        assertEquals(listOf(accepted), timeline)
    }

    @Test
    fun `capture and derived task stay hidden until processing finishes`() {
        val source = entry(
            id = 20,
            text = "Recordar comprar pan",
            contentKind = EntryContentKind.MEMORY,
            status = EntryStatus.SAVED
        )
        val provisional = entry(
            id = 21,
            text = "Comprar pan",
            contentKind = EntryContentKind.ACTION,
            status = EntryStatus.PENDING,
            parentEntryId = source.id
        )

        val timeline = projectEntriesForDay(
            sourceEntries = listOf(source),
            openActions = listOf(provisional),
            dayStart = 0,
            dayEnd = 2_000,
            processingSourceIds = setOf(source.id)
        )

        assertEquals(emptyList<DiaryEntry>(), timeline)
    }

    @Test
    fun `capture without a derived action remains in the timeline`() {
        val source = entry(
            id = 1,
            text = "Una idea sin acción derivada",
            contentKind = EntryContentKind.MEMORY,
            status = EntryStatus.SAVED
        )

        assertEquals(listOf(source), projectEntriesForDay(listOf(source), emptyList(), 0, 2_000))
    }

    @Test
    fun `meeting suggestion stays only inside its recording review`() {
        val meetingSuggestion = entry(
            id = 3025,
            text = "Enviar el resumen de la reunión",
            contentKind = EntryContentKind.ACTION,
            status = EntryStatus.SUGGESTED,
            sourceRecordingId = 42
        )

        assertEquals(emptyList<DiaryEntry>(), timelineSuggestions(listOf(meetingSuggestion)))
    }

    @Test
    fun `ordinary capture suggestion remains in timeline review`() {
        val suggestion = entry(
            id = 4,
            text = "Comprar entradas",
            contentKind = EntryContentKind.ACTION,
            status = EntryStatus.SUGGESTED
        )

        assertEquals(listOf(suggestion), timelineSuggestions(listOf(suggestion)))
    }

    @Test
    fun `task created earlier but due today appears as a today occurrence`() {
        val dueToday = entry(
            id = 8,
            text = "Llamar al dentista",
            contentKind = EntryContentKind.ACTION,
            status = EntryStatus.PENDING,
            createdAt = 500,
            dueDate = 12_000
        )

        val occurrences = pendingOccurrencesForDay(
            pendingOnDay = listOf(dueToday),
            representedEntryIds = emptySet(),
            dayStart = 10_000,
            dayEnd = 20_000
        )

        assertEquals(listOf(dueToday to 12_000L), occurrences)
        assertEquals(
            emptyList<DiaryEntry>(),
            pendingFromOtherDaysForDisplay(listOf(dueToday), 10_000, 20_000)
        )
    }

    @Test
    fun `task already in today timeline is not rendered twice`() {
        val createdToday = entry(
            id = 9,
            text = "Comprar leche",
            contentKind = EntryContentKind.ACTION,
            status = EntryStatus.PENDING,
            createdAt = 15_000
        )

        assertEquals(
            emptyList<Pair<DiaryEntry, Long>>(),
            pendingOccurrencesForDay(
                pendingOnDay = listOf(createdToday),
                representedEntryIds = setOf(createdToday.id),
                dayStart = 10_000,
                dayEnd = 20_000
            )
        )
    }

    @Test
    fun `older undated task stays in other days section`() {
        val older = entry(
            id = 10,
            text = "Ordenar documentos",
            contentKind = EntryContentKind.ACTION,
            status = EntryStatus.PENDING,
            createdAt = 500
        )

        assertEquals(
            listOf(older),
            pendingFromOtherDaysForDisplay(listOf(older), 10_000, 20_000)
        )
    }

    private fun entry(
        id: Long,
        text: String,
        contentKind: String,
        status: String,
        createdAt: Long = 1_000,
        dueDate: Long? = null,
        parentEntryId: Long? = null,
        sourceRecordingId: Long? = null
    ) = DiaryEntry(
        id = id,
        text = text,
        keyword = "recordar",
        category = "Acción",
        confidence = 0.8f,
        createdAt = createdAt,
        source = Source.PHONE,
        duration = 2,
        cleanText = text,
        contentKind = contentKind,
        status = status,
        dueDate = dueDate,
        parentEntryId = parentEntryId,
        sourceRecordingId = sourceRecordingId
    )
}
