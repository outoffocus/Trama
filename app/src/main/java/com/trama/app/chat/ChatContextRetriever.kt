package com.trama.app.chat

import com.trama.shared.data.DiaryRepository
import com.trama.shared.model.DailyPage
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryActionType
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

    data class PlaceCollection(
        val dateRange: ChatDateRange?,
        val regionTerms: List<String>,
        val places: List<PlaceResult>,
        val category: ChatPlaceCategory = ChatPlaceCategory.ANY,
        val likedOnly: Boolean = false
    ) : ChatRetrievedContext()

    data class GenericFacts(
        val dateRange: ChatDateRange?,
        val searchTerms: List<String>,
        val actionTypeFilter: String?,
        val answerMode: ChatAnswerMode,
        val entries: List<DiaryEntry>,
        val recordings: List<Recording>,
        val timelineEvents: List<TimelineEvent>,
        val placesById: Map<Long, Place>
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
            ChatIntent.GENERAL_FACT_SEARCH -> retrieveGenericFacts(query)
            ChatIntent.PLACE_LIST, ChatIntent.LIKED_PLACES -> retrievePlaceCollection(query)
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

    private suspend fun retrieveGenericFacts(query: ChatQuery): ChatRetrievedContext.GenericFacts {
        val entries = if (query.dateRange != null) {
            repository.byDateRange(query.dateRange.startMillis, query.dateRange.endMillis).first()
        } else {
            repository.getAllOnce()
        }
            .filter { matchesGenericEntry(it, query) }
            .sortedWith(
                compareByDescending<DiaryEntry> { genericEntryScore(it, query) }
                    .thenByDescending { it.completedAt ?: it.createdAt }
            )
            .take(GENERIC_RESULT_LIMIT)

        val recordings = repository.getAllRecordingsOnce()
            .asSequence()
            .filter { recording ->
                query.dateRange == null || recording.createdAt in query.dateRange.startMillis..query.dateRange.endMillis
            }
            .filter { recording ->
                query.actionTypeFilter == null && matchesText(genericRecordingText(recording), query.searchTerms)
            }
            .sortedByDescending { it.createdAt }
            .take(GENERIC_RECORDING_LIMIT)
            .toList()

        val timelineEvents = if (query.dateRange != null) {
            repository.getTimelineEventsByDateRangeOnce(query.dateRange.startMillis, query.dateRange.endMillis)
        } else {
            repository.getTimelineEvents().first()
        }
            .filter { event -> matchesText(genericEventText(event), query.searchTerms) }
            .sortedByDescending { it.timestamp }
            .take(GENERIC_EVENT_LIMIT)

        val placeIds = timelineEvents.mapNotNull { it.placeId }.toSet()
        val placesById = repository.getAllPlacesOnce()
            .filter { it.id in placeIds }
            .associateBy { it.id }

        return ChatRetrievedContext.GenericFacts(
            dateRange = query.dateRange,
            searchTerms = query.searchTerms,
            actionTypeFilter = query.actionTypeFilter,
            answerMode = query.answerMode,
            entries = entries,
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

    private suspend fun retrievePlaceCollection(query: ChatQuery): ChatRetrievedContext.PlaceCollection? {
        val allPlaces = repository.getAllPlacesOnce()
        if (allPlaces.isEmpty()) return null

        val events = if (query.dateRange != null) {
            repository.getTimelineEventsByDateRangeOnce(query.dateRange.startMillis, query.dateRange.endMillis)
        } else {
            repository.getTimelineEvents().first()
        }.filter { it.placeId != null }

        if (events.isEmpty()) {
            return ChatRetrievedContext.PlaceCollection(
                dateRange = query.dateRange,
                regionTerms = query.placeTerms,
                places = emptyList(),
                category = query.placeCategory,
                likedOnly = query.likedOnly
            )
        }

        val placesById = allPlaces.associateBy { it.id }
        val groupedEvents = events
            .mapNotNull { event ->
                val place = event.placeId?.let { placesById[it] } ?: return@mapNotNull null
                place to event
            }
            .groupBy({ it.first }, { it.second })

        val regionFiltered = groupedEvents.filter { (place, visits) ->
            query.placeTerms.isEmpty() || query.placeTerms.any { term -> matchesRegion(term, place, visits) }
        }.ifEmpty {
            // If the stored places do not carry country metadata, a scoped list question is still
            // more useful with the date-bounded visits than with an empty answer.
            if (query.intent == ChatIntent.PLACE_LIST && query.dateRange != null) groupedEvents else emptyMap()
        }

        val filtered = regionFiltered
            .filter { (place, _) -> matchesCategory(place, query.placeCategory) }
            .filter { (place, _) -> !query.likedOnly || isLiked(place, query.rawQuestion) }
            .map { (place, visits) ->
                PlaceResult(
                    term = query.placeTerms.joinToString(", "),
                    place = place,
                    visits = visits.sortedBy { it.timestamp }
                )
            }
            .sortedWith(
                compareByDescending<PlaceResult> { it.place.rating ?: 0 }
                    .thenByDescending { it.visits.maxOfOrNull { visit -> visit.timestamp } ?: 0L }
                    .thenBy { it.place.name }
            )

        return ChatRetrievedContext.PlaceCollection(
            dateRange = query.dateRange,
            regionTerms = query.placeTerms,
            places = filtered,
            category = query.placeCategory,
            likedOnly = query.likedOnly
        )
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

    private fun matchesGenericEntry(entry: DiaryEntry, query: ChatQuery): Boolean {
        val text = genericEntryText(entry)
        val actionMatches = query.actionTypeFilter?.let { filter ->
            entry.actionType == filter || matchesActionWords(text, filter)
        } ?: true
        val termsMatch = query.searchTerms.isEmpty() || matchesText(text, query.searchTerms)
        return actionMatches && termsMatch
    }

    private fun genericEntryScore(entry: DiaryEntry, query: ChatQuery): Int {
        val text = normalize(genericEntryText(entry))
        val actionScore = if (query.actionTypeFilter != null && entry.actionType == query.actionTypeFilter) 6 else 0
        val termScore = query.searchTerms.count { text.contains(normalize(it)) } * 2
        val reviewedScore = if (entry.wasReviewedByLLM) 1 else 0
        return actionScore + termScore + reviewedScore
    }

    private fun matchesText(rawText: String, terms: List<String>): Boolean {
        if (terms.isEmpty()) return false
        val normalizedText = normalize(rawText)
        val textStems = normalizedText
            .split(Regex("[^\\p{L}0-9]+"))
            .mapNotNull { tokenStem(it) }
            .toSet()
        val matched = terms.count { term ->
            val normalizedTerm = normalize(term)
            normalizedText.contains(normalizedTerm) ||
                tokenStem(normalizedTerm)?.let { stem -> stem in textStems } == true
        }
        val required = when {
            terms.size <= 1 -> 1
            terms.size <= 3 -> 2
            else -> 2
        }
        return matched >= required
    }

    private fun matchesActionWords(rawText: String, actionType: String): Boolean {
        val text = normalize(rawText)
        val words = when (actionType) {
            EntryActionType.BUY -> setOf("comprar", "compre", "compra", "comprado", "compras")
            EntryActionType.CALL -> setOf("llamar", "llame", "llamada", "llamadas")
            EntryActionType.SEND -> setOf("enviar", "envie", "mandar", "mande", "enviado")
            EntryActionType.TALK_TO -> setOf("hablar", "hable", "reunion", "reunir")
            EntryActionType.REVIEW -> setOf("revisar", "revise", "buscar", "busque")
            else -> emptySet()
        }
        return words.any(text::contains)
    }

    private fun genericEntryText(entry: DiaryEntry): String =
        listOfNotNull(
            entry.text,
            entry.cleanText,
            entry.correctedText,
            entry.keyword,
            entry.category,
            entry.actionType,
            entry.status,
            entry.priority
        ).joinToString(" ")

    private fun genericRecordingText(recording: Recording): String =
        listOfNotNull(recording.title, recording.summary, recording.keyPoints, recording.transcription)
            .joinToString(" ")

    private fun genericEventText(event: TimelineEvent): String =
        listOfNotNull(event.title, event.subtitle, event.dataJson, event.type, event.source)
            .joinToString(" ")

    private fun tokenStem(token: String): String? {
        val value = normalize(token)
            .trim()
            .takeIf { it.length >= 3 }
            ?: return null
        val stripped = STEM_SUFFIXES.firstOrNull { suffix ->
            value.length - suffix.length >= 3 && value.endsWith(suffix)
        }?.let { suffix -> value.removeSuffix(suffix) } ?: value
        return stripped.takeIf { it.length >= 3 }
    }

    private fun matchesCategory(place: Place, category: ChatPlaceCategory): Boolean {
        if (category == ChatPlaceCategory.ANY) return true
        val text = normalize(
            listOfNotNull(place.name, place.type, place.opinionText, place.opinionSummary)
                .joinToString(" ")
        )
        return when (category) {
            ChatPlaceCategory.RESTAURANT -> RESTAURANT_TERMS.any(text::contains)
            ChatPlaceCategory.ANY -> true
        }
    }

    private fun isLiked(place: Place, rawQuestion: String): Boolean {
        val wantsFiveStars = normalize(rawQuestion).let {
            it.contains("5 estrellas") || it.contains("cinco estrellas")
        }
        val rating = place.rating
        if (rating != null) return if (wantsFiveStars) rating >= 5 else rating >= 4
        val opinion = normalize(listOfNotNull(place.opinionText, place.opinionSummary).joinToString(" "))
        return POSITIVE_OPINION_TERMS.any(opinion::contains)
    }

    private fun matchesRegion(term: String, place: Place, visits: List<TimelineEvent>): Boolean {
        val normalizedTerms = expandRegionTerm(term)
        val searchable = normalize(
            buildString {
                append(place.name).append(' ')
                append(place.type.orEmpty()).append(' ')
                append(place.opinionText.orEmpty()).append(' ')
                append(place.opinionSummary.orEmpty()).append(' ')
                visits.forEach { event ->
                    append(event.title).append(' ')
                    append(event.subtitle.orEmpty()).append(' ')
                    append(event.dataJson.orEmpty()).append(' ')
                }
            }
        )
        return normalizedTerms.any { searchable.contains(it) }
    }

    private fun expandRegionTerm(term: String): Set<String> {
        val normalized = normalize(term)
        return COUNTRY_REGION_TERMS[normalized].orEmpty() + normalized
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase(Locale("es"))
            .trim()

    private companion object {
        val RESTAURANT_TERMS = setOf(
            "restaurant",
            "restaurante",
            "food",
            "meal",
            "bar",
            "cafe",
            "cafeteria",
            "tapas",
            "taberna"
        )

        val POSITIVE_OPINION_TERMS = setOf(
            "me gusto",
            "me encanto",
            "bueno",
            "buenisimo",
            "excelente",
            "recomendable",
            "volveria",
            "favorito"
        )

        val COUNTRY_REGION_TERMS = mapOf(
            "portugal" to setOf(
                "portugal",
                "porto",
                "oporto",
                "lisboa",
                "lisbon",
                "braga",
                "coimbra",
                "aveiro",
                "faro",
                "sintra",
                "cascais",
                "guimaraes",
                "guimaraes"
            ),
            "espana" to setOf(
                "espana",
                "spain",
                "madrid",
                "barcelona",
                "valencia",
                "sevilla",
                "bilbao",
                "zaragoza",
                "malaga"
            ),
            "españa" to setOf(
                "espana",
                "spain",
                "madrid",
                "barcelona",
                "valencia",
                "sevilla",
                "bilbao",
                "zaragoza",
                "malaga"
            )
        )

        const val GENERIC_RESULT_LIMIT = 40
        const val GENERIC_RECORDING_LIMIT = 8
        const val GENERIC_EVENT_LIMIT = 20

        val STEM_SUFFIXES = listOf(
            "aciones",
            "imientos",
            "amiento",
            "imientos",
            "acion",
            "ición",
            "mente",
            "ados",
            "adas",
            "idos",
            "idas",
            "ando",
            "iendo",
            "ado",
            "ada",
            "ido",
            "ida",
            "ar",
            "er",
            "ir",
            "es",
            "s"
        )
    }
}
