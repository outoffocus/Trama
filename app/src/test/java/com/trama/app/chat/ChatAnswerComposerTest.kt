package com.trama.app.chat

import com.trama.shared.model.DailyPage
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.Place
import com.trama.shared.model.Recording
import com.trama.shared.model.Source
import com.trama.shared.model.TimelineEvent
import com.trama.shared.model.TimelineEventType
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAnswerComposerTest {

    private val composer = ChatAnswerComposer()

    @Test
    fun `composes day summary with places and tasks`() {
        val context = ChatRetrievedContext.Day(
            dateRange = ChatDateRange(0L, 1L, "Martes 21 de abril 2026"),
            dailyPage = DailyPage(dayStartMillis = 0L, date = "2026-04-21", briefSummary = "Fue un día movido."),
            entries = listOf(entry("Comprar pan"), entry("Llamar a Marta")),
            completedEntries = listOf(entry("Enviar presupuesto")),
            recordings = listOf(
                Recording(
                    createdAt = 1L,
                    title = "Ideas de la reunión",
                    transcription = "",
                    durationSeconds = 120,
                    source = Source.PHONE
                )
            ),
            timelineEvents = listOf(
                TimelineEvent(
                    id = 1L,
                    type = TimelineEventType.DWELL,
                    timestamp = 0L,
                    endTimestamp = 60 * 60 * 1000L,
                    title = "Oficina",
                    placeId = 7L
                )
            ),
            placesById = mapOf(7L to Place(id = 7L, name = "Oficina", latitude = 0.0, longitude = 0.0))
        )

        val answer = composer.compose(ChatQuery("¿Qué hice este martes?", ChatIntent.DAY_SUMMARY), context)

        assertTrue(answer!!.contains("Fue un día movido"))
        assertTrue(answer.contains("Oficina"))
        assertTrue(answer.contains("Comprar pan"))
    }

    @Test
    fun `composes place duration answer`() {
        val context = ChatRetrievedContext.PlaceLookup(
            dateRange = null,
            results = listOf(
                PlaceResult(
                    term = "oficina",
                    place = Place(id = 1L, name = "Oficina", latitude = 0.0, longitude = 0.0),
                    visits = listOf(
                        TimelineEvent(
                            id = 1L,
                            type = TimelineEventType.DWELL,
                            timestamp = 0L,
                            endTimestamp = 2 * 60 * 60 * 1000L,
                            title = "Oficina",
                            placeId = 1L
                        )
                    )
                )
            )
        )

        val answer = composer.compose(
            ChatQuery("¿Cuánto tiempo estuve en la oficina?", ChatIntent.PLACE_DURATION),
            context
        )

        assertTrue(answer!!.contains("2 h"))
        assertTrue(answer.contains("Oficina"))
    }

    @Test
    fun `composes comparative duration answer`() {
        val context = ChatRetrievedContext.PlaceLookup(
            dateRange = ChatDateRange(0L, 1L, "esta semana"),
            results = listOf(
                PlaceResult(
                    term = "casa",
                    place = Place(id = 1L, name = "Casa", latitude = 0.0, longitude = 0.0),
                    visits = listOf(
                        TimelineEvent(
                            id = 1L,
                            type = TimelineEventType.DWELL,
                            timestamp = 0L,
                            endTimestamp = 60 * 60 * 1000L,
                            title = "Casa",
                            placeId = 1L
                        )
                    )
                ),
                PlaceResult(
                    term = "oficina",
                    place = Place(id = 2L, name = "Oficina", latitude = 0.0, longitude = 0.0),
                    visits = listOf(
                        TimelineEvent(
                            id = 2L,
                            type = TimelineEventType.DWELL,
                            timestamp = 0L,
                            endTimestamp = 3 * 60 * 60 * 1000L,
                            title = "Oficina",
                            placeId = 2L
                        )
                    )
                )
            )
        )

        val answer = composer.compose(
            ChatQuery(
                "¿Dónde estuve más tiempo esta semana, en casa o en la oficina?",
                ChatIntent.PLACE_DURATION,
                dateRange = ChatDateRange(0L, 1L, "esta semana"),
                placeTerms = listOf("casa", "oficina")
            ),
            context
        )

        assertTrue(answer!!.contains("Pasaste más tiempo en Oficina"))
        assertTrue(answer.contains("esta semana"))
    }

    @Test
    fun `composes place order answer`() {
        val context = ChatRetrievedContext.PlaceLookup(
            dateRange = null,
            results = listOf(
                PlaceResult(
                    term = "casa",
                    place = Place(id = 1L, name = "Casa", latitude = 0.0, longitude = 0.0),
                    visits = listOf(
                        TimelineEvent(
                            id = 1L,
                            type = TimelineEventType.DWELL,
                            timestamp = 1_000L,
                            endTimestamp = 2_000L,
                            title = "Casa",
                            placeId = 1L
                        )
                    )
                ),
                PlaceResult(
                    term = "oficina",
                    place = Place(id = 2L, name = "Oficina", latitude = 0.0, longitude = 0.0),
                    visits = listOf(
                        TimelineEvent(
                            id = 2L,
                            type = TimelineEventType.DWELL,
                            timestamp = 5_000L,
                            endTimestamp = 6_000L,
                            title = "Oficina",
                            placeId = 2L
                        )
                    )
                )
            )
        )

        val answer = composer.compose(
            ChatQuery(
                "¿Fui antes a casa o a la oficina?",
                ChatIntent.PLACE_ORDER,
                placeTerms = listOf("casa", "oficina")
            ),
            context
        )

        assertTrue(answer!!.contains("Fuiste antes a Casa que a Oficina"))
    }

    @Test
    fun `composes first place answer`() {
        val context = dayContext()
        val answer = composer.compose(
            ChatQuery(
                "¿Cuál fue el primer sitio en el que estuve este martes?",
                ChatIntent.FIRST_PLACE,
                dateRange = ChatDateRange(0L, 1L, "Martes 21 de abril 2026")
            ),
            context
        )

        assertTrue(answer!!.contains("El primer sitio registrado"))
        assertTrue(answer.contains("Casa"))
    }

    @Test
    fun `composes day places answer`() {
        val context = dayContext()
        val answer = composer.compose(
            ChatQuery(
                "¿Dónde estuve este martes?",
                ChatIntent.DAY_PLACES,
                dateRange = ChatDateRange(0L, 1L, "Martes 21 de abril 2026")
            ),
            context
        )

        assertTrue(answer!!.contains("estuviste en"))
        assertTrue(answer.contains("Casa"))
        assertTrue(answer.contains("Oficina"))
    }

    @Test
    fun `composes completed tasks answer`() {
        val context = dayContext().copy(
            completedEntries = listOf(entry("Enviar presupuesto"), entry("Llamar a Marta"))
        )
        val answer = composer.compose(
            ChatQuery(
                "¿Qué tareas completé en marzo?",
                ChatIntent.COMPLETED_TASKS,
                dateRange = ChatDateRange(0L, 1L, "Marzo 2026")
            ),
            context
        )

        assertTrue(answer!!.contains("Tareas completadas"))
        assertTrue(answer.contains("Enviar presupuesto"))
        assertTrue(answer.contains("Llamar a Marta"))
    }

    @Test
    fun `composes last place answer`() {
        val context = dayContext()
        val answer = composer.compose(
            ChatQuery(
                "¿Cuál fue el último lugar en el que estuve este martes?",
                ChatIntent.LAST_PLACE,
                dateRange = ChatDateRange(0L, 1L, "Martes 21 de abril 2026")
            ),
            context
        )

        assertTrue(answer!!.contains("El último sitio registrado"))
        assertTrue(answer.contains("Oficina"))
    }

    private fun dayContext() = ChatRetrievedContext.Day(
        dateRange = ChatDateRange(0L, 1L, "Martes 21 de abril 2026"),
        dailyPage = DailyPage(dayStartMillis = 0L, date = "2026-04-21", briefSummary = "Fue un día movido."),
        entries = listOf(entry("Comprar pan")),
        completedEntries = emptyList(),
        recordings = emptyList(),
        timelineEvents = listOf(
            TimelineEvent(
                id = 1L,
                type = TimelineEventType.DWELL,
                timestamp = 1_000L,
                endTimestamp = 2_000L,
                title = "Casa",
                placeId = 1L
            ),
            TimelineEvent(
                id = 2L,
                type = TimelineEventType.DWELL,
                timestamp = 5_000L,
                endTimestamp = 6_000L,
                title = "Oficina",
                placeId = 2L
            )
        ),
        placesById = mapOf(
            1L to Place(id = 1L, name = "Casa", latitude = 0.0, longitude = 0.0),
            2L to Place(id = 2L, name = "Oficina", latitude = 0.0, longitude = 0.0)
        )
    )

    private fun entry(text: String) = DiaryEntry(
        id = 1L,
        text = text,
        keyword = "",
        category = "",
        confidence = 1f,
        source = Source.PHONE,
        duration = 0,
        cleanText = text
    )
}
