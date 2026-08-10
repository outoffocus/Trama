package com.trama.app.chat

import android.content.Context
import android.util.Log
import com.trama.app.summary.GemmaClient
import com.trama.shared.data.DiaryRepository
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Multi-turn diary assistant.
 *
 * Priority chain per message:
 *   1. Deterministic local retrieval for grounded questions.
 *   2. Gemma local   — if model is downloaded & enabled. Multi-turn is simulated by
 *                       appending the full conversation history to the prompt on every call.
 *   3. No model      — returns a user-facing error string.
 *
 * Call clearHistory() to start a fresh conversation (also resets context cache).
 */
class DiaryAssistant(
    private val context: Context,
    private val contextBuilder: DiaryContextBuilder,
    repository: DiaryRepository
) {

    private val queryInterpreter = ChatQueryInterpreter()
    private val contextRetriever = ChatContextRetriever(repository)
    private val answerComposer = ChatAnswerComposer()
    private val factsFormatter = ChatFactsFormatter()

    // All inference and context retrieval stays on-device.

    // ── Gemini state (lazy session, persists for the conversation) ────────────
    // ── Gemma state (manual history for simulated multi-turn) ────────────────
    // Each entry is Pair(role, text): role is "Usuario" or "Asistente"
    private val localHistory = mutableListOf<Pair<String, String>>()
    private var lastDeterministicQuery: ChatQuery? = null

    // ── Public API ────────────────────────────────────────────────────────────

    suspend fun send(userMessage: String): String {
        val deterministic = tryDeterministicAnswer(userMessage)
        if (deterministic != null) return deterministic

        // Try Gemma local model.
        if (GemmaClient.isModelAvailable(context)) {
            try {
                val reply = sendWithLocalModel(userMessage)
                if (reply != null) return reply
            } catch (t: Throwable) {
                // Catch Throwable (not just Exception) — LiteRT-LM can throw native errors
                Log.w(TAG, "Local model failed: ${t.javaClass.simpleName}: ${t.message}")
            }
        }

        return "⚠️ Descarga el modelo local en Ajustes → IA local para usar el asistente."
    }

    fun clearHistory() {
        localHistory.clear()
        lastDeterministicQuery = null
        contextBuilder.invalidate()
    }

    private suspend fun tryDeterministicAnswer(userMessage: String): String? {
        val query = resolveFollowUpQuery(queryInterpreter.interpret(userMessage), userMessage)
        if (query.intent == ChatIntent.UNKNOWN) return null
        rememberQueryScope(query)

        val retrieved = contextRetriever.retrieve(query) ?: return null
        val factualAnswer = answerComposer.compose(query, retrieved) ?: return null
        lastDeterministicQuery = query
        return tryGroundedLocalAnswer(query, retrieved, factualAnswer) ?: factualAnswer
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

    // ── Gemini Cloud ──────────────────────────────────────────────────────────

    // ── Gemma local (simulated multi-turn) ────────────────────────────────────

    private suspend fun sendWithLocalModel(userMessage: String): String? {
        // Use size-limited context to avoid overflowing the LiteRT-LM KV cache
        val compactContext = contextBuilder.getContextForLocalModel()
        val today = todayString()

        // System instruction: diary context + persona.
        // Passed separately so GemmaClient can inject it as a proper system turn
        // (LiteRT-LM: ConversationConfig.systemInstruction; MediaPipe: <start_of_turn>system).
        val systemInstruction = buildLocalSystemPrompt(compactContext, today)

        // Prompt: only conversation history + current question (no context duplication)
        val prompt = buildString {
            val historyWindow = localHistory.takeLast(MAX_LOCAL_HISTORY_MESSAGES)
            if (historyWindow.isNotEmpty()) {
                historyWindow.forEach { (role, text) ->
                    appendLine("$role: $text")
                }
            }
            appendLine("Usuario: $userMessage")
            append("Asistente:")
        }

        val reply = GemmaClient.generate(
            context = context,
            prompt = prompt,
            maxTokens = 1024,
            systemInstruction = systemInstruction
        ) ?: return null

        // Store exchange in local history
        localHistory.add("Usuario" to userMessage)
        localHistory.add("Asistente" to reply)

        return reply
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildLocalSystemPrompt(diaryContext: String, today: String) = buildString {
        appendLine("Eres el asistente personal local de Trama.")
        appendLine("El contexto incluido es una vista compacta y puede estar truncado por límites del modelo on-device.")
        appendLine("Para preguntas factuales, usa solo hechos presentes en el contexto o en los hechos recuperados por la app.")
        appendLine("Si no tienes información suficiente, dilo con claridad; no inventes datos.")
        appendLine("Responde siempre en español, de forma directa y concisa.")
        appendLine("Fecha actual: $today.")
        appendLine()
        append(diaryContext)
    }

    private fun todayString(): String =
        SimpleDateFormat("EEEE d 'de' MMMM yyyy", Locale("es"))
            .format(Date())
            .replaceFirstChar { it.uppercase() }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase(Locale("es"))

    companion object {
        private const val TAG = "DiaryAssistant"
        /** Max conversation messages kept in local history (Gemma 4 E4B: 128K token window) */
        private const val MAX_LOCAL_HISTORY_MESSAGES = 60 // 30 user + 30 assistant
    }
}
