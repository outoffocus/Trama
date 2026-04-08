package com.trama.app.summary

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.trama.app.GeminiConfig
import com.google.ai.client.generativeai.type.generationConfig
import com.trama.shared.data.DiaryRepository
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryActionType
import com.trama.shared.model.EntryPriority
import com.trama.shared.model.EntryStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Processes a captured diary entry through LLM to extract:
 * - cleanText: a clean, actionable summary
 * - actionType: CALL, BUY, SEND, EVENT, REVIEW, TALK_TO, GENERIC
 * - dueDate: extracted date if mentioned
 * - priority: LOW, NORMAL, HIGH, URGENT
 *
 * Priority: Cloud → local on-device model. If no LLM available, leaves entry as-is.
 */
class ActionItemProcessor(private val context: Context) {

    suspend fun process(entryId: Long, text: String, repository: DiaryRepository) {
        val result = tryProcess(text)
        val splitCleanTexts = maybeAutoSplitEntry(entryId, text, result, repository)
        val dedupTargets = splitCleanTexts.ifEmpty {
            listOf(result?.cleanText ?: text)
        }

        if (result != null && splitCleanTexts.isEmpty()) {
            repository.updateAIProcessing(
                id = entryId,
                cleanText = result.cleanText,
                actionType = result.actionType,
                dueDate = result.dueDate,
                priority = result.priority,
                confidence = result.confidence
            )
            Log.i(TAG, "Processed entry $entryId: '${result.cleanText}' [${result.actionType}]")
        } else {
            Log.w(TAG, "No LLM available for entry $entryId, leaving as-is")
        }

        // Check for duplicates
        try {
            checkForDuplicates(entryId, dedupTargets.first(), repository)
        } catch (e: Exception) {
            Log.w(TAG, "Duplicate check failed", e)
        }
    }

    private suspend fun maybeAutoSplitEntry(
        entryId: Long,
        originalText: String,
        result: ProcessingResult?,
        repository: DiaryRepository
    ): List<String> {
        val suggestions = ManualActionSuggestionExtractor.extract(originalText)
            .distinctBy { it.text.lowercase(Locale.getDefault()) }
            .take(4)

        if (suggestions.size < 2) return emptyList()

        val originalEntry = repository.getByIdOnce(entryId) ?: return emptyList()
        val primary = suggestions.first()

        repository.updateAIProcessing(
            id = entryId,
            cleanText = primary.text,
            actionType = primary.actionType,
            dueDate = primary.dueDate,
            priority = primary.priority,
            confidence = result?.confidence ?: 0.8f
        )

        Log.i(TAG, "Auto-splitting entry $entryId into ${suggestions.size} actions")

        for (suggestion in suggestions.drop(1)) {
            val siblingId = repository.insert(
                DiaryEntry(
                    text = suggestion.text,
                    keyword = originalEntry.keyword,
                    category = originalEntry.category,
                    confidence = originalEntry.confidence,
                    createdAt = System.currentTimeMillis(),
                    source = originalEntry.source,
                    duration = originalEntry.duration,
                    correctedText = null,
                    wasReviewedByLLM = true,
                    llmConfidence = result?.confidence ?: originalEntry.llmConfidence,
                    status = EntryStatus.PENDING,
                    actionType = suggestion.actionType,
                    cleanText = suggestion.text,
                    dueDate = suggestion.dueDate,
                    priority = suggestion.priority
                )
            )
            try {
                checkForDuplicates(siblingId, suggestion.text, repository)
            } catch (e: Exception) {
                Log.w(TAG, "Duplicate check failed for auto-split sibling $siblingId", e)
            }
        }

        return suggestions.map { it.text }
    }

    private suspend fun tryProcess(text: String): ProcessingResult? {
        // Try Cloud
        val apiKey = getApiKey()
        if (!apiKey.isNullOrBlank()) {
            try {
                return processWithCloud(text, apiKey)
            } catch (e: Exception) {
                Log.w(TAG, "Cloud failed: ${e.javaClass.simpleName}", e)
            }
        }

        // Try local on-device model
        if (GemmaClient.isModelAvailable(context)) {
            try {
                return processWithLocalModel(text)
            } catch (e: Exception) {
                Log.w(TAG, "Local model failed", e)
            }
        }

        return null
    }

    private suspend fun processWithCloud(text: String, apiKey: String): ProcessingResult {
        val prompt = buildPrompt(text)

        val model = GenerativeModel(
            modelName = GeminiConfig.MODEL_NAME,
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.1f
                maxOutputTokens = 256
            }
        )

        val response = model.generateContent(prompt)
        val responseText = response.text?.trim()
            ?: throw Exception("Empty Gemini response")

        Log.d(TAG, "Cloud OK: $responseText")
        return parseResult(JsonRepair.extractJson(responseText), 1.0f)
    }

    private suspend fun processWithLocalModel(text: String): ProcessingResult? {
        // Use the same structured prompt as Cloud
        val prompt = buildPrompt(text)
        val responseText = GemmaClient.generate(context, prompt, maxTokens = 256, responsePrefix = "{") ?: return null
        Log.d(TAG, "Local model response: $responseText")

        return try {
            // Try parsing as JSON (same format as Cloud)
            parseResult(JsonRepair.extractJson(responseText), 0.85f)
        } catch (e: Exception) {
            // Fallback: use response as clean text + keyword-based inference
            Log.d(TAG, "Local model JSON parse failed, using text fallback", e)
            val cleanText = JsonRepair.extractJson(responseText).trim().removeSurrounding("\"")
            ProcessingResult(
                cleanText = cleanText.replaceFirstChar { it.uppercase() }.take(200),
                actionType = inferActionType(text),
                dueDate = null,
                priority = "NORMAL",
                confidence = 0.7f
            )
        }
    }

    /** Simple keyword-based action type detection as fallback. */
    private fun inferActionType(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("llamar") || lower.contains("llama") -> "CALL"
            lower.contains("comprar") || lower.contains("compra") -> "BUY"
            lower.contains("enviar") || lower.contains("mandar") -> "SEND"
            lower.contains("reunión") || lower.contains("cita") || lower.contains("evento") -> "EVENT"
            lower.contains("revisar") || lower.contains("mirar") -> "REVIEW"
            lower.contains("hablar con") || lower.contains("decir a") || lower.contains("decirle") -> "TALK_TO"
            else -> "GENERIC"
        }
    }

    private fun buildPrompt(text: String): String {
        val today = dateFormat.format(Calendar.getInstance().time)
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tomorrowStr = dateFormat.format(tomorrow.time)

        return """Analiza esta nota de voz capturada y devuelve SOLO un objeto JSON valido.
- No añadas explicaciones, markdown, backticks ni texto extra.
- Si dudas entre varias interpretaciones, elige la mas conservadora.

Formato exacto:
{
  "cleanText": "texto limpio y corregido, sin el trigger inicial",
  "actionType": "CALL|BUY|SEND|EVENT|REVIEW|TALK_TO|GENERIC",
  "dueDate": "YYYY-MM-DD o null",
  "priority": "LOW|NORMAL|HIGH|URGENT",
  "confidence": 0.0-1.0
}

Reglas:
- cleanText:
  - elimina frases activadoras como "recordar", "tengo que", "hay que", "deberia", "acordarme de", "acordarnos de", "me olvide", "se me fue la olla"
  - deja solo el contenido util de la nota
  - corrige errores claros de transcripcion
  - manten el mismo idioma
  - escribe una frase breve, natural y capitalizada
  - no inventes detalles que no aparecen en la nota
- actionType: CALL=llamar, BUY=comprar, SEND=enviar, EVENT=cita/reunión, REVIEW=revisar, TALK_TO=hablar con, GENERIC=otro
- dueDate:
  - usa YYYY-MM-DD
  - conviertela solo si la nota menciona una fecha o momento temporal claro
  - hoy = $today
  - mañana = $tomorrowStr
  - si no hay fecha clara, devuelve null
  - no inventes fechas
- priority:
  - URGENT si aparece urgencia explicita como "urgente", "ya", "ahora", "cuanto antes"
  - HIGH si indica alta importancia
  - LOW si transmite baja prioridad o "cuando pueda"
  - NORMAL en el resto
- confidence:
  - entre 0.0 y 1.0
  - alto si el contenido es claro y especifico
  - bajo si la nota es ambigua o esta mal transcrita

Nota de voz: "$text""""
    }


    private fun parseResult(responseText: String, confidenceMultiplier: Float): ProcessingResult {
        val parsed = json.decodeFromString<LLMResponse>(JsonRepair.repair(responseText))
        return ProcessingResult(
            cleanText = parsed.cleanText,
            actionType = validateActionType(parsed.actionType),
            dueDate = parseDateString(parsed.dueDate),
            priority = validatePriority(parsed.priority),
            confidence = parsed.confidence * confidenceMultiplier
        )
    }

    // ── Duplicate detection ──

    private suspend fun checkForDuplicates(entryId: Long, cleanText: String, repository: DiaryRepository) {
        val existing = repository.getRecentPendingForDedup()
            .filter { it.id != entryId }

        if (existing.isEmpty()) return

        val entriesList = existing.take(20).joinToString("\n") { "${it.id}: ${it.displayText}" }

        val prompt = """Compara esta nueva tarea con las existentes y decide si es un DUPLICADO exacto.
Responde SOLO con un objeto JSON valido y nada mas:
{"duplicateOfId": ID_NUMBER o null}

Nueva tarea: "$cleanText"

Tareas existentes:
$entriesList

Reglas:
- DUPLICADO = la MISMA tarea concreta: mismo verbo + mismo objeto/persona específica
- Debe referirse a la misma accion pendiente, no solo al mismo tema general
- "Llamar a Juan" y "Telefonear a Juan" → SÍ duplicado (misma persona)
- "Comprar ajos" y "Comprar un coche" → NO duplicado (objetos distintos)
- "Llamar al dentista" y "Llamar a mi hermana" → NO duplicado (personas distintas)
- "Mirar presupuesto de cocina" y "Pedir presupuesto de cocina" → NO duplicado si la accion principal cambia
- Compartir solo el verbo (comprar, llamar, enviar) NO es suficiente para ser duplicado
- Si no coincide exactamente la persona, objeto o accion principal, responde null
- En caso de duda, responde null"""

        // Try Cloud
        val apiKey = getApiKey()
        if (!apiKey.isNullOrBlank()) {
            try {
                val model = GenerativeModel(
                    modelName = GeminiConfig.MODEL_NAME,
                    apiKey = apiKey,
                    generationConfig = generationConfig { temperature = 0.1f }
                )
                val response = model.generateContent(prompt)
                if (parseDedupAndMark(response.text, entryId, existing, repository)) return
            } catch (e: Exception) {
                Log.d(TAG, "Cloud dedup failed: ${e.message}")
            }
        }

        // Try local model
        if (GemmaClient.isModelAvailable(context)) {
            try {
                val response = GemmaClient.generate(context, prompt, maxTokens = 64)
                if (response != null && parseDedupAndMark(response, entryId, existing, repository)) return
            } catch (e: Exception) {
                Log.d(TAG, "Local model dedup failed: ${e.message}")
            }
        }
    }

    private suspend fun parseDedupAndMark(
        responseText: String?,
        entryId: Long,
        existing: List<com.trama.shared.model.DiaryEntry>,
        repository: DiaryRepository
    ): Boolean {
        if (responseText.isNullOrBlank()) return false
        val jsonStr = JsonRepair.extractAndRepair(responseText)
        return try {
            @Serializable
            data class DedupResult(val duplicateOfId: Long? = null)

            val result = json.decodeFromString<DedupResult>(jsonStr)
            if (result.duplicateOfId != null && existing.any { it.id == result.duplicateOfId }) {
                repository.markDuplicate(entryId, result.duplicateOfId)
                val original = existing.first { it.id == result.duplicateOfId }
                Log.i(TAG, "Duplicate: entry $entryId ≈ '${original.displayText}'")
                true
            } else false
        } catch (_: Exception) { false }
    }

    // ── Helpers ──

    private fun parseDateString(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank() || dateStr == "null") return null
        return try { dateFormat.parse(dateStr)?.time } catch (_: Exception) { null }
    }

    private fun validateActionType(type: String): String = when (type.uppercase()) {
        "CALL", "BUY", "SEND", "EVENT", "REVIEW", "TALK_TO", "GENERIC" -> type.uppercase()
        else -> EntryActionType.GENERIC
    }

    private fun validatePriority(priority: String): String = when (priority.uppercase()) {
        "LOW", "NORMAL", "HIGH", "URGENT" -> priority.uppercase()
        else -> EntryPriority.NORMAL
    }

    private fun getApiKey(): String? =
        context.getSharedPreferences("daily_summary", Context.MODE_PRIVATE)
            .getString("gemini_api_key", null)

    companion object {
        private const val TAG = "ActionItemProcessor"
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }

    data class ProcessingResult(
        val cleanText: String,
        val actionType: String,
        val dueDate: Long?,
        val priority: String,
        val confidence: Float
    )

    @Serializable
    private data class LLMResponse(
        val cleanText: String,
        val actionType: String = "GENERIC",
        val dueDate: String? = null,
        val priority: String = "NORMAL",
        val confidence: Float = 0.8f
    )
}
