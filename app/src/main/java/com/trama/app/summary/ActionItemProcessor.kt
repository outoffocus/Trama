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
        // Only run the heuristic auto-splitter when the LLM was unavailable or
        // returned nothing usable. If the LLM already produced an actionable
        // result, trust it — re-splitting with regex was a source of false
        // positives (e.g. splitting on every verb in a single coherent request).
        val splitCleanTexts = if (result == null) {
            maybeAutoSplitEntry(entryId, processingText, null, repository)
        } else {
            emptyList()
        }
        val heuristicFallback = if (result == null && splitCleanTexts.isEmpty()) {
            buildHeuristicFallback(originalText, processingText)
        } else {
            null
        }
        val dedupTargets = splitCleanTexts.ifEmpty {
            listOf(result?.cleanText ?: heuristicFallback?.cleanText ?: processingText)
        }

        if (result != null && splitCleanTexts.isEmpty()) {
            if (shouldAcceptAsTask(result)) {
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
                repository.updateAIProcessing(
                    id = entryId,
                    cleanText = result.cleanText,
                    actionType = result.actionType,
                    dueDate = result.dueDate,
                    priority = result.priority,
                    confidence = result.confidence
                )
                repository.markDiscarded(entryId)
                Log.i(
                    TAG,
                    "Discarding non-actionable entry $entryId: '${result.cleanText}' " +
                        "(actionable=${result.isActionable}, confidence=${result.confidence})"
                )
                return
            }
        } else if (heuristicFallback != null && splitCleanTexts.isEmpty()) {
            if (shouldAcceptAsTask(heuristicFallback)) {
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
                repository.markDiscarded(entryId)
                Log.i(
                    TAG,
                    "Discarding non-actionable heuristic fallback for entry $entryId: " +
                        "'${heuristicFallback.cleanText}' (confidence=${heuristicFallback.confidence})"
                )
                return
            }
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

        // Without an LLM we can't reliably judge actionability, so the heuristic
        // only proposes — the deterministic validator below is the final gate.
        val passesGate = isActionableAfterValidation(suggestion.text, modelIsActionable = true)
        return ProcessingResult(
            cleanText = suggestion.text,
            actionType = suggestion.actionType,
            dueDate = suggestion.dueDate,
            priority = suggestion.priority,
            confidence = if (passesGate) 0.5f else 0.25f,
            isActionable = passesGate
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
            val truncated = validatedCleanText.take(200)
            val passesGate = isActionableAfterValidation(truncated, modelIsActionable = true)
            ProcessingResult(
                cleanText = truncated,
                actionType = inferActionType(normalizedInput),
                dueDate = null,
                priority = "NORMAL",
                confidence = if (passesGate) 0.7f else 0.25f,
                isActionable = passesGate
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
        val cleanText = parsed.cleanText.trim()
        val baseConfidence = parsed.confidence * confidenceMultiplier
        val passesGate = isActionableAfterValidation(cleanText, parsed.isActionable)
        // If the deterministic validator disagrees with the model, collapse confidence
        // so the caller can treat the entry as non-actionable.
        val effectiveConfidence = if (!passesGate) {
            minOf(baseConfidence, 0.29f)
        } else {
            baseConfidence
        }
        return ProcessingResult(
            cleanText = cleanText,
            actionType = validateActionType(parsed.actionType),
            dueDate = parseDateString(parsed.dueDate),
            priority = validatePriority(parsed.priority),
            confidence = effectiveConfidence,
            isActionable = passesGate
        )
    }

    private fun shouldAcceptAsTask(result: ProcessingResult): Boolean =
        result.isActionable && result.confidence >= ACTIONABLE_CONFIDENCE_THRESHOLD

    /**
     * Deterministic post-LLM check: rejects cleanText strings that are clearly not
     * actionable regardless of what the model reported. Catches the well-known
     * failure modes — temporal-only fragments, verbless stubs, ASR noise — where
     * the model's textual rules are ignored.
     */
    private fun isActionableAfterValidation(cleanText: String, modelIsActionable: Boolean): Boolean {
        if (!modelIsActionable) return false
        val normalized = cleanText
            .lowercase(Locale.getDefault())
            .trim()
            .trim('.', ',', ';', ':', '!', '?', '¿', '¡', '-', ' ')
        if (normalized.length < 6) return false
        if (normalized in TEMPORAL_ONLY_PHRASES) return false
        val tokens = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size < 2) return false
        // At least one non-temporal, non-stopword token of length ≥ 3
        val meaningful = tokens.filter { it.length >= 3 && it !in TEMPORAL_TOKENS }
        if (meaningful.isEmpty()) return false
        return true
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

        /** Minimum confidence for an LLM extraction to be accepted as a real task. */
        private const val ACTIONABLE_CONFIDENCE_THRESHOLD = 0.45f

        /** Phrases that, when they constitute the whole cleanText, are not actionable. */
        private val TEMPORAL_ONLY_PHRASES = setOf(
            "mañana", "manana", "hoy", "ayer", "anoche",
            "esta tarde", "esta noche", "esta mañana", "esta manana",
            "mañana por la mañana", "manana por la manana",
            "mañana por la tarde", "manana por la tarde",
            "mañana por la noche", "manana por la noche",
            "pasado mañana", "pasado manana",
            "hay que", "tengo que", "deberia", "debería",
            "recordar", "acordarme", "acordarnos",
            "por la mañana", "por la manana", "por la tarde", "por la noche",
            "todos los dias", "todos los días", "cada dia", "cada día",
            "cada mañana", "cada manana", "cada tarde", "cada noche",
            "a veces", "de vez en cuando"
        )

        /** Tokens that count as temporal/filler when evaluating whether a cleanText has real content. */
        private val TEMPORAL_TOKENS = setOf(
            "hoy", "ayer", "anoche", "mañana", "manana", "tarde", "noche",
            "esta", "este", "pasado", "pasada",
            "luego", "después", "despues", "antes",
            "siempre", "nunca", "veces", "vez",
            "todos", "todas", "cada", "los", "las", "el", "la",
            "por", "de", "en", "a", "al", "del",
            "que", "y", "o", "u"
        )
    }

    data class ProcessingResult(
        val cleanText: String,
        val actionType: String,
        val dueDate: Long?,
        val priority: String,
        val confidence: Float,
        val isActionable: Boolean = true
    )

    @Serializable
    private data class LLMResponse(
        val cleanText: String,
        val actionType: String = "GENERIC",
        val dueDate: String? = null,
        val priority: String = "NORMAL",
        val confidence: Float = 0.8f,
        val isActionable: Boolean = true
    )
}
