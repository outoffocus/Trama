package com.trama.app.chat

import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.Place
import com.trama.shared.model.Source
import com.trama.shared.model.TimelineEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryAssistantSourcesTest {

    @Test
    fun `completed task answer links only the completed entries`() {
        val completed = entry(2, "Llamar al taller")
        val context = ChatRetrievedContext.Day(
            dateRange = range,
            dailyPage = null,
            entries = listOf(entry(1, "Nota cualquiera")),
            completedEntries = listOf(completed),
            recordings = emptyList(),
            timelineEvents = listOf(TimelineEvent(id = 4, type = "DWELL", timestamp = 1, title = "Café", placeId = 3)),
            placesById = mapOf(3L to place(3, "Café"))
        )

        val sources = DiaryAssistantSources.from(
            ChatQuery("¿Qué tareas completé?", ChatIntent.COMPLETED_TASKS, range),
            context
        )

        assertEquals(listOf(DiaryAssistantSource.Entry(2, "Llamar al taller")), sources)
    }

    @Test
    fun `place answer exposes each place once`() {
        val cafe = place(3, "Café")
        val context = ChatRetrievedContext.PlaceCollection(
            dateRange = null,
            regionTerms = emptyList(),
            places = listOf(
                PlaceResult("", cafe, emptyList()),
                PlaceResult("", cafe, emptyList())
            )
        )

        val sources = DiaryAssistantSources.from(
            ChatQuery("¿Qué lugares conozco?", ChatIntent.PLACE_LIST),
            context
        )

        assertEquals(1, sources.size)
        assertTrue(sources.single() is DiaryAssistantSource.Place)
    }

    private fun entry(id: Long, text: String) = DiaryEntry(
        id = id,
        text = text,
        keyword = "",
        category = "",
        confidence = 1f,
        source = Source.PHONE,
        duration = 0
    )

    private fun place(id: Long, name: String) = Place(
        id = id,
        name = name,
        latitude = 0.0,
        longitude = 0.0
    )

    private companion object {
        val range = ChatDateRange(0, 10, "hoy")
    }
}
