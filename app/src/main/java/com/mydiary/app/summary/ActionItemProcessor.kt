package com.mydiary.app.summary

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.mydiary.shared.data.DiaryRepository
import com.mydiary.shared.model.EntryActionType
import com.mydiary.shared.model.EntryPriority
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Processes a captured diary entry through Gemini to extract:
 * - cleanText: a clean, actionable summary
 * - actionType: CALL, BUY, SEND, EVENT, REVIEW, TALK_TO, GENERIC
 * - dueDate: extracted date if mentioned
 * - priority: LOW, NORMAL, HIGH, URGENT
 *
 * Runs async after the entry is saved (fire-and-forget).
 * Updates the entry in the DB once processing completes.
 */
class ActionItemProcessor(private val context: Context) {

    companion object {
        private const val TAG = "ActionItemProcessor"
        private val json = Json { ignoreUnknownKeys = true }
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }

    /**
     * Process a saved entry: extract action metadata via Gemini and update DB.
     * Should be called from a coroutine on Dispatchers.IO.
     */
    suspend fun process(entryId: Long, text: String, repository: DiaryRepository) {
        // Try LLM first, fall back to rules
        val result = try {
            val apiKey = getApiKey()
            if (!apiKey.isNullOrBlank()) {
                processWithGemini(text, apiKey)
            } else {
                processWithRules(text)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini processing failed, using rules", e)
            processWithRules(text)
        }

        repository.updateAIProcessing(
            id = entryId,
            cleanText = result.cleanText,
            actionType = result.actionType,
            dueDate = result.dueDate,
            priority = result.priority,
            confidence = result.confidence
        )
        Log.i(TAG, "Processed entry $entryId: '${result.cleanText}' [${result.actionType}]")
    }

    private suspend fun processWithGemini(text: String, apiKey: String): ProcessingResult {
        val today = dateFormat.format(Calendar.getInstance().time)
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tomorrowStr = dateFormat.format(tomorrow.time)

        val prompt = """Analiza esta nota de voz capturada y responde SOLO con JSON:
{
  "cleanText": "texto limpio y corregido, sin el trigger inicial",
  "actionType": "CALL|BUY|SEND|EVENT|REVIEW|TALK_TO|GENERIC",
  "dueDate": "YYYY-MM-DD o null",
  "priority": "LOW|NORMAL|HIGH|URGENT",
  "confidence": 0.0-1.0
}

Reglas:
- cleanText: elimina frases activadoras ("tengo que", "hay que", "debería", "recordar") y deja solo la acción limpia. Capitaliza. Corrige errores de transcripción.
  Ejemplo: "tengo que llamar al dentista mañana" → "Llamar al dentista"
  Ejemplo: "debería comprar leche y pan" → "Comprar leche y pan"
  Ejemplo: "recordar ir al psiquiatra el miércoles" → "Ir al psiquiatra el miércoles"
- actionType: detecta el tipo de acción:
  CALL = llamar, telefonear
  BUY = comprar, adquirir
  SEND = enviar mensaje, email, escribir a
  EVENT = cita, reunión, evento, reserva
  REVIEW = revisar, mirar, comprobar, buscar
  TALK_TO = hablar con, decirle a, comentar con
  GENERIC = cualquier otra tarea
- dueDate: si menciona fecha, conviértela. Hoy=$today, mañana=$tomorrowStr. Si dice "el miércoles", "el lunes" etc, calcula la fecha. Si no hay fecha, null.
- priority: URGENT si dice "urgente", "ya", "ahora", "cuanto antes". HIGH si dice "importante", "no olvidar". LOW si dice "cuando pueda", "algún día". NORMAL en el resto.

Nota de voz: "$text"
"""

        val model = GenerativeModel(
            modelName = "gemini-2.0-flash",
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.1f
                maxOutputTokens = 256
            }
        )

        val response = model.generateContent(prompt)
        val responseText = response.text
            ?.replace("```json", "")?.replace("```", "")?.trim()
            ?: throw Exception("Empty response")

        Log.d(TAG, "Gemini response: $responseText")

        val parsed = json.decodeFromString<GeminiProcessingResponse>(responseText)

        return ProcessingResult(
            cleanText = parsed.cleanText,
            actionType = validateActionType(parsed.actionType),
            dueDate = parseDateString(parsed.dueDate),
            priority = validatePriority(parsed.priority),
            confidence = parsed.confidence
        )
    }

    /**
     * Rule-based fallback when Gemini is unavailable.
     */
    fun processWithRules(text: String): ProcessingResult {
        val lower = text.lowercase()

        // Detect action type
        val actionType = when {
            lower.contains("llamar") || lower.contains("llama a") || lower.contains("telefonear") ->
                EntryActionType.CALL
            lower.contains("comprar") || lower.contains("compra") ->
                EntryActionType.BUY
            lower.contains("enviar") || lower.contains("envía") || lower.contains("escríbele") ||
            lower.contains("mándale") || lower.contains("mensaje") ->
                EntryActionType.SEND
            lower.contains("cita") || lower.contains("reunión") || lower.contains("evento") ||
            lower.contains("reserva") || lower.contains("reservar") ->
                EntryActionType.EVENT
            lower.contains("revisar") || lower.contains("mirar") || lower.contains("comprobar") ||
            lower.contains("buscar") ->
                EntryActionType.REVIEW
            lower.contains("hablar con") || lower.contains("dile a") || lower.contains("decirle") ||
            lower.contains("comentar con") ->
                EntryActionType.TALK_TO
            else -> EntryActionType.GENERIC
        }

        // Clean text: remove trigger phrases
        val triggers = listOf(
            "tengo que ", "hay que ", "debería ", "deberia ", "necesito ",
            "me falta ", "me toca ", "recordar ", "no olvidar ", "acuérdate de ",
            "vamos a ", "habría que ", "tendría que ", "oye "
        )
        var clean = text.trim()
        for (trigger in triggers) {
            if (clean.lowercase().startsWith(trigger)) {
                clean = clean.substring(trigger.length).trim()
                break
            }
        }
        // Capitalize first letter
        clean = clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        // Detect priority
        val priority = when {
            lower.contains("urgente") || lower.contains("ya ") || lower.contains("ahora") ||
            lower.contains("cuanto antes") -> EntryPriority.URGENT
            lower.contains("importante") || lower.contains("no olvidar") -> EntryPriority.HIGH
            lower.contains("cuando pueda") || lower.contains("algún día") -> EntryPriority.LOW
            else -> EntryPriority.NORMAL
        }

        // Detect due date
        val dueDate = extractDueDate(lower)

        return ProcessingResult(
            cleanText = clean,
            actionType = actionType,
            dueDate = dueDate,
            priority = priority,
            confidence = 0.6f
        )
    }

    private fun extractDueDate(text: String): Long? {
        val cal = Calendar.getInstance()

        return when {
            text.contains("hoy") -> {
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.timeInMillis
            }
            text.contains("mañana") -> {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.timeInMillis
            }
            text.contains("lunes") -> nextWeekday(Calendar.MONDAY)
            text.contains("martes") -> nextWeekday(Calendar.TUESDAY)
            text.contains("miércoles") || text.contains("miercoles") -> nextWeekday(Calendar.WEDNESDAY)
            text.contains("jueves") -> nextWeekday(Calendar.THURSDAY)
            text.contains("viernes") -> nextWeekday(Calendar.FRIDAY)
            text.contains("sábado") || text.contains("sabado") -> nextWeekday(Calendar.SATURDAY)
            text.contains("domingo") -> nextWeekday(Calendar.SUNDAY)
            text.contains("esta semana") -> {
                cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                if (cal.timeInMillis < System.currentTimeMillis()) cal.add(Calendar.WEEK_OF_YEAR, 1)
                cal.timeInMillis
            }
            else -> null
        }
    }

    private fun nextWeekday(targetDay: Int): Long {
        val cal = Calendar.getInstance()
        val currentDay = cal.get(Calendar.DAY_OF_WEEK)
        var daysAhead = targetDay - currentDay
        if (daysAhead <= 0) daysAhead += 7
        cal.add(Calendar.DAY_OF_YEAR, daysAhead)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        return cal.timeInMillis
    }

    private fun parseDateString(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank() || dateStr == "null") return null
        return try {
            dateFormat.parse(dateStr)?.time
        } catch (e: Exception) {
            null
        }
    }

    private fun validateActionType(type: String): String = when (type.uppercase()) {
        "CALL", "BUY", "SEND", "EVENT", "REVIEW", "TALK_TO", "GENERIC" -> type.uppercase()
        else -> EntryActionType.GENERIC
    }

    private fun validatePriority(priority: String): String = when (priority.uppercase()) {
        "LOW", "NORMAL", "HIGH", "URGENT" -> priority.uppercase()
        else -> EntryPriority.NORMAL
    }

    private fun getApiKey(): String? {
        return context.getSharedPreferences("daily_summary", Context.MODE_PRIVATE)
            .getString("gemini_api_key", null)
    }

    data class ProcessingResult(
        val cleanText: String,
        val actionType: String,
        val dueDate: Long?,
        val priority: String,
        val confidence: Float
    )

    @Serializable
    private data class GeminiProcessingResponse(
        val cleanText: String,
        val actionType: String = "GENERIC",
        val dueDate: String? = null,
        val priority: String = "NORMAL",
        val confidence: Float = 0.8f
    )
}
