package com.trama.app.summary

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.trama.app.GeminiConfig
import com.google.ai.client.generativeai.type.generationConfig
import com.trama.shared.model.DiaryEntry
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates daily summaries using LLMs.
 * Priority: Gemini Cloud → local on-device model.
 * Both use the same structured JSON prompt. If no LLM available, returns a minimal summary.
 */
class SummaryGenerator(private val context: Context) {

    companion object {
        private const val TAG = "SummaryGenerator"
        private const val PREFS = "daily_summary"
        private const val KEY_API_KEY = "gemini_api_key"
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    }

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    suspend fun generate(entries: List<DiaryEntry>, dateStr: String): DailySummary {
        if (entries.isEmpty()) {
            return DailySummary(
                date = dateStr,
                narrative = "No hubo entradas hoy.",
                groups = emptyList(),
                actions = emptyList(),
                entryCount = 0
            )
        }

        // Try Gemini Cloud
        val apiKey = getApiKey()
        if (!apiKey.isNullOrBlank()) {
            try {
                return generateWithCloud(entries, dateStr, apiKey)
            } catch (e: Exception) {
                Log.w(TAG, "Cloud failed, trying local model", e)
            }
        }

        // Try local on-device model (same prompt & format as Cloud)
        if (GemmaClient.isModelAvailable(context)) {
            try {
                val result = generateWithLocalModel(entries, dateStr)
                if (result != null) return result
            } catch (e: Exception) {
                Log.w(TAG, "Local model failed", e)
            }
        }

        // No LLM — minimal summary
        return DailySummary(
            date = dateStr,
            narrative = "Se capturaron ${entries.size} notas hoy. Descarga el modelo local o configura la API para generar resúmenes.",
            groups = emptyList(),
            actions = emptyList(),
            entryCount = entries.size
        )
    }

    private suspend fun generateWithCloud(
        entries: List<DiaryEntry>,
        dateStr: String,
        apiKey: String
    ): DailySummary {
        val prompt = buildPrompt(entries, dateStr)

        val model = GenerativeModel(
            modelName = GeminiConfig.MODEL_NAME,
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.3f
                maxOutputTokens = 2048
            }
        )

        val response = model.generateContent(prompt)
        val responseText = response.text ?: throw Exception("Empty response from Gemini")

        Log.i(TAG, "Cloud response: ${responseText.take(200)}...")
        return parseResponse(responseText, dateStr, entries.size, entries)
    }

    private suspend fun generateWithLocalModel(
        entries: List<DiaryEntry>,
        dateStr: String
    ): DailySummary? {
        // Use the same structured prompt as Cloud
        val prompt = buildPrompt(entries, dateStr)
        val responseText = GemmaClient.generate(context, prompt, maxTokens = 2048, responsePrefix = "{") ?: return null
        Log.i(TAG, "Local model response: ${responseText.take(200)}...")

        // Try parsing as JSON (same format as Cloud)
        return try {
            parseResponse(responseText, dateStr, entries.size, entries)
        } catch (e: Exception) {
            // Fallback: use response as narrative with simple grouping
            Log.w(TAG, "Local model JSON parse failed, using text fallback", e)
            val cleanText = responseText.trimEnd()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            DailySummary(
                date = dateStr,
                narrative = cleanText.take(500),
                groups = buildSimpleGroups(entries),
                actions = emptyList(),
                entryCount = entries.size
            )
        }
    }

    /** Groups entries as a simple fallback when JSON parsing fails. */
    private fun buildSimpleGroups(entries: List<DiaryEntry>): List<EntryGroup> {
        val items = entries.map { it.displayText.take(80) }
        return if (items.isNotEmpty()) {
            listOf(EntryGroup(label = "Notas del dia", emoji = "\uD83D\uDCDD", items = items))
        } else emptyList()
    }

    private fun buildEntriesText(entries: List<DiaryEntry>): String =
        entries.joinToString("\n") { entry ->
            val time = timeFormat.format(Date(entry.createdAt))
            val source = if (entry.source.name == "WATCH") " [reloj]" else ""
            val status = if (entry.status == "COMPLETED") " [COMPLETADA]" else ""
            val displayText = entry.displayText
            val age = if (entry.createdAt < System.currentTimeMillis() - 86400000) {
                val days = ((System.currentTimeMillis() - entry.createdAt) / 86400000).toInt()
                " (hace ${days}d)"
            } else ""
            "- $time$source$status$age \"$displayText\""
        }

    /** Prompt for Gemini Cloud (powerful model, understands structure from description). */
    private fun buildPrompt(entries: List<DiaryEntry>, dateStr: String): String {
        val entriesText = buildEntriesText(entries)
        val calendarContext = buildCalendarContext()

        return """Eres un asistente personal. Analiza las notas de voz del usuario capturadas hoy ($dateStr).

Responde UNICAMENTE con un JSON valido, sin texto antes ni despues, con esta estructura:
- "narrative": string con resumen de 2-3 frases del dia en español
- "groups": array de objetos con "label" (string), "emoji" (string), "items" (array de strings)
- "actions": array de objetos con "type" (CALENDAR_EVENT|REMINDER|TODO|MESSAGE|CALL|NOTE), "title" (string), y opcionalmente "description", "datetime" (ISO 8601), "contact"

Reglas generales:
- No inventes hechos, fechas, personas ni acciones
- Si algo es ambiguo, elige la interpretacion mas conservadora
- Todo debe salir de las notas o del contexto de calendario proporcionado

Reglas para "groups":
- Agrupa TODAS las notas en categorias SEMANTICAS. Maximo 5-6 categorias
- Los items deben ser frases cortas que resuman cada nota
- NUNCA repitas una misma nota en dos categorias
- Usa etiquetas utiles y naturales en español

Reglas para "actions":
- Solo incluye acciones que se deduzcan claramente de las notas
- Detecta fechas relativas: "mañana" = dia siguiente a $dateStr
- Si mencionan una persona, usa "contact"
- "title" debe ser breve y accionable
- "description" solo si aporta contexto real
- Si no puedes inferir una fecha con claridad, omite "datetime"
- NO inventes acciones que no esten en las notas
- No dupliques acciones equivalentes
$calendarContext
Notas del dia:
$entriesText"""
    }


    private fun buildCalendarContext(): String {
        if (!CalendarHelper.hasCalendarPermission(context)) {
            return "\n(Sin acceso al calendario)"
        }

        val (todayEvents, tomorrowEvents) = CalendarHelper.getUpcomingEvents(context)
        val tomorrow = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
        val tomorrowStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(tomorrow.time)

        return CalendarHelper.formatEventsForPrompt(todayEvents, tomorrowEvents, tomorrowStr)
    }

    private fun parseResponse(
        response: String, dateStr: String, entryCount: Int,
        entries: List<DiaryEntry>
    ): DailySummary {
        val jsonStr = JsonRepair.extractAndRepair(response)

        return try {
            val parsed = json.decodeFromString<GeminiResponse>(jsonStr)
            val llmActions = parsed.actions.map { geminiAction ->
                val action = geminiAction.toSuggestedAction()
                val matched = matchActionToEntries(action.title, entries)
                val earliestCaptured = matched.firstOrNull()?.let { id ->
                    entries.firstOrNull { it.id == id }?.createdAt
                }
                action.copy(entryIds = matched, capturedAt = earliestCaptured)
            }

            // Ensure every pending entry is represented as an action.
            // The LLM may skip entries it doesn't consider "actionable".
            val coveredEntryIds = llmActions.flatMap { it.entryIds }.toSet()
            val missedEntries = entries.filter { it.id !in coveredEntryIds }
            val missedActions = missedEntries.map { entry ->
                SuggestedAction(
                    type = inferActionType(entry.displayText),
                    title = (entry.cleanText ?: entry.displayText).take(100),
                    entryIds = listOf(entry.id),
                    capturedAt = entry.createdAt
                )
            }
            if (missedActions.isNotEmpty()) {
                Log.i(TAG, "${missedActions.size} entries not covered by LLM, adding as actions")
            }

            DailySummary(
                date = dateStr,
                narrative = parsed.narrative,
                groups = parsed.groups?.map { it.toEntryGroup() } ?: emptyList(),
                actions = llmActions + missedActions,
                entryCount = entryCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON: $jsonStr", e)
            DailySummary(
                date = dateStr,
                narrative = response.take(500),
                groups = emptyList(),
                actions = emptyList(),
                entryCount = entryCount
            )
        }
    }

    private fun matchActionToEntries(actionTitle: String, entries: List<DiaryEntry>): List<Long> {
        val actionWords = actionTitle.lowercase().split("\\s+".toRegex()).filter { it.length > 2 }.toSet()
        if (actionWords.isEmpty()) return emptyList()

        return entries
            .map { entry ->
                val entryWords = entry.displayText.lowercase().split("\\s+".toRegex()).filter { it.length > 2 }.toSet()
                val overlap = actionWords.intersect(entryWords).size.toFloat() / actionWords.size
                entry.id to overlap
            }
            .filter { it.second >= 0.4f }
            .sortedByDescending { it.second }
            .take(3)
            .map { it.first }
    }

    /** Keyword-based action type for entries the LLM skipped. */
    private fun inferActionType(text: String): ActionType {
        val lower = text.lowercase()
        return when {
            lower.contains("llamar") || lower.contains("llama a") -> ActionType.CALL
            lower.contains("enviar") || lower.contains("mandar") || lower.contains("mensaje") -> ActionType.MESSAGE
            lower.contains("reunión") || lower.contains("cita") || lower.contains("evento") -> ActionType.CALENDAR_EVENT
            lower.contains("recordar") || lower.contains("no olvidar") -> ActionType.REMINDER
            lower.contains("comprar") || lower.contains("revisar") || lower.contains("mirar")
                || lower.contains("hacer") || lower.contains("pagar") -> ActionType.TODO
            else -> ActionType.TODO
        }
    }

    private fun getApiKey(): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_API_KEY, null)
    }
}

@kotlinx.serialization.Serializable
private data class GeminiResponse(
    val narrative: String,
    val groups: List<GeminiGroup>? = null,
    val actions: List<GeminiAction>
)

@kotlinx.serialization.Serializable
private data class GeminiGroup(
    val label: String,
    val emoji: String = "📝",
    val items: List<String>
) {
    fun toEntryGroup(): EntryGroup = EntryGroup(
        label = label,
        emoji = emoji,
        items = items
    )
}

@kotlinx.serialization.Serializable
private data class GeminiAction(
    val type: String,
    val title: String,
    val description: String = "",
    val datetime: String? = null,
    val contact: String? = null
) {
    fun toSuggestedAction(): SuggestedAction = SuggestedAction(
        type = try { ActionType.valueOf(type) } catch (_: Exception) { ActionType.NOTE },
        title = title,
        description = description,
        datetime = datetime,
        contact = contact
    )
}
