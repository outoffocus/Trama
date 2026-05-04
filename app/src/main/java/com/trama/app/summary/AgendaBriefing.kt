package com.trama.app.summary

import android.content.Context
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.TimelineEvent
import com.trama.shared.model.TimelineEventType
import com.trama.shared.util.DayRange
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class AgendaBriefing(
    val title: String,
    val shortText: String,
    val longText: String
)

object AgendaBriefingBuilder {

    private const val LOOKAHEAD_DAYS = 8
    private val timeFormat = SimpleDateFormat("HH:mm", Locale("es"))
    private val dayFormat = SimpleDateFormat("EEE d", Locale("es"))

    suspend fun build(context: Context): AgendaBriefing {
        val appContext = context.applicationContext
        runCatching { GoogleCalendarSyncManager(appContext).syncSelectedCalendars() }

        val repository = DatabaseProvider.getRepository(appContext)
        val today = DayRange.today()
        val rangeEnd = Calendar.getInstance().apply {
            timeInMillis = today.startMs
            add(Calendar.DAY_OF_YEAR, LOOKAHEAD_DAYS)
        }.timeInMillis - 1

        val events = repository.getTimelineEventsByDateRangeOnce(today.startMs, rangeEnd)
            .filter { it.type == TimelineEventType.CALENDAR }
            .sortedBy { it.timestamp }

        if (events.isEmpty()) {
            return AgendaBriefing(
                title = "Agenda despejada",
                shortText = "No hay eventos importados en los próximos días.",
                longText = "No hay eventos importados en los próximos $LOOKAHEAD_DAYS días."
            )
        }

        val todayEvents = events.filter { it.timestamp in today.startMs..today.endInclusiveMs }
        val tomorrowRange = tomorrowRange(today.startMs)
        val tomorrowEvents = events.filter { it.timestamp in tomorrowRange.startMs..tomorrowRange.endInclusiveMs }
        val prepEvents = events.filter(::needsPreparation).take(4)

        val shortText = when {
            todayEvents.isNotEmpty() -> "Hoy: ${compactList(todayEvents, maxItems = 2)}"
            tomorrowEvents.isNotEmpty() -> "Mañana: ${compactList(tomorrowEvents, maxItems = 2)}"
            else -> "Próximo: ${compactEvent(events.first(), includeDay = true)}"
        }

        val longText = buildString {
            appendLine("Hoy")
            appendLine(todayEvents.toSectionText("Sin eventos importados."))
            appendLine()
            appendLine("Mañana")
            appendLine(tomorrowEvents.toSectionText("Sin eventos importados."))

            val later = events.filter { it.timestamp > tomorrowRange.endInclusiveMs }
            if (later.isNotEmpty()) {
                appendLine()
                appendLine("Próximos días")
                later.take(6).forEach { appendLine("- ${compactEvent(it, includeDay = true)}") }
            }

            if (prepEvents.isNotEmpty()) {
                appendLine()
                appendLine("Conviene preparar")
                prepEvents.forEach { appendLine("- ${compactEvent(it, includeDay = true)}") }
            }
        }.trim()

        return AgendaBriefing(
            title = "Radar de agenda",
            shortText = shortText,
            longText = longText
        )
    }

    private fun tomorrowRange(todayStartMs: Long): DayRange {
        val tomorrowStart = Calendar.getInstance().apply {
            timeInMillis = todayStartMs
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        return DayRange.of(tomorrowStart)
    }

    private fun compactList(events: List<TimelineEvent>, maxItems: Int): String {
        val visible = events.take(maxItems).joinToString("; ") { compactEvent(it, includeDay = false) }
        val remaining = events.size - maxItems
        return if (remaining > 0) "$visible y $remaining más" else visible
    }

    private fun compactEvent(event: TimelineEvent, includeDay: Boolean): String {
        val day = if (includeDay) "${dayFormat.format(Date(event.timestamp)).replaceFirstChar { it.uppercase() }} · " else ""
        val time = if (isAllDay(event)) "Todo el día" else timeFormat.format(Date(event.timestamp))
        return "$day$time ${event.title}"
    }

    private fun List<TimelineEvent>.toSectionText(empty: String): String {
        if (isEmpty()) return "- $empty"
        return take(5).joinToString("\n") { "- ${compactEvent(it, includeDay = false)}" }
    }

    private fun isAllDay(event: TimelineEvent): Boolean {
        val duration = (event.endTimestamp ?: event.timestamp) - event.timestamp
        val cal = Calendar.getInstance().apply { timeInMillis = event.timestamp }
        return duration >= 23 * 60 * 60 * 1000L &&
            cal.get(Calendar.HOUR_OF_DAY) == 0 &&
            cal.get(Calendar.MINUTE) == 0
    }

    private fun needsPreparation(event: TimelineEvent): Boolean {
        val text = "${event.title} ${event.subtitle.orEmpty()}".lowercase(Locale.getDefault())
        return listOf(
            "viaje",
            "vuelo",
            "tren",
            "hotel",
            "médico",
            "medico",
            "dentista",
            "cumple",
            "boda",
            "reserva",
            "cita",
            "reunión",
            "reunion",
            "presentación",
            "presentacion",
            "entrevista"
        ).any { it in text }
    }
}
