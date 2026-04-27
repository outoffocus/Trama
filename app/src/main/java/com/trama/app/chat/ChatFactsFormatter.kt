package com.trama.app.chat

import com.trama.app.summary.DailyInsightsCodec
import com.trama.shared.model.Place
import com.trama.shared.model.TimelineEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatFactsFormatter {

    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale("es"))

    fun format(query: ChatQuery, context: ChatRetrievedContext): String = when (context) {
        is ChatRetrievedContext.Day -> formatDayFacts(query, context)
        is ChatRetrievedContext.PlaceLookup -> formatPlaceFacts(query, context)
    }

    private fun formatDayFacts(query: ChatQuery, context: ChatRetrievedContext.Day): String = buildString {
        appendLine("QUESTION: ${query.rawQuestion}")
        appendLine("RANGE: ${context.dateRange.label}")
        appendLine("SUMMARY: ${context.dailyPage?.briefSummary.orEmpty()}")

        DailyInsightsCodec.decode(context.dailyPage?.insightsJson)?.let { insights ->
            appendLine("INSIGHTS_TOTAL_TRACKED_MINUTES: ${insights.totalTrackedMinutes}")
            appendLine("INSIGHTS_FIRST_PLACE: ${insights.firstPlaceName ?: "NONE"}")
            appendLine("INSIGHTS_LAST_PLACE: ${insights.lastPlaceName ?: "NONE"}")
            appendLine("INSIGHTS_COMPLETED_TASKS: ${insights.completedTaskCount}")
            appendLine("INSIGHTS_OPEN_TASKS: ${insights.openTaskCount}")
        }

        if (context.completedEntries.isNotEmpty()) {
            appendLine("COMPLETED_TASKS:")
            context.completedEntries.forEach { entry ->
                appendLine("- ${entry.displayText}")
            }
        }

        val placeEvents = context.timelineEvents.filter { it.placeId != null }
        if (placeEvents.isNotEmpty()) {
            appendLine("PLACE_VISITS:")
            placeEvents.forEach { event ->
                val place = event.placeId?.let { context.placesById[it] }
                appendLine(formatPlaceEvent(place, event))
            }
        }
    }

    private fun formatPlaceFacts(query: ChatQuery, context: ChatRetrievedContext.PlaceLookup): String = buildString {
        appendLine("QUESTION: ${query.rawQuestion}")
        appendLine("RANGE: ${context.dateRange?.label ?: "todo el historial"}")
        context.results.forEach { result ->
            appendLine("PLACE: ${result.place.name}")
            appendLine("VISITS_COUNT: ${result.visits.size}")
            appendLine("RATING: ${result.place.rating?.toString() ?: "NONE"}")
            appendLine("OPINION_SUMMARY: ${result.place.opinionSummary ?: "NONE"}")
            appendLine("OPINION_TEXT: ${result.place.opinionText ?: "NONE"}")
            appendLine("TOTAL_MINUTES: ${result.visits.sumOf { visitMinutes(it) }}")
            if (result.visits.isNotEmpty()) {
                appendLine("VISITS:")
                result.visits.forEach { event ->
                    appendLine(formatPlaceEvent(result.place, event))
                }
            }
        }
    }

    private fun formatPlaceEvent(place: Place?, event: TimelineEvent): String {
        val name = place?.name ?: event.title
        val start = dateTimeFormat.format(Date(event.timestamp))
        val end = dateTimeFormat.format(Date(event.endTimestamp ?: event.timestamp))
        val minutes = visitMinutes(event)
        return "- $name | start=$start | end=$end | minutes=$minutes"
    }

    private fun visitMinutes(event: TimelineEvent): Long =
        (((event.endTimestamp ?: event.timestamp) - event.timestamp) / 60_000L).coerceAtLeast(0L)
}
