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
        val existingEntry = repository.getByIdOnce(entryId)
        val originalText = existingEntry?.text?.takeIf { it.isNotBlank() } ?: text
        val normalizedInput = existingEntry?.correctedText?.takeIf { it.isNotBlank() } ?: text
        val processingText = normalizedInput.ifBlank { originalText }

        val result = tryProcess(
            originalText = originalText,
            normalizedInput = processingText
        )
        val splitCleanTexts = maybeAutoSplitEntry(entryId, processingText, result, repository)
        val heuristicFallback = if (result == null && splitCleanTexts.isEmpty()) {
            buildHeuristicFallback(originalText, processingText)
        } else {
            null
        }
        val dedupTargets = splitCleanTexts.ifEmpty {
            listOf(result?.cleanText ?: heuristicFallback?.cleanText ?: processingText)
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
        } else if (heuristicFallback != null && splitCleanTexts.isEmpty()) {
            repository.updateAIProcessing(
                id = entryId,
                cleanText = heuristicFallback.cleanText,
                actionType = heuristicFallback.actionType,
                dueDate = heuristicFallback.dueDate,
                priority = heuristicFallback.priority,
                confidence = heuristicFallback.confidence
            )
            Log.i(TAG, "Heuristic fallback for entry $entryId: '${heuristicFallback.cleanText}' [${heuristicFallback.actionType}]")
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

    private suspend fun tryProcess(
        originalText: String,
        normalizedInput: String
    ): ProcessingResult? {
        // Try Cloud
        val apiKey = getApiKey()
        if (!apiKey.isNullOrBlank()) {
            try {
                return processWithCloud(originalText, normalizedInput, apiKey)
            } catch (e: Exception) {
                Log.w(TAG, "Cloud failed: ${e.javaClass.simpleName}", e)
            }
        }

        // Try local on-device model
        if (GemmaClient.isModelAvailable(context)) {
            try {
                return processWithLocalModel(originalText, normalizedInput)
            } catch (e: Exception) {
                Log.w(TAG, "Local model failed", e)
            }
        }

        return null
    }

    private fun buildHeuristicFallback(
        originalText: String,
        normalizedInput: String
    ): ProcessingResult? {
        val candidates = linkedSetOf<String>()
        if (originalText.isNotBlank()) candidates += originalText
        if (normalizedInput.isNotBlank()) candidates += normalizedInput

        val suggestion = candidates
            .asSequence()
            .flatMap { ManualActionSuggestionExtractor.extract(it).asSequence() }
            .firstOrNull()
            ?: return null

        return ProcessingResult(
            cleanText = suggestion.text,
            actionType = suggestion.actionType,
            dueDate = suggestion.dueDate,
            priority = suggestion.priority,
            confidence = 0.65f
        )
    }

    private suspend fun processWithCloud(
        originalText: String,
        normalizedInput: String,
        apiKey: String
    ): ProcessingResult {
        val prompt = buildPrompt(originalText, normalizedInput)

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
        return parseResult(responseText, 1.0f)
    }

    private suspend fun processWithLocalModel(
        originalText: String,
        normalizedInput: String
    ): ProcessingResult? {
        // Use the same structured prompt as Cloud
        val prompt = buildPrompt(originalText, normalizedInput)
        val responseText = GemmaClient.generate(context, prompt, maxTokens = 256, responsePrefix = "{") ?: return null
        Log.d(TAG, "Local model response: $responseText")

        return try {
            // Try parsing as JSON (same format as Cloud)
            parseResult(responseText, 0.85f)
        } catch (e: Exception) {
            // Fallback: use response as clean text + keyword-based inference
            Log.d(TAG, "Local model JSON parse failed, using text fallback", e)
            val cleanText = JsonRepair.extractAndRepair(responseText).trim().removeSurrounding("\"")
            val validatedCleanText = cleanText.takeIf { it.isNotBlank() } ?: return null
            ProcessingResult(
                cleanText = validatedCleanText.take(200),
                actionType = inferActionType(normalizedInput),
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

    private fun buildPrompt(originalText: String, normalizedInput: String): String {
        val today = dateFormat.format(Calendar.getInstance().time)
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tomorrowStr = dateFormat.format(tomorrow.time)
        return PromptTemplateStore.render(
            context,
            PromptTemplateStore.ACTION_ITEM,
            mapOf(
                "text" to normalizedInput,
                "originalText" to originalText,
                "normalizedInput" to normalizedInput,
                "today" to today,
                "tomorrow" to tomorrowStr
            )
        )
    }


    private fun parseResult(responseText: String, confidenceMultiplier: Float): ProcessingResult {
        val parsed = json.decodeFromString<LLMResponse>(JsonRepair.extractAndRepair(responseText))
        require(parsed.cleanText.isNotBlank()) { "LLM returned blank cleanText" }
        return ProcessingResult(
            cleanText = parsed.cleanText.trim(),
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

        DuplicateHeuristics.findLikelyDuplicate(
            text = cleanText,
            existing = existing,
            ignoreId = entryId
        )?.let { duplicate ->
            repository.markDuplicate(entryId, duplicate.id)
            Log.i(TAG, "Heuristic duplicate: entry $entryId ≈ '${duplicate.displayText}'")
            return
        }

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
