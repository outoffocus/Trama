package com.trama.app.summary

import android.content.Context
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.DailyPage
import com.trama.shared.model.DailyPageStatus
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryStatus
import com.trama.shared.model.Place
import com.trama.shared.model.Recording
import com.trama.shared.model.TimelineEvent
import com.trama.shared.model.TimelineEventType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DailyPageGenerator(
    context: Context
) {
    private val appContext = context.applicationContext
    private val repository = DatabaseProvider.getRepository(appContext)
    private val summaryGenerator = SummaryGenerator(appContext)
    private val insightExtractor = DailyInsightExtractor()
    private val markdownStore = DailyPageMarkdownStore(appContext)
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val longDateFormat = SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", Locale("es"))
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    suspend fun generateAndPersist(
        dayStartMillis: Long,
        status: String = DailyPageStatus.DRAFT
    ): DailyPage = withContext(Dispatchers.IO) {
        val snapshot = buildSnapshot(dayStartMillis)
        val dateStr = dayFormat.format(Date(dayStartMillis))
        val llmSummary = summaryGenerator.generate(snapshot.summaryEntries, dateStr)
        val briefSummary = llmSummary.narrative.trim()
        val insights = insightExtractor.extract(snapshot)
        val insightsJson = DailyInsightsCodec.encode(insights)
        val markdown = buildMarkdown(snapshot, briefSummary, insights)
        val markdownPath = markdownStore.write(dayStartMillis, markdown)
        val existing = repository.getDailyPageOnce(dayStartMillis)
        val now = System.currentTimeMillis()

        val page = DailyPage(
            dayStartMillis = dayStartMillis,
            date = dateStr,
            status = status,
            briefSummary = briefSummary,
            insightsJson = insightsJson,
            markdown = markdown,
            markdownPath = markdownPath,
            generatedAt = existing?.generatedAt ?: now,
            updatedAt = now,
            reviewedAt = existing?.reviewedAt,
            hasManualReview = existing?.hasManualReview ?: false
        )
        repository.upsertDailyPage(page)
        page
    }

    suspend fun regenerateWithReviewMark(dayStartMillis: Long): DailyPage {
        return withContext(Dispatchers.IO) {
            repository.markDailyPageReviewed(dayStartMillis)
            generateAndPersist(dayStartMillis, status = DailyPageStatus.DRAFT)
        }
    }

    suspend fun buildSnapshot(dayStartMillis: Long): DailyReviewSnapshot = withContext(Dispatchers.IO) {
        val endOfDay = com.trama.shared.util.DayRange.of(dayStartMillis).endInclusiveMs
        val entriesToday = repository.byDateRange(dayStartMillis, endOfDay).first()
        val pending = repository.getPending().first()
        val completed = repository.getCompleted().first()
        val duplicates = repository.getDuplicates().first()
        val recordings = repository.getAllRecordingsOnce().filter { it.createdAt in dayStartMillis..endOfDay }
        val timelineEvents = repository.getTimelineEventsByDateRangeOnce(dayStartMillis, endOfDay)
        val calendarEvents = timelineEvents.filter { it.type == TimelineEventType.CALENDAR }
        val places = resolveVisitedPlaces(dayStartMillis, endOfDay, timelineEvents)

        val tasksToReview = pending
            .filter { it.createdAt <= endOfDay && it.duplicateOfId == null }
            .sortedByDescending { it.createdAt }
        val duplicatesToReview = duplicates
            .filter { it.createdAt <= endOfDay }
            .sortedByDescending { it.createdAt }
        val completedToday = completed.filter { (it.completedAt ?: 0L) in dayStartMillis..endOfDay }
        val postponed = pending.filter { entry ->
            entry.createdAt <= endOfDay && (entry.dueDate ?: Long.MIN_VALUE) > endOfDay
        }.sortedBy { it.dueDate ?: Long.MAX_VALUE }
        val placesToReview = places.filter { it.rating == null && it.opinionText.isNullOrBlank() }
        val olderPending = pending.filter { it.createdAt < dayStartMillis }
        val summaryEntries = (entriesToday + olderPending)
            .distinctBy { it.id }

        DailyReviewSnapshot(
            dayStartMillis = dayStartMillis,
            date = dayFormat.format(Date(dayStartMillis)),
            entriesToday = entriesToday,
            tasksToReview = tasksToReview,
            duplicatesToReview = duplicatesToReview,
            completedToday = completedToday,
            postponed = postponed,
            placesVisited = places,
            placesToReview = placesToReview,
            recordings = recordings,
            timelineEvents = timelineEvents,
            calendarEvents = calendarEvents,
            summaryEntries = summaryEntries
        )
    }

    private suspend fun resolveVisitedPlaces(
        dayStartMillis: Long,
        endOfDay: Long,
        timelineEvents: List<TimelineEvent>
    ): List<Place> {
        val placeIds = timelineEvents.mapNotNull { it.placeId }.toSet()
        return repository.getAllPlacesOnce()
            .filter { place ->
                val visitedToday = (place.lastVisitAt ?: Long.MIN_VALUE) in dayStartMillis..endOfDay
                visitedToday || place.id in placeIds
            }
            .sortedByDescending { it.lastVisitAt ?: 0L }
    }

    private fun buildMarkdown(
        snapshot: DailyReviewSnapshot,
        briefSummary: String,
        insights: DailyInsights
    ): String {
        val headerDate = longDateFormat.format(Date(snapshot.dayStartMillis)).replaceFirstChar { it.uppercase() }
        val openTasks = snapshot.tasksToReview.filter { it.status == EntryStatus.PENDING && it !in snapshot.postponed }

        return buildString {
            appendLine("# $headerDate")
            appendLine()
            appendLine("## Resumen")
            appendLine(briefSummary.ifBlank { "Dia registrado sin resumen narrativo." })
            appendLine()

            appendLine("## Insights")
            appendLine("- Tiempo total registrado en lugares: ${formatMinutes(insights.totalTrackedMinutes)}")
            insights.firstPlaceName?.let {
                val arrival = insights.firstPlaceArrival?.let { ts -> timeFormat.format(Date(ts)) } ?: "sin hora"
                appendLine("- Primer lugar: $it · $arrival")
            }
            insights.lastPlaceName?.let {
                val arrival = insights.lastPlaceArrival?.let { ts -> timeFormat.format(Date(ts)) } ?: "sin hora"
                appendLine("- Ultimo lugar: $it · $arrival")
            }
            appendLine("- Tareas creadas: ${insights.createdTaskCount}")
            appendLine("- Tareas completadas: ${insights.completedTaskCount}")
            appendLine("- Tareas abiertas: ${insights.openTaskCount}")
            appendLine("- Tareas pospuestas: ${insights.postponedTaskCount}")
            if (insights.placeDurations.isEmpty()) {
                appendLine("- Sin tiempos por lugar")
            } else {
                insights.placeDurations.take(3).forEach { place ->
                    appendLine("- ${place.placeName}: ${formatMinutes(place.durationMinutes)} en ${place.visitCount} visita(s)")
                }
            }
            appendLine()

            appendLine("## Tareas abiertas")
            if (openTasks.isEmpty()) appendLine("- Ninguna")
            else openTasks.forEach { appendLine("- ${it.displayText}") }
            appendLine()

            appendLine("## Tareas completadas")
            if (snapshot.completedToday.isEmpty()) appendLine("- Ninguna")
            else snapshot.completedToday.forEach { appendLine("- ${it.displayText}") }
            appendLine()

            appendLine("## Tareas pospuestas")
            if (snapshot.postponed.isEmpty()) appendLine("- Ninguna")
            else snapshot.postponed.forEach { entry ->
                val due = entry.dueDate?.let { dayFormat.format(Date(it)) } ?: "sin fecha"
                appendLine("- ${entry.displayText} -> $due")
            }
            appendLine()

            appendLine("## Sitios visitados")
            if (snapshot.placesVisited.isEmpty()) {
                appendLine("- Ninguno")
            } else {
                snapshot.placesVisited.forEach { place ->
                    append("- ${place.name}")
                    place.rating?.let { append(" · ${it}/5") }
                    appendLine()
                    place.opinionSummary?.takeIf { it.isNotBlank() }?.let { appendLine("  - $it") }
                    place.opinionText?.takeIf { it.isNotBlank() }?.let { appendLine("  - Opinion: $it") }
                }
            }
            appendLine()

            appendLine("## Eventos")
            if (snapshot.calendarEvents.isEmpty() && snapshot.timelineEvents.isEmpty()) {
                appendLine("- Ninguno destacado")
            } else {
                snapshot.calendarEvents.forEach { event ->
                    val timeLabel = event.endTimestamp?.let { end ->
                        "${timeFormat.format(Date(event.timestamp))}-${timeFormat.format(Date(end))}"
                    } ?: timeFormat.format(Date(event.timestamp))
                    appendLine("- $timeLabel ${event.title}")
                }
                snapshot.timelineEvents
                    .filter { it.placeId == null && it.type != TimelineEventType.CALENDAR }
                    .forEach { event ->
                        val time = timeFormat.format(Date(event.timestamp))
                        appendLine("- $time ${event.title}")
                    }
            }
            appendLine()

            appendLine("## Grabaciones y notas destacadas")
            if (snapshot.recordings.isEmpty() && snapshot.entriesToday.isEmpty()) {
                appendLine("- Sin contenido destacado")
            } else {
                snapshot.recordings.forEach { recording ->
                    appendLine("- Grabacion: ${recording.title ?: recording.summary ?: "Sin titulo"}")
                }
                snapshot.entriesToday
                    .take(10)
                    .forEach { appendLine("- Nota: ${it.displayText}") }
            }
        }
    }

    private fun formatMinutes(totalMinutes: Long): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours} h ${minutes} min"
            hours > 0 -> "${hours} h"
            else -> "${minutes} min"
        }
    }
}

data class DailyReviewSnapshot(
    val dayStartMillis: Long,
    val date: String,
    val entriesToday: List<DiaryEntry>,
    val tasksToReview: List<DiaryEntry>,
    val duplicatesToReview: List<DiaryEntry>,
    val completedToday: List<DiaryEntry>,
    val postponed: List<DiaryEntry>,
    val placesVisited: List<Place>,
    val placesToReview: List<Place>,
    val recordings: List<Recording>,
    val timelineEvents: List<TimelineEvent>,
    val calendarEvents: List<TimelineEvent>,
    val summaryEntries: List<DiaryEntry>
)
