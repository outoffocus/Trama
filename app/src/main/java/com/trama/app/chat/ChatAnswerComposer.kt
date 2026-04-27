package com.trama.app.chat

import com.trama.app.summary.DailyInsights
import com.trama.app.summary.DailyInsightsCodec
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.Place
import com.trama.shared.model.TimelineEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAnswerComposer {

    private val longDateFormat = SimpleDateFormat("EEEE d 'de' MMMM yyyy", Locale("es"))
    private val shortDateTimeFormat = SimpleDateFormat("d MMM yyyy · HH:mm", Locale("es"))

    fun compose(query: ChatQuery, context: ChatRetrievedContext): String? {
        return when {
            query.intent == ChatIntent.DAY_SUMMARY && context is ChatRetrievedContext.Day ->
                composeDaySummary(context)
            query.intent == ChatIntent.DAY_PLACES && context is ChatRetrievedContext.Day ->
                composeDayPlaces(context)
            query.intent == ChatIntent.COMPLETED_TASKS && context is ChatRetrievedContext.Day ->
                composeCompletedTasks(context)
            query.intent == ChatIntent.FIRST_PLACE && context is ChatRetrievedContext.Day ->
                composeFirstPlace(context)
            query.intent == ChatIntent.LAST_PLACE && context is ChatRetrievedContext.Day ->
                composeLastPlace(context)
            query.intent == ChatIntent.PLACE_PRESENCE && context is ChatRetrievedContext.PlaceLookup ->
                composePlacePresence(context)
            query.intent == ChatIntent.PLACE_DURATION && context is ChatRetrievedContext.PlaceLookup ->
                composePlaceDuration(context)
            query.intent == ChatIntent.PLACE_ORDER && context is ChatRetrievedContext.PlaceLookup ->
                composePlaceOrder(context)
            query.intent == ChatIntent.PLACE_AFTER && context is ChatRetrievedContext.PlaceLookup ->
                composePlaceAfter(context)
            else -> null
        }
    }

    private fun composeDaySummary(context: ChatRetrievedContext.Day): String {
        val header = "Para ${context.dateRange.label.lowercase(Locale("es"))} encontré esto:"
        val lines = mutableListOf<String>()

        context.dailyPage?.briefSummary
            ?.takeIf { it.isNotBlank() }
            ?.let { lines += it.trim() }

        DailyInsightsCodec.decode(context.dailyPage?.insightsJson)?.let { insights ->
            val insightBits = mutableListOf<String>()
            insights.firstPlaceName?.let { insightBits += "Primer lugar: $it" }
            insights.lastPlaceName?.let { insightBits += "Último lugar: $it" }
            if (insights.totalTrackedMinutes > 0) {
                insightBits += "Tiempo en lugares: ${formatDuration(insights.totalTrackedMinutes * 60_000L)}"
            }
            insightBits += insightBitsSafe(insights)
            if (insightBits.isNotEmpty()) {
                lines += "Insights: ${insightBits.joinToString("; ")}."
            }
        }

        val placeSummaries = summarizePlaces(context.timelineEvents, context.placesById)
        if (placeSummaries.isNotEmpty()) {
            lines += "Lugares: ${placeSummaries.joinToString("; ")}."
        }

        if (context.entries.isNotEmpty()) {
            lines += "Notas: ${context.entries.take(4).joinToString("; ") { it.displayText }}."
        }

        if (context.completedEntries.isNotEmpty()) {
            lines += "Completaste: ${context.completedEntries.take(4).joinToString("; ") { it.displayText }}."
        }

        if (context.recordings.isNotEmpty()) {
            lines += "Grabaciones: ${context.recordings.take(3).joinToString("; ") { it.title ?: it.summary ?: "Grabación sin título" }}."
        }

        if (lines.isEmpty()) {
            return "No encontré actividad registrada para ${context.dateRange.label.lowercase(Locale("es"))}."
        }

        return buildString {
            appendLine(header)
            lines.forEach { appendLine(it) }
        }.trim()
    }

    private fun composeDayPlaces(context: ChatRetrievedContext.Day): String {
        val ranked = rankedPlaces(context)
        if (ranked.isEmpty()) {
            return "No encontré lugares registrados para ${context.dateRange.label.lowercase(Locale("es"))}."
        }

        val places = ranked
            .distinctBy { it.first.id }
            .map { (place, event) -> "${place.name} (${shortTime(event.timestamp)})" }

        return buildString {
            append("En ${context.dateRange.label.lowercase(Locale("es"))} estuviste en ")
            append(places.joinToString(", "))
            append(".")
        }
    }

    private fun composePlacePresence(context: ChatRetrievedContext.PlaceLookup): String {
        val prefix = context.dateRange?.let {
            "En ${it.label.lowercase(Locale("es"))}:"
        } ?: "En todo el historial:"

        val lines = context.results.map { result ->
            if (result.visits.isEmpty()) {
                "No encontré visitas registradas a ${result.place.name}."
            } else {
                val lastVisit = result.visits.maxByOrNull { it.endTimestamp ?: it.timestamp }!!
                val totalDuration = result.visits.sumOf { visitDurationMillis(it) }
                val opinion = placeOpinionSnippet(result.place)
                "Sí, estuviste en ${result.place.name} ${result.visits.size} veces. " +
                    "La última fue ${shortDateTimeFormat.format(Date(lastVisit.timestamp))} " +
                    "y el tiempo total registrado es ${formatDuration(totalDuration)}.$opinion"
            }
        }

        return buildString {
            appendLine(prefix)
            lines.forEach { appendLine(it) }
        }.trim()
    }

    private fun composePlaceDuration(context: ChatRetrievedContext.PlaceLookup): String {
        val prefix = context.dateRange?.let {
            "Tiempo registrado en ${it.label.lowercase(Locale("es"))}:"
        } ?: "Tiempo registrado en todo el historial:"

        val durationsByPlace = context.results.associateWith { result ->
            result.visits.sumOf { visitDurationMillis(it) }
        }

        val lines = context.results.map { result ->
            val totalDuration = durationsByPlace[result] ?: 0L
            if (totalDuration <= 0L) {
                "No encontré tiempo registrado en ${result.place.name}."
            } else {
                "En ${result.place.name}: ${formatDuration(totalDuration)} en ${result.visits.size} visitas."
            }
        }

        val comparisonLine = if (context.results.size >= 2) {
            val ranked = durationsByPlace.entries.sortedByDescending { it.value }
            val top = ranked.firstOrNull()
            val second = ranked.getOrNull(1)
            if (top != null && second != null && top.value > second.value) {
                val diff = top.value - second.value
                "Pasaste más tiempo en ${top.key.place.name} que en ${second.key.place.name} por ${formatDuration(diff)}."
            } else if (top != null && second != null && top.value == second.value && top.value > 0L) {
                "${top.key.place.name} y ${second.key.place.name} tienen el mismo tiempo registrado."
            } else {
                null
            }
        } else {
            null
        }

        return buildString {
            appendLine(prefix)
            comparisonLine?.let { appendLine(it) }
            lines.forEach { appendLine(it) }
        }.trim()
    }

    private fun composePlaceOrder(context: ChatRetrievedContext.PlaceLookup): String {
        val prefix = context.dateRange?.let {
            "Orden registrado en ${it.label.lowercase(Locale("es"))}:"
        } ?: "Orden registrado en el historial:"

        val ranked = context.results
            .mapNotNull { result ->
                val firstVisit = result.visits.minByOrNull { it.timestamp } ?: return@mapNotNull null
                result to firstVisit.timestamp
            }
            .sortedBy { it.second }

        if (ranked.size < 2) {
            return buildString {
                appendLine(prefix)
                context.results.forEach { result ->
                    if (result.visits.isEmpty()) {
                        appendLine("No encontré visitas registradas a ${result.place.name}.")
                    }
                }
            }.trim()
        }

        val first = ranked[0]
        val second = ranked[1]
        val sameMoment = first.second == second.second

        return buildString {
            appendLine(prefix)
            if (sameMoment) {
                appendLine("${first.first.place.name} y ${second.first.place.name} aparecen al mismo tiempo en el registro.")
            } else {
                appendLine("Fuiste antes a ${first.first.place.name} que a ${second.first.place.name}.")
                appendLine(
                    "${first.first.place.name}: ${shortDateTimeFormat.format(Date(first.second))}."
                )
                appendLine(
                    "${second.first.place.name}: ${shortDateTimeFormat.format(Date(second.second))}."
                )
            }
        }.trim()
    }

    private fun composeFirstPlace(context: ChatRetrievedContext.Day): String {
        val ranked = rankedPlaces(context)
        val first = ranked.firstOrNull()
            ?: return "No encontré lugares registrados para ${context.dateRange.label.lowercase(Locale("es"))}."

        return buildString {
            append("El primer sitio registrado en ${context.dateRange.label.lowercase(Locale("es"))} fue ")
            append(first.first.name)
            append(" a las ")
            append(shortTime(first.second.timestamp))
            append(".")
        }
    }

    private fun composeLastPlace(context: ChatRetrievedContext.Day): String {
        val ranked = rankedPlaces(context)
        val last = ranked.lastOrNull()
            ?: return "No encontré lugares registrados para ${context.dateRange.label.lowercase(Locale("es"))}."

        return buildString {
            append("El último sitio registrado en ${context.dateRange.label.lowercase(Locale("es"))} fue ")
            append(last.first.name)
            append(" a las ")
            append(shortTime(last.second.timestamp))
            append(".")
        }
    }

    private fun composePlaceAfter(context: ChatRetrievedContext.PlaceLookup): String {
        val anchor = context.results.firstOrNull()
            ?: return "No pude resolver el lugar de referencia."
        val anchorVisit = anchor.visits.minByOrNull { it.timestamp }
            ?: return "No encontré visitas registradas a ${anchor.place.name}."

        val others = context.results.drop(1)
            .mapNotNull { result ->
                val nextVisit = result.visits
                    .filter { it.timestamp > anchorVisit.timestamp }
                    .minByOrNull { it.timestamp }
                    ?: return@mapNotNull null
                result.place to nextVisit
            }
            .sortedBy { it.second.timestamp }

        val next = others.firstOrNull()
            ?: return "No encontré un sitio posterior a ${anchor.place.name} en el rango consultado."

        return buildString {
            append("Después de ${anchor.place.name}, el siguiente sitio registrado fue ${next.first.name}")
            append(" a las ")
            append(shortTime(next.second.timestamp))
            append(".")
        }
    }

    private fun rankedPlaces(context: ChatRetrievedContext.Day): List<Pair<Place, TimelineEvent>> =
        context.timelineEvents
            .mapNotNull { event ->
                val placeId = event.placeId ?: return@mapNotNull null
                val place = context.placesById[placeId] ?: return@mapNotNull null
                place to event
            }
            .sortedBy { it.second.timestamp }

    private fun composeCompletedTasks(context: ChatRetrievedContext.Day): String {
        if (context.completedEntries.isEmpty()) {
            return "No encontré tareas completadas en ${context.dateRange.label.lowercase(Locale("es"))}."
        }

        return buildString {
            appendLine("Tareas completadas en ${context.dateRange.label.lowercase(Locale("es"))}:")
            context.completedEntries.forEach { entry ->
                appendLine("- ${entry.displayText}")
            }
        }.trim()
    }

    private fun shortTime(epochMs: Long): String =
        SimpleDateFormat("HH:mm", Locale("es")).format(Date(epochMs))

    private fun summarizePlaces(
        events: List<TimelineEvent>,
        placesById: Map<Long, Place>
    ): List<String> {
        return events
            .mapNotNull { event ->
                val placeId = event.placeId ?: return@mapNotNull null
                val place = placesById[placeId] ?: return@mapNotNull null
                val duration = visitDurationMillis(event)
                if (duration > 0L) "${place.name} (${formatDuration(duration)})" else place.name
            }
            .distinct()
            .take(4)
    }

    private fun visitDurationMillis(event: TimelineEvent): Long =
        ((event.endTimestamp ?: event.timestamp) - event.timestamp).coerceAtLeast(0L)

    private fun formatDuration(durationMillis: Long): String {
        val totalMinutes = durationMillis / 60_000L
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours} h ${minutes} min"
            hours > 0 -> "${hours} h"
            else -> "${minutes} min"
        }
    }

    private fun placeOpinionSnippet(place: Place): String {
        val summary = place.opinionSummary?.takeIf { it.isNotBlank() }
        val rating = place.rating
        return when {
            rating != null && summary != null -> " Además, lo valoraste con $rating/5: $summary"
            rating != null -> " Además, lo valoraste con $rating/5."
            summary != null -> " Además, anotaste: $summary"
            else -> ""
        }
    }

    private fun insightBitsSafe(insights: DailyInsights): List<String> {
        val bits = mutableListOf<String>()
        if (insights.completedTaskCount > 0) bits += "Completadas ${insights.completedTaskCount}"
        if (insights.openTaskCount > 0) bits += "Abiertas ${insights.openTaskCount}"
        return bits
    }
}
