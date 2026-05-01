package com.trama.app.summary

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.trama.app.GeminiConfig
import com.google.ai.client.generativeai.type.generationConfig
import com.trama.app.diagnostics.CaptureLog
import com.trama.shared.data.DiaryRepository
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryProcessingBackend
import com.trama.shared.model.EntryStatus
import com.trama.shared.model.RecordingStatus
import com.trama.shared.model.Source
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Processes a recording transcription:
 * 1. Tries Gemini Cloud
 * 2. Falls back to local on-device model
 * 3. If no LLM available, stays PENDING for later retry
 *
 * Both Cloud and local use the same JSON prompt and produce
 * the same output (title, summary, keyPoints, actionItems).
 */
class RecordingProcessor(private val context: Context) {

    companion object {
        private const val TAG = "RecordingProcessor"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    suspend fun process(recordingId: Long, repository: DiaryRepository) {
        val recording = repository.getRecordingByIdOnce(recordingId)
        if (recording == null) {
            Log.w(TAG, "Recording $recordingId not found")
            return
        }

        if (recording.transcription.isBlank()) {
            Log.w(TAG, "Recording $recordingId has empty transcription")
            repository.updateRecordingStatus(recordingId, RecordingStatus.FAILED)
            return
        }

        repository.updateRecordingStatus(recordingId, RecordingStatus.PROCESSING)

        // 1. Try Gemini Cloud
        val apiKey = getApiKey()
        if (!apiKey.isNullOrBlank()) {
            val ok = tryCloud(recordingId, recording.transcription, recording.source, apiKey, repository)
            if (ok) return
        }

        // 2. If already processed AND has existing actions, keep them.
        //    Actions persist until the user completes or deletes them.
        //    But if there are no actions yet (e.g. previous local attempt failed JSON),
        //    the local model must try again.
        val existingActions = repository.getByRecordingIdOnce(recordingId)
        if (recording.processingStatus == RecordingStatus.COMPLETED && existingActions.isNotEmpty()) {
            Log.i(TAG, "Recording $recordingId already has ${existingActions.size} actions, keeping them")
            repository.updateRecordingStatus(recordingId, RecordingStatus.COMPLETED)
            return
        }

        // 3. Try local on-device model (same prompt & format as Cloud)
        val modelFile = GemmaClient.getModelFile(context)
        if (modelFile.exists()) {
            val ok = tryLocalModel(recordingId, recording.transcription, recording.source, repository)
            if (ok) return
        }

        // 4. No LLM available — stay PENDING for later retry
        Log.w(TAG, "No LLM available for recording $recordingId, leaving as PENDING (apiKey blank=${apiKey.isNullOrBlank()}, model exists=${modelFile.exists()})")
        repository.updateRecordingStatus(recordingId, RecordingStatus.PENDING)
    }

    // ── Cloud ──

    private suspend fun tryCloud(
        recordingId: Long,
        transcription: String,
        source: Source,
        apiKey: String,
        repository: DiaryRepository
    ): Boolean {
        val prompt = buildPrompt(transcription)

        val model = GenerativeModel(
            modelName = GeminiConfig.MODEL_NAME,
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.2f
                maxOutputTokens = 4096
            }
        )

        return try {
            val response = model.generateContent(prompt)
            val responseText = response.text?.trim() ?: throw Exception("Empty Cloud response")
            Log.d(TAG, "Cloud response: ${responseText.take(200)}")

            val result = parseResponse(responseText)
            // Delete previous actions only when we have new ones to replace them
            repository.deleteByRecordingId(recordingId)
            saveResult(recordingId, result, transcription, source, "CLOUD", 0.9f, repository)

            Log.i(TAG, "Recording $recordingId processed via Cloud: '${result.title}', ${result.actionItems.size} actions")
            CaptureLog.event(
                gate = CaptureLog.Gate.RECORDING,
                result = if (result.actionItems.isNotEmpty()) CaptureLog.Result.OK else CaptureLog.Result.NO_MATCH,
                text = result.title,
                meta = mapOf(
                    "id" to recordingId,
                    "actions" to result.actionItems.size,
                    "source" to "CLOUD"
                )
            )
            checkActionsForDuplicates(recordingId, repository)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Cloud failed for recording $recordingId: ${e.javaClass.simpleName}", e)
            false
        }
    }

    // ── Local on-device model ──

    private suspend fun tryLocalModel(
        recordingId: Long,
        transcription: String,
        source: Source,
        repository: DiaryRepository
    ): Boolean {
        return try {
            // Attempt 1: full prompt with JSON prefix forcing
            val prompt = buildPrompt(transcription)
            val responseText = GemmaClient.generate(context, prompt, maxTokens = 2048, responsePrefix = "{")
                ?: throw Exception("Empty local model response")
            Log.d(TAG, "Local model response: ${responseText.take(300)}")

            val result = try {
                parseResponse(responseText)
            } catch (e: Exception) {
                Log.w(TAG, "Local model JSON attempt 1 failed: ${e.message}")
                // Attempt 2: simpler prompt, less likely to confuse the model
                retryWithSimplePrompt(transcription, repository)
            }

            repository.deleteByRecordingId(recordingId)
            saveResult(recordingId, result, transcription, source, "LOCAL", 0.8f, repository)

            Log.i(TAG, "Recording $recordingId processed via local model: '${result.title}', ${result.actionItems.size} actions")
            CaptureLog.event(
                gate = CaptureLog.Gate.RECORDING,
                result = if (result.actionItems.isNotEmpty()) CaptureLog.Result.OK else CaptureLog.Result.NO_MATCH,
                text = result.title,
                meta = mapOf(
                    "id" to recordingId,
                    "actions" to result.actionItems.size,
                    "source" to "LOCAL"
                )
            )
            if (result.actionItems.isNotEmpty()) {
                checkActionsForDuplicates(recordingId, repository)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Local model failed for recording $recordingId: ${e.javaClass.simpleName}", e)
            false
        }
    }

    /**
     * Retry with a much simpler prompt when the full prompt fails.
     * Splits into two calls: one for title+summary, one for action extraction.
     */
    private suspend fun retryWithSimplePrompt(
        transcription: String,
        repository: DiaryRepository
    ): RecordingAnalysis {
        Log.i(TAG, "Retrying with simplified prompts")

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Calendar.getInstance().time)

        // Call 1: title + summary (plain text, no JSON needed)
        val titlePrompt = PromptTemplateStore.render(
            context,
            PromptTemplateStore.RECORDING_TITLE,
            mapOf("transcription" to transcription)
        )
        val title = GemmaClient.generate(context, titlePrompt, maxTokens = 32)
            ?.trim()?.removeSurrounding("\"")?.take(80)
            ?: "Nota de voz"

        val summaryPrompt = PromptTemplateStore.render(
            context,
            PromptTemplateStore.RECORDING_SUMMARY,
            mapOf("transcription" to transcription)
        )
        val summary = GemmaClient.generate(context, summaryPrompt, maxTokens = 256)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: transcription.take(200)

        // Call 2: extract actions as simple JSON array.
        // Include live pending/completed/place context so we don't re-extract
        // tasks already captured during the same continuous-ASR session.
        val actionProcessor = ActionItemProcessor(context)
        val recentContext = actionProcessor.buildContextBlock(
            actionProcessor.buildRecentContext(entryId = -1L, repository = repository)
        )
        val actionsPrompt = PromptTemplateStore.render(
            context,
            PromptTemplateStore.RECORDING_ACTIONS,
            mapOf(
                "transcription" to transcription,
                "today" to today,
                "recentContext" to recentContext
            )
        )

        val actionsResponse = GemmaClient.generate(context, actionsPrompt, maxTokens = 512, responsePrefix = "[")
        val actionItems = parseSimpleActions(actionsResponse)

        Log.d(TAG, "Simple prompt: title='$title', actions=${actionItems.size}")
        return RecordingAnalysis(
            title = title,
            summary = summary,
            keyPoints = emptyList(),
            actionItems = actionItems
        )
    }

    /** Parse a simple JSON array of actions: [{"text":"...", "type":"..."}] */
    private fun parseSimpleActions(response: String?): List<ActionItem> {
        if (response.isNullOrBlank()) return emptyList()
        return try {
            val cleaned = JsonRepair.extractAndRepair(response)
            val items = json.decodeFromString<List<SimpleAction>>(cleaned)
            items.mapNotNull {
                val text = it.text.trim()
                val actionType = validateActionType(it.type ?: "GENERIC")
                if (
                    text.isBlank() ||
                    !ActionQualityGate.isActionable(
                        cleanText = text,
                        actionType = actionType,
                        modelIsActionable = true
                    )
                ) {
                    null
                } else {
                    ActionItem(text = text, actionType = actionType)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Simple actions parse failed: ${e.message}")
            emptyList()
        }
    }

    @Serializable
    private data class SimpleAction(
        val text: String,
        val type: String? = "GENERIC"
    )

    // ── Shared ──

    private fun buildPrompt(transcription: String): String {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Calendar.getInstance().time)
        val tomorrow = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.time)
        return PromptTemplateStore.render(
            context,
            PromptTemplateStore.RECORDING_ANALYSIS,
            mapOf(
                "transcription" to transcription,
                "today" to today,
                "tomorrow" to tomorrow
            )
        )
    }


    private fun parseResponse(responseText: String): RecordingAnalysis {
        val jsonStr = JsonRepair.extractAndRepair(responseText)
        val parsed = json.decodeFromString<RecordingAnalysis>(jsonStr)
        require(parsed.title.isNotBlank()) { "LLM returned blank title" }
        require(parsed.summary.isNotBlank()) { "LLM returned blank summary" }
        return parsed.copy(
            title = parsed.title.trim(),
            summary = parsed.summary.trim(),
            keyPoints = parsed.keyPoints.map { it.trim() }.filter { it.isNotBlank() },
            actionItems = parsed.actionItems.mapNotNull { action ->
                val text = action.text.trim()
                if (text.isBlank()) null else action.copy(text = text)
            }
        )
    }

    private suspend fun saveResult(
        recordingId: Long,
        result: RecordingAnalysis,
        transcription: String,
        source: Source,
        processedBy: String,
        confidence: Float,
        repository: DiaryRepository
    ) {
        repository.updateRecordingResult(
            id = recordingId,
            title = result.title,
            summary = result.summary,
            keyPoints = result.keyPoints.joinToString("\n"),
            status = RecordingStatus.COMPLETED,
            processedBy = processedBy
        )

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val activeDedupEntries = repository.getRecentActiveForDedup().toMutableList()
        val displayTrigger = ManualActionSuggestionExtractor.leadingDisplayTrigger(transcription)
        for (action in result.actionItems) {
            val actionText = withDisplayTrigger(action.text, displayTrigger)
            val actionType = validateActionType(action.actionType)
            if (!ActionQualityGate.isActionable(cleanText = actionText, actionType = actionType)) {
                Log.i(TAG, "Skipping non-actionable recording action: '$actionText' [${action.actionType}]")
                continue
            }
            val dueDate = action.dueDate?.let {
                try { dateFormat.parse(it)?.time } catch (_: Exception) { null }
            }

            val duplicate = DuplicateHeuristics.findLikelyDuplicate(
                text = actionText,
                existing = activeDedupEntries,
                newDueDate = dueDate
            )
            if (duplicate != null && duplicate.status == EntryStatus.PENDING) {
                Log.i(TAG, "Skipping recording action duplicate of pending entry ${duplicate.id}: '$actionText'")
                continue
            }

            val entry = DiaryEntry(
                text = actionText,
                keyword = "grabación",
                category = "Grabación",
                confidence = confidence,
                source = source,
                duration = 0,
                cleanText = actionText,
                actionType = actionType,
                priority = validatePriority(action.priority),
                dueDate = dueDate,
                wasReviewedByLLM = true,
                llmConfidence = confidence,
                processingBackend = when (processedBy.lowercase(Locale.getDefault())) {
                    "cloud", "gemini", "gemini_cloud" -> EntryProcessingBackend.CLOUD
                    "local", "gemma", "gemma_local" -> EntryProcessingBackend.LOCAL
                    else -> null
                },
                sourceRecordingId = recordingId,
                status = EntryStatus.PENDING
            )
            val insertedId = repository.insert(entry)
            val insertedEntry = entry.copy(id = insertedId)
            activeDedupEntries += insertedEntry

            if (duplicate != null && duplicate.status == EntryStatus.SUGGESTED) {
                repository.markDuplicate(duplicate.id, insertedId)
                Log.i(TAG, "Hiding suggested duplicate ${duplicate.id} in favor of recording action $insertedId")
            }
        }
    }

    private fun withDisplayTrigger(text: String, displayTrigger: String?): String {
        val trimmed = text.trim()
        if (displayTrigger == null || trimmed.isBlank()) return trimmed
        if (trimmed.startsWith(displayTrigger, ignoreCase = true)) return trimmed
        return "$displayTrigger ${trimmed.replaceFirstChar { it.lowercase(Locale.getDefault()) }}"
    }

    // ── Duplicate detection (LLM-based when possible) ──

    private suspend fun checkActionsForDuplicates(recordingId: Long, repository: DiaryRepository) {
        try {
            val existing = repository.getRecentPendingForDedup()
            if (existing.isEmpty()) return

            val actions = repository.getByRecordingIdOnce(recordingId)
            if (actions.isEmpty()) return

            // Build existing entries summary for LLM
            val entriesList = existing.take(20).joinToString("\n") { "${it.id}: ${it.displayText}" }

            for (action in actions) {
                val actionText = action.displayText

                val heuristicDuplicate = DuplicateHeuristics.findLikelyDuplicate(
                    text = actionText,
                    existing = existing,
                    ignoreId = action.id,
                    newDueDate = action.dueDate
                )
                if (heuristicDuplicate != null) {
                    repository.markDuplicate(action.id, heuristicDuplicate.id)
                    Log.i(TAG, "Heuristic duplicate: '$actionText' ≈ '${heuristicDuplicate.displayText}'")
                    continue
                }

                // Try LLM dedup (Cloud or Gemma)
                val duplicateId = tryLlmDedup(actionText, entriesList, existing)
                if (duplicateId != null) {
                    repository.markDuplicate(action.id, duplicateId)
                    Log.i(TAG, "Duplicate: '$actionText' ≈ '${existing.first { it.id == duplicateId }.displayText}'")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Duplicate check failed for recording $recordingId", e)
        }
    }

    private suspend fun tryLlmDedup(
        actionText: String,
        entriesList: String,
        existing: List<DiaryEntry>
    ): Long? {
        val prompt = """Compara esta nueva tarea con las existentes y dime si es un DUPLICADO.
Responde SOLO con JSON: {"duplicateOfId": ID_NUMBER o null}

Nueva tarea: "$actionText"

Tareas existentes:
$entriesList

Reglas:
- DUPLICADO = la MISMA tarea concreta: mismo verbo + mismo objeto/persona específica
- "Llamar a Juan" y "Telefonear a Juan" → SÍ duplicado (misma persona)
- "Comprar ajos" y "Comprar un coche" → NO duplicado (objetos distintos)
- "Llamar al dentista" y "Llamar a mi hermana" → NO duplicado (personas distintas)
- Compartir solo el verbo (comprar, llamar, enviar) NO es suficiente para ser duplicado
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
                val result = parseDedupResponse(response.text, existing)
                if (result != null) return result
            } catch (e: Exception) {
                Log.d(TAG, "Cloud dedup failed: ${e.message}")
            }
        }

        // Try local model
        if (GemmaClient.isModelAvailable(context)) {
            try {
                val response = GemmaClient.generate(context, prompt, maxTokens = 64)
                val result = parseDedupResponse(response, existing)
                if (result != null) return result
            } catch (e: Exception) {
                Log.d(TAG, "Local model dedup failed: ${e.message}")
            }
        }

        return null
    }

    private fun parseDedupResponse(responseText: String?, existing: List<DiaryEntry>): Long? {
        if (responseText.isNullOrBlank()) return null
        val jsonStr = JsonRepair.extractAndRepair(responseText)
        return try {
            val result = json.decodeFromString<DedupResult>(jsonStr)
            if (result.duplicateOfId != null && existing.any { it.id == result.duplicateOfId }) {
                result.duplicateOfId
            } else null
        } catch (_: Exception) { null }
    }

    private fun getApiKey(): String? =
        context.getSharedPreferences("daily_summary", Context.MODE_PRIVATE)
            .getString("gemini_api_key", null)

    private fun validateActionType(type: String): String = when (type.uppercase()) {
        "CALL", "BUY", "SEND", "EVENT", "REVIEW", "TALK_TO", "GENERIC" -> type.uppercase()
        else -> "GENERIC"
    }

    private fun validatePriority(priority: String): String = when (priority.uppercase()) {
        "LOW", "NORMAL", "HIGH", "URGENT" -> priority.uppercase()
        else -> "NORMAL"
    }

    @Serializable
    private data class RecordingAnalysis(
        val title: String,
        val summary: String,
        val keyPoints: List<String> = emptyList(),
        val actionItems: List<ActionItem> = emptyList()
    )

    @Serializable
    private data class ActionItem(
        val text: String,
        val actionType: String = "GENERIC",
        val priority: String = "NORMAL",
        val dueDate: String? = null
    )

    @Serializable
    private data class DedupResult(val duplicateOfId: Long? = null)
}
