package com.trama.app.summary

import android.content.Context
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.DiaryEntry
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

        // Pending tasks with a dueDate inside the lookahead window plus all
        // overdue tasks. Tasks without a dueDate are out of scope for this
        // weekly briefing — they are surfaced in the Agenda screen instead.
        val allPending = repository.getPendingOnce()
        val upcomingTasks = allPending
            .filter { it.dueDate != null && it.dueDate!! in today.startMs..rangeEnd }
            .sortedBy { it.dueDate ?: 0L }
        val overdueTasks = allPending
            .filter { it.dueDate != null && it.dueDate!! < today.startMs }
            .sortedBy { it.dueDate ?: 0L }

        if (events.isEmpty() && upcomingTasks.isEmpty() && overdueTasks.isEmpty()) {
            return AgendaBriefing(
                title = "Agenda despejada",
                shortText = "Sin eventos ni tareas en los próximos días.",
                longText = "No hay eventos ni tareas con fecha en los próximos $LOOKAHEAD_DAYS días."
            )
        }

        val todayEvents = events.filter { it.timestamp in today.startMs..today.endInclusiveMs }
        val tomorrowRange = tomorrowRange(today.startMs)
        val tomorrowEvents = events.filter { it.timestamp in tomorrowRange.startMs..tomorrowRange.endInclusiveMs }
        val todayTasks = upcomingTasks.filter { (it.dueDate ?: 0L) in today.startMs..today.endInclusiveMs }
        val tomorrowTasks = upcomingTasks.filter { (it.dueDate ?: 0L) in tomorrowRange.startMs..tomorrowRange.endInclusiveMs }
        val prepEvents = events.filter(::needsPreparation).take(4)

        val shortText = buildShortText(events, upcomingTasks, overdueTasks, todayEvents, todayTasks, tomorrowEvents, tomorrowTasks)

        val longText = buildString {
            if (overdueTasks.isNotEmpty()) {
                appendLine("Vencidas")
                overdueTasks.take(5).forEach { appendLine("- ${compactTask(it, includeDay = true)}") }
                appendLine()
            }
            appendLine("Hoy")
            appendLine(combinedSectionText(todayEvents, todayTasks, "Sin eventos ni tareas."))
            appendLine()
            appendLine("Mañana")
            appendLine(combinedSectionText(tomorrowEvents, tomorrowTasks, "Sin eventos ni tareas."))

            val laterEvents = events.filter { it.timestamp > tomorrowRange.endInclusiveMs }
            val laterTasks = upcomingTasks.filter { (it.dueDate ?: 0L) > tomorrowRange.endInclusiveMs }
            if (laterEvents.isNotEmpty() || laterTasks.isNotEmpty()) {
                appendLine()
                appendLine("Próximos días")
                laterEvents.take(6).forEach { appendLine("- ${compactEvent(it, includeDay = true)}") }
                laterTasks.take(6).forEach { appendLine("- ${compactTask(it, includeDay = true)}") }
            }

            if (prepEvents.isNotEmpty()) {
                appendLine()
                appendLine("Conviene preparar")
                prepEvents.forEach { appendLine("- ${compactEvent(it, includeDay = true)}") }
            }
        }.trim()

        return AgendaBriefing(
            title = "Tu semana en Trama",
            shortText = shortText,
            longText = longText
        )
    }

    private fun buildShortText(
        events: List<TimelineEvent>,
        upcomingTasks: List<DiaryEntry>,
        overdueTasks: List<DiaryEntry>,
        todayEvents: List<TimelineEvent>,
        todayTasks: List<DiaryEntry>,
        tomorrowEvents: List<TimelineEvent>,
        tomorrowTasks: List<DiaryEntry>
    ): String {
        if (overdueTasks.isNotEmpty()) {
            val first = overdueTasks.first()
            val extra = overdueTasks.size - 1
            val tail = if (extra > 0) " (+$extra más)" else ""
            return "${overdueTasks.size} vencida${if (overdueTasks.size == 1) "" else "s"}: ${compactTask(first, includeDay = true)}$tail"
        }
        val parts = mutableListOf<String>()
        if (todayEvents.isNotEmpty() || todayTasks.isNotEmpty()) {
            parts += "Hoy: ${compactCombined(todayEvents, todayTasks, maxItems = 2)}"
        } else if (tomorrowEvents.isNotEmpty() || tomorrowTasks.isNotEmpty()) {
            parts += "Mañana: ${compactCombined(tomorrowEvents, tomorrowTasks, maxItems = 2)}"
        }
        if (parts.isEmpty()) {
            val ev = events.firstOrNull()
            val tk = upcomingTasks.firstOrNull()
            val first = when {
                ev == null && tk == null -> null
                ev == null -> compactTask(tk!!, includeDay = true)
                tk == null -> compactEvent(ev, includeDay = true)
                ev.timestamp <= (tk.dueDate ?: Long.MAX_VALUE) -> compactEvent(ev, includeDay = true)
                else -> compactTask(tk, includeDay = true)
            }
            if (first != null) parts += "Próximo: $first"
        }
        val totalEvents = events.size
        val totalTasks = upcomingTasks.size
        if (totalEvents + totalTasks > 0) {
            parts += "$totalEvents evento${if (totalEvents == 1) "" else "s"} · $totalTasks tarea${if (totalTasks == 1) "" else "s"}"
        }
        return parts.joinToString(" · ")
    }

    private fun tomorrowRange(todayStartMs: Long): DayRange {
        val tomorrowStart = Calendar.getInstance().apply {
            timeInMillis = todayStartMs
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        return DayRange.of(tomorrowStart)
    }

    private fun compactCombined(
        events: List<TimelineEvent>,
        tasks: List<DiaryEntry>,
        maxItems: Int
    ): String {
        val items = (events.map { compactEvent(it, includeDay = false) } +
            tasks.map { compactTask(it, includeDay = false) })
        val visible = items.take(maxItems).joinToString("; ")
        val remaining = items.size - maxItems
        return if (remaining > 0) "$visible y $remaining más" else visible
    }

    private fun compactEvent(event: TimelineEvent, includeDay: Boolean): String {
        val day = if (includeDay) "${dayFormat.format(Date(event.timestamp)).replaceFirstChar { it.uppercase() }} · " else ""
        val time = if (isAllDay(event)) "Todo el día" else timeFormat.format(Date(event.timestamp))
        return "$day$time ${event.title}"
    }

    private fun compactTask(task: DiaryEntry, includeDay: Boolean): String {
        val ts = task.dueDate ?: task.createdAt
        val day = if (includeDay) "${dayFormat.format(Date(ts)).replaceFirstChar { it.uppercase() }} · " else ""
        val text = task.displayText.ifBlank { task.text }
        return "${day}✓ $text"
    }

    private fun combinedSectionText(
        events: List<TimelineEvent>,
        tasks: List<DiaryEntry>,
        empty: String
    ): String {
        if (events.isEmpty() && tasks.isEmpty()) return "- $empty"
        val lines = mutableListOf<String>()
        events.take(5).forEach { lines += "- ${compactEvent(it, includeDay = false)}" }
        tasks.take(5).forEach { lines += "- ${compactTask(it, includeDay = false)}" }
        return lines.joinToString("\n")
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
