package com.trama.app.chat

import com.trama.shared.data.DiaryRepository
import com.trama.shared.model.DailyPage
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.Place
import com.trama.shared.model.Recording
import com.trama.shared.model.TimelineEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale

sealed class ChatRetrievedContext {
    data class Day(
        val dateRange: ChatDateRange,
        val dailyPage: DailyPage?,
        val entries: List<DiaryEntry>,
        val completedEntries: List<DiaryEntry>,
        val recordings: List<Recording>,
        val timelineEvents: List<TimelineEvent>,
        val placesById: Map<Long, Place>
    ) : ChatRetrievedContext()

    data class PlaceLookup(
        val dateRange: ChatDateRange?,
        val results: List<PlaceResult>
    ) : ChatRetrievedContext()
}

data class PlaceResult(
    val term: String,
    val place: Place,
    val visits: List<TimelineEvent>
)

class ChatContextRetriever(
    private val repository: DiaryRepository
) {

    suspend fun retrieve(query: ChatQuery): ChatRetrievedContext? = withContext(Dispatchers.IO) {
        when (query.intent) {
            ChatIntent.DAY_SUMMARY, ChatIntent.DAY_PLACES, ChatIntent.COMPLETED_TASKS -> retrieveDay(query.dateRange)
            ChatIntent.FIRST_PLACE, ChatIntent.LAST_PLACE -> retrieveDay(query.dateRange)
            ChatIntent.PLACE_PRESENCE, ChatIntent.PLACE_DURATION, ChatIntent.PLACE_ORDER, ChatIntent.PLACE_AFTER ->
                retrievePlaces(query.placeTerms, query.dateRange)
            ChatIntent.UNKNOWN -> null
        }
    }

    private suspend fun retrieveDay(dateRange: ChatDateRange?): ChatRetrievedContext.Day? {
        val range = dateRange ?: return null
        val entries = repository.byDateRange(range.startMillis, range.endMillis).first()
            .sortedBy { it.createdAt }
        val completed = repository.getCompletedByCompletedAt(range.startMillis, range.endMillis).first()
            .sortedBy { it.completedAt ?: it.createdAt }
        val recordings = repository.getAllRecordingsOnce()
            .filter { it.createdAt in range.startMillis..range.endMillis }
            .sortedBy { it.createdAt }
        val timelineEvents = repository.getTimelineEventsByDateRangeOnce(range.startMillis, range.endMillis)
        val placeIds = timelineEvents.mapNotNull { it.placeId }.toSet()
        val placesById = repository.getAllPlacesOnce()
            .filter { it.id in placeIds }
            .associateBy { it.id }

        return ChatRetrievedContext.Day(
            dateRange = range,
            dailyPage = repository.getDailyPageOnce(range.startMillis),
            entries = entries,
            completedEntries = completed,
            recordings = recordings,
            timelineEvents = timelineEvents,
            placesById = placesById
        )
    }

    private suspend fun retrievePlaces(
        terms: List<String>,
        dateRange: ChatDateRange?
    ): ChatRetrievedContext.PlaceLookup? {
        if (terms.isEmpty()) return null

        val allPlaces = repository.getAllPlacesOnce()
        val results = terms.mapNotNull { term ->
            val place = findBestPlace(term, allPlaces) ?: return@mapNotNull null
            val visits = repository.getTimelineEventsByPlaceId(place.id).first()
                .filter { event ->
                    val inRange = dateRange == null || event.timestamp in dateRange.startMillis..dateRange.endMillis
                    inRange
                }
            PlaceResult(term = term, place = place, visits = visits)
        }

        return if (results.isEmpty()) null else ChatRetrievedContext.PlaceLookup(dateRange, results)
    }

    private fun findBestPlace(term: String, places: List<Place>): Place? {
        val normalizedTerm = normalize(term)
        return places
            .map { place -> place to score(normalizedTerm, normalize(place.name)) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Place, Int>> { it.second }.thenBy { it.first.name.length })
            .firstOrNull()
            ?.first
    }

    private fun score(term: String, placeName: String): Int {
        if (term == placeName) return 6
        if (placeName.contains(term)) return 5
        if (term.contains(placeName)) return 4

        val termTokens = tokenSet(term)
        val placeTokens = tokenSet(placeName)
        val overlap = termTokens.intersect(placeTokens)

        return when {
            overlap.isNotEmpty() && overlap.size == termTokens.size -> 3
            overlap.size >= 2 -> 2
            overlap.size == 1 -> 1
            else -> 0
        }
    }

    private fun tokenSet(value: String): Set<String> =
        value.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 2 }
            .toSet()

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase(Locale("es"))
            .trim()
}
