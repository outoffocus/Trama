package com.trama.app.chat

import android.content.Context
import android.util.Log
import com.trama.app.summary.GemmaClient
import com.trama.shared.data.DiaryRepository
import java.text.Normalizer
import java.util.Locale

/**
 * Multi-turn diary assistant.
 *
 * Every factual answer starts with deterministic local retrieval. If the local model is
 * available, it may improve the wording using only those retrieved facts. Queries without
 * enough evidence return guidance instead of an ungrounded generated answer.
 *
 * Call clearHistory() to start a fresh conversation (also resets context cache).
 */
class DiaryAssistant(
    private val context: Context,
    repository: DiaryRepository
) {

    private val queryInterpreter = ChatQueryInterpreter()
    private val contextRetriever = ChatContextRetriever(repository)
    private val answerComposer = ChatAnswerComposer()
    private val factsFormatter = ChatFactsFormatter()

    // All inference and context retrieval stays on-device.

    private var lastDeterministicQuery: ChatQuery? = null

    // ── Public API ────────────────────────────────────────────────────────────

    suspend fun send(userMessage: String): String = sendReply(userMessage).text

    suspend fun sendReply(userMessage: String): DiaryAssistantReply {
        val deterministic = tryDeterministicAnswer(userMessage)
        if (deterministic != null) return deterministic
        return DiaryAssistantReply(
            "No he encontrado datos suficientes para responder con seguridad. Prueba indicando qué buscas y, si aplica, una fecha o un lugar."
        )
    }

    fun clearHistory() {
        lastDeterministicQuery = null
    }

    private suspend fun tryDeterministicAnswer(userMessage: String): DiaryAssistantReply? {
        val query = resolveFollowUpQuery(queryInterpreter.interpret(userMessage), userMessage)
        if (query.intent == ChatIntent.UNKNOWN) return null
        rememberQueryScope(query)

        val retrieved = contextRetriever.retrieve(query) ?: return null
        val factualAnswer = answerComposer.compose(query, retrieved) ?: return null
        lastDeterministicQuery = query
        return DiaryAssistantReply(
            text = tryGroundedLocalAnswer(query, retrieved, factualAnswer) ?: factualAnswer,
            sources = DiaryAssistantSources.from(query, retrieved)
        )
    }

    private fun rememberQueryScope(query: ChatQuery) {
        if (query.dateRange != null || query.placeTerms.isNotEmpty()) {
            lastDeterministicQuery = query
        }
    }

    private fun resolveFollowUpQuery(query: ChatQuery, userMessage: String): ChatQuery {
        val previous = lastDeterministicQuery ?: return query
        val normalized = normalize(userMessage)

        val hasExplicitScope = query.dateRange != null || query.placeTerms.isNotEmpty()
        val inheritedDateRange = query.dateRange ?: previous.dateRange
        val inheritedPlaceTerms = if (query.placeTerms.isNotEmpty()) query.placeTerms else previous.placeTerms

        if (query.intent == ChatIntent.LIKED_PLACES && !hasExplicitScope) {
            return query.copy(
                dateRange = inheritedDateRange,
                placeTerms = inheritedPlaceTerms
            )
        }

        if (
            query.intent == ChatIntent.UNKNOWN &&
            listOf("ciudades", "lugares", "sitios", "donde", "dónde").any(normalized::contains) &&
            (inheritedDateRange != null || inheritedPlaceTerms.isNotEmpty())
        ) {
            return ChatQuery(
                rawQuestion = userMessage.trim(),
                intent = ChatIntent.PLACE_LIST,
                dateRange = inheritedDateRange,
                placeTerms = inheritedPlaceTerms,
                placeCategory = query.placeCategory
            )
        }

        if (
            query.intent == ChatIntent.UNKNOWN &&
            listOf("restaurantes", "restaurante", "me gustaron", "favoritos", "5 estrellas").any(normalized::contains)
        ) {
            return ChatQuery(
                rawQuestion = userMessage.trim(),
                intent = ChatIntent.LIKED_PLACES,
                dateRange = inheritedDateRange,
                placeTerms = inheritedPlaceTerms,
                placeCategory = if (normalized.contains("restaurante")) {
                    ChatPlaceCategory.RESTAURANT
                } else {
                    ChatPlaceCategory.ANY
                },
                likedOnly = true
            )
        }

        return query
    }

    private suspend fun tryGroundedLocalAnswer(
        query: ChatQuery,
        retrieved: ChatRetrievedContext,
        factualAnswer: String
    ): String? {
        if (!GemmaClient.isModelAvailable(context)) return null

        val facts = factsFormatter.format(query, retrieved)
        val prompt = buildString {
            appendLine("Responde en español usando SOLO los hechos proporcionados.")
            appendLine("No inventes lugares, tiempos, fechas, opiniones ni tareas.")
            appendLine("Si los hechos no bastan, dilo claramente.")
            appendLine("Puedes inferir una conclusion suave como si un sitio gustó o no SOLO si hay rating u opinion.")
            appendLine()
            appendLine("[PREGUNTA]")
            appendLine(query.rawQuestion)
            appendLine()
            appendLine("[HECHOS]")
            appendLine(facts)
            appendLine()
            appendLine("[RESPUESTA FACTUAL BASE]")
            appendLine(factualAnswer)
            appendLine()
            append("Respuesta final:")
        }

        return try {
            GemmaClient.generate(
                context = context,
                prompt = prompt,
                maxTokens = 256,
                systemInstruction = "Eres un asistente personal riguroso. Resume y redacta usando solo hechos verificados."
            )?.trim()
                ?.takeIf { it.isNotBlank() }
        } catch (t: Throwable) {
            Log.w(TAG, "Grounded local answer failed: ${t.javaClass.simpleName}: ${t.message}")
            null
        }
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase(Locale("es"))

    companion object {
        private const val TAG = "DiaryAssistant"
    }
}

data class DiaryAssistantReply(
    val text: String,
    val sources: List<DiaryAssistantSource> = emptyList()
)

sealed interface DiaryAssistantSource {
    val id: Long
    val label: String

    data class Entry(override val id: Long, override val label: String) : DiaryAssistantSource
    data class Place(override val id: Long, override val label: String) : DiaryAssistantSource
    data class Recording(override val id: Long, override val label: String) : DiaryAssistantSource
}

internal object DiaryAssistantSources {
    fun from(query: ChatQuery, context: ChatRetrievedContext): List<DiaryAssistantSource> {
        val sources: List<DiaryAssistantSource> = when (context) {
            is ChatRetrievedContext.Day -> buildList<DiaryAssistantSource> {
                when (query.intent) {
                    ChatIntent.COMPLETED_TASKS -> context.completedEntries.forEach {
                        add(DiaryAssistantSource.Entry(it.id, it.displayText))
                    }
                    ChatIntent.DAY_PLACES, ChatIntent.FIRST_PLACE, ChatIntent.LAST_PLACE -> Unit
                    else -> {
                        context.entries.forEach { add(DiaryAssistantSource.Entry(it.id, it.displayText)) }
                        context.recordings.forEach {
                            add(DiaryAssistantSource.Recording(it.id, it.title ?: "Grabación"))
                        }
                    }
                }
                if (query.intent != ChatIntent.COMPLETED_TASKS) {
                    context.timelineEvents.mapNotNull { it.placeId }.forEach { placeId ->
                        context.placesById[placeId]?.let { add(DiaryAssistantSource.Place(it.id, it.name)) }
                    }
                }
            }
            is ChatRetrievedContext.PlaceLookup -> context.results.map {
                DiaryAssistantSource.Place(it.place.id, it.place.name)
            }
            is ChatRetrievedContext.PlaceCollection -> context.places.map {
                DiaryAssistantSource.Place(it.place.id, it.place.name)
            }
            is ChatRetrievedContext.GenericFacts -> buildList<DiaryAssistantSource> {
                context.entries.forEach { add(DiaryAssistantSource.Entry(it.id, it.displayText)) }
                context.recordings.forEach {
                    add(DiaryAssistantSource.Recording(it.id, it.title ?: "Grabación"))
                }
                context.timelineEvents.mapNotNull { it.placeId }.forEach { placeId ->
                    context.placesById[placeId]?.let { add(DiaryAssistantSource.Place(it.id, it.name)) }
                }
            }
        }
        return sources.distinctBy { source ->
            val kind = when (source) {
                is DiaryAssistantSource.Entry -> "entry"
                is DiaryAssistantSource.Place -> "place"
                is DiaryAssistantSource.Recording -> "recording"
            }
            "$kind:${source.id}"
        }.take(MAX_SOURCES)
    }

    private const val MAX_SOURCES = 6
}
