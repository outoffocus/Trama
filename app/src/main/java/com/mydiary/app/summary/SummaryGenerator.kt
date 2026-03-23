package com.mydiary.app.summary

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.mydiary.shared.model.DiaryEntry
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates daily summaries using Google Gemini (cloud).
 * Uses gemini-2.0-flash (free tier, fast, cheap).
 * Falls back to rule-based summary if API key is missing or call fails.
 *
 * API key: get one free at https://aistudio.google.com/apikey
 * Store it in Settings -> "Clave API Gemini"
 */
class SummaryGenerator(private val context: Context) {

    companion object {
        private const val TAG = "SummaryGenerator"
        private const val PREFS = "daily_summary"
        private const val KEY_API_KEY = "gemini_api_key"
        private val json = Json { ignoreUnknownKeys = true }
    }

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    /**
     * Generate a daily summary from the given entries.
     * Includes calendar events as context for the LLM.
     */
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

        val apiKey = getApiKey()
        if (apiKey.isNullOrBlank()) {
            Log.i(TAG, "No API key, using rule-based summary")
            return generateRuleBased(entries, dateStr)
        }

        return try {
            generateWithGemini(entries, dateStr, apiKey)
        } catch (e: Exception) {
            Log.w(TAG, "Gemini failed, using rule-based summary", e)
            generateRuleBased(entries, dateStr)
        }
    }

    private suspend fun generateWithGemini(
        entries: List<DiaryEntry>,
        dateStr: String,
        apiKey: String
    ): DailySummary {
        val prompt = buildPrompt(entries, dateStr)

        val model = GenerativeModel(
            modelName = "gemini-2.0-flash",
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.3f
                maxOutputTokens = 2048
            }
        )

        val response = model.generateContent(prompt)
        val responseText = response.text ?: throw Exception("Empty response from Gemini")

        Log.i(TAG, "Gemini response: ${responseText.take(200)}...")
        return parseGeminiResponse(responseText, dateStr, entries.size)
    }

    private fun buildPrompt(entries: List<DiaryEntry>, dateStr: String): String {
        val entriesText = entries.joinToString("\n") { entry ->
            val time = timeFormat.format(Date(entry.createdAt))
            val source = if (entry.source.name == "WATCH") " [reloj]" else ""
            "- $time$source \"${entry.text}\""
        }

        // Include calendar context so LLM avoids duplicates and considers existing schedule
        val calendarContext = buildCalendarContext()

        return """Eres un asistente personal. Analiza las notas de voz del usuario capturadas hoy ($dateStr) y genera un JSON con este formato exacto:

{
  "narrative": "Resumen de 2-3 frases del dia en español, conciso y util",
  "groups": [
    {"label": "Pendientes", "emoji": "📋", "items": ["Llamar al dentista", "Comprar leche"]},
    {"label": "Salud", "emoji": "🏥", "items": ["Cita con el medico mañana a las 8"]}
  ],
  "actions": [
    {"type": "CALENDAR_EVENT", "title": "titulo", "description": "detalle", "datetime": "2026-03-19T10:00"},
    {"type": "REMINDER", "title": "titulo", "description": "detalle", "datetime": "2026-03-19T08:00"},
    {"type": "TODO", "title": "tarea pendiente", "description": "detalle"},
    {"type": "MESSAGE", "title": "mensaje", "description": "contenido", "contact": "nombre"},
    {"type": "CALL", "title": "llamar a alguien", "contact": "nombre"},
    {"type": "NOTE", "title": "nota importante", "description": "detalle"}
  ]
}

Reglas CRITICAS para "groups":
- Agrupa TODAS las notas en categorias SEMANTICAS (no por la frase que las activo)
- MINIMIZA el numero de categorias: fusiona las que sean similares
- Por ejemplo: "tengo que", "debería", "hay que", "necesito" → TODO ES "Pendientes"
- Por ejemplo: "cita médico", "me duele", "farmacia" → TODO ES "Salud"
- Las notas pueden ser dichas por el usuario para sí mismo o para un "nosotros" (grupo, familia, equipo)
- Cada categoria debe tener un label corto (1-2 palabras) y un emoji representativo
- Maximo 5-6 categorias. Si solo hay 1-2 temas, usa 1-2 categorias
- Los items deben ser frases cortas que resuman cada nota, NO copiar el texto entero
- NUNCA repitas una misma nota en dos categorias distintas

Reglas para "actions":
- Solo incluye acciones que se deduzcan claramente de las notas
- Detecta fechas relativas: "mañana" = dia siguiente a $dateStr, "esta semana" = esta semana, etc
- Si mencionan una persona, usa el campo "contact"
- El datetime debe ser ISO 8601 (YYYY-MM-DDTHH:mm)
- NO sugieras crear eventos de calendario que ya existan (revisa los eventos listados abajo)
- Si una nota menciona algo que coincide con un evento existente, menciona la conexion en el narrative

- Responde SOLO con el JSON, sin texto adicional
$calendarContext
Notas del dia:
$entriesText"""
    }

    /**
     * Build calendar context string with today's and tomorrow's events.
     */
    private fun buildCalendarContext(): String {
        if (!CalendarHelper.hasCalendarPermission(context)) {
            return "\n(Sin acceso al calendario)"
        }

        val (todayEvents, tomorrowEvents) = CalendarHelper.getUpcomingEvents(context)

        val tomorrow = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
        val tomorrowStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(tomorrow.time)

        return CalendarHelper.formatEventsForPrompt(todayEvents, tomorrowEvents, tomorrowStr)
    }

    private fun parseGeminiResponse(response: String, dateStr: String, entryCount: Int): DailySummary {
        // Extract JSON from response (might have markdown code blocks)
        val jsonStr = response
            .replace("```json", "").replace("```", "")
            .trim()

        return try {
            val parsed = json.decodeFromString<GeminiResponse>(jsonStr)
            DailySummary(
                date = dateStr,
                narrative = parsed.narrative,
                groups = parsed.groups?.map { it.toEntryGroup() } ?: emptyList(),
                actions = parsed.actions.map { it.toSuggestedAction() },
                entryCount = entryCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Gemini JSON: $jsonStr", e)
            DailySummary(
                date = dateStr,
                narrative = response.take(500),
                groups = emptyList(),
                actions = emptyList(),
                entryCount = entryCount
            )
        }
    }

    /**
     * Simple rule-based summary when Gemini is not available.
     * Groups entries by semantic similarity using keyword patterns.
     */
    private fun generateRuleBased(entries: List<DiaryEntry>, dateStr: String): DailySummary {
        // Semantic grouping by text content analysis
        val groupMap = mutableMapOf<String, MutableList<String>>()
        val groupEmojis = mutableMapOf<String, String>()

        for (entry in entries) {
            val text = entry.text.lowercase()
            val summary = entry.text.take(60).let { if (entry.text.length > 60) "$it..." else it }

            val (label, emoji) = categorizeEntry(text)
            groupMap.getOrPut(label) { mutableListOf() }.add(summary)
            groupEmojis[label] = emoji
        }

        val groups = groupMap.map { (label, items) ->
            EntryGroup(label = label, emoji = groupEmojis[label] ?: "📝", items = items)
        }

        val narrative = buildString {
            append("Hoy capturaste ${entries.size} entradas")
            if (groups.size == 1) {
                append(" sobre ${groups.first().label.lowercase()}.")
            } else {
                append(": ")
                append(groups.joinToString(", ") { "${it.items.size} de ${it.label.lowercase()}" })
                append(".")
            }
        }

        val actions = mutableListOf<SuggestedAction>()
        var hasCalendarEvent = false

        for (entry in entries) {
            val text = entry.text.lowercase()

            // Detect calendar events
            if (text.contains("cita") || text.contains("médico") || text.contains("reunión") ||
                (text.contains("a las ") && (text.contains("mañana") || text.contains("lunes") ||
                    text.contains("martes") || text.contains("miércoles") || text.contains("jueves") ||
                    text.contains("viernes")))) {
                val datetime = extractDateTimeHint(text, dateStr)
                actions.add(
                    SuggestedAction(
                        type = ActionType.CALENDAR_EVENT,
                        title = entry.text.take(80),
                        description = "Capturado a las ${timeFormat.format(Date(entry.createdAt))}",
                        datetime = datetime
                    )
                )
                hasCalendarEvent = true
            }

            // Detect calls
            if (text.contains("llamar") || text.contains("llama a") || text.contains("call")) {
                val contact = extractAfterWord(entry.text, "llama a", "llamar a", "llamar", "call", "a")
                actions.add(
                    SuggestedAction(
                        type = ActionType.CALL,
                        title = "Llamar a $contact",
                        contact = contact
                    )
                )
            }

            // Detect reminders
            if ((text.contains("mañana") || text.contains("tomorrow") ||
                    text.contains("recordar") || text.contains("no olvidar")) && !hasCalendarEvent) {
                actions.add(
                    SuggestedAction(
                        type = ActionType.REMINDER,
                        title = entry.text.take(80),
                        description = "Mencionado como pendiente"
                    )
                )
            }

            // Detect tasks
            if (text.contains("hay que") || text.contains("tengo que") ||
                text.contains("necesito") || text.contains("pendiente") ||
                text.contains("debería") || text.contains("falta")) {
                actions.add(
                    SuggestedAction(
                        type = ActionType.TODO,
                        title = entry.text.take(80),
                        description = "Capturado a las ${timeFormat.format(Date(entry.createdAt))}"
                    )
                )
            }

            // Detect messages
            if (text.contains("escríbele") || text.contains("dile a") ||
                text.contains("mándale") || text.contains("envíale")) {
                val contact = extractAfterWord(entry.text, "escríbele a", "dile a", "mándale a", "envíale a")
                actions.add(
                    SuggestedAction(
                        type = ActionType.MESSAGE,
                        title = "Mensaje a $contact",
                        description = entry.text.take(80),
                        contact = contact
                    )
                )
            }
        }

        return DailySummary(
            date = dateStr,
            narrative = narrative,
            groups = groups,
            actions = actions.distinctBy { "${it.type}:${it.title}" },
            entryCount = entries.size
        )
    }

    /**
     * Categorize an entry into a semantic group based on text content.
     * Returns Pair(label, emoji).
     */
    private fun categorizeEntry(text: String): Pair<String, String> {
        return when {
            // Health
            text.contains("médico") || text.contains("doctor") || text.contains("hospital") ||
            text.contains("farmacia") || text.contains("medicina") || text.contains("duele") ||
            text.contains("salud") || text.contains("enfermo") -> "Salud" to "🏥"

            // Work / Professional
            text.contains("reunión") || text.contains("trabajo") || text.contains("oficina") ||
            text.contains("proyecto") || text.contains("cliente") || text.contains("jefe") ||
            text.contains("presentación") || text.contains("deadline") -> "Trabajo" to "💼"

            // Tasks / Pending
            text.contains("tengo que") || text.contains("hay que") || text.contains("debería") ||
            text.contains("necesito") || text.contains("pendiente") || text.contains("falta") ||
            text.contains("comprar") || text.contains("hacer") -> "Pendientes" to "📋"

            // Appointments / Calendar
            text.contains("cita") || text.contains("a las") || text.contains("mañana") ||
            text.contains("evento") || text.contains("reserva") -> "Agenda" to "📅"

            // People / Communication
            text.contains("llamar") || text.contains("llama") || text.contains("hablar con") ||
            text.contains("escríbele") || text.contains("dile a") || text.contains("contactar") ->
                "Contactos" to "👤"

            // Ideas
            text.contains("idea") || text.contains("podríamos") || text.contains("se me ocurre") ||
            text.contains("qué tal si") -> "Ideas" to "💡"

            // Personal / Family
            text.contains("casa") || text.contains("familia") || text.contains("niños") ||
            text.contains("colegio") || text.contains("hijos") -> "Personal" to "🏠"

            // Default
            else -> "Notas" to "📝"
        }
    }

    /**
     * Try to extract a datetime from text hints like "mañana a las 8", "a las 10".
     * Returns ISO format or null.
     */
    private fun extractDateTimeHint(text: String, dateStr: String): String? {
        val timeRegex = Regex("""a las (\d{1,2})(?::(\d{2}))?""")
        val match = timeRegex.find(text) ?: return null

        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0

        val cal = java.util.Calendar.getInstance()
        if (text.contains("mañana")) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }

        val datePart = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        return String.format(Locale.getDefault(), "%sT%02d:%02d", datePart, hour, minute)
    }

    private fun extractAfterWord(text: String, vararg keywords: String): String {
        for (keyword in keywords) {
            val idx = text.lowercase().indexOf(keyword)
            if (idx >= 0) {
                val after = text.substring(idx + keyword.length).trim()
                return after.split(" ").take(3).joinToString(" ").trim(',', '.', '!', '?')
            }
        }
        return ""
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
