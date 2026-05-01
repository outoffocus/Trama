package com.trama.app.summary

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.trama.app.GeminiConfig
import com.google.ai.client.generativeai.type.generationConfig
import com.trama.app.diagnostics.CaptureLog
import com.trama.app.service.EntryProcessingState
import com.trama.shared.data.DiaryRepository
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryActionType
import com.trama.shared.model.EntryProcessingBackend
import com.trama.shared.model.EntryPriority
import com.trama.shared.model.EntryStatus
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer
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

    suspend fun process(entryId: Long, text: String, repository: DiaryRepository): Boolean {
        val existingEntry = repository.getByIdOnce(entryId)
        val originalText = existingEntry?.text?.takeIf { it.isNotBlank() } ?: text
        val normalizedInput = existingEntry?.correctedText?.takeIf { it.isNotBlank() } ?: text
        val processingText = normalizedInput.ifBlank { originalText }

        val recentContext = buildRecentContext(entryId, repository)

        val outcome = tryProcess(
            entryId = entryId,
            originalText = originalText,
            normalizedInput = processingText,
            recentContext = recentContext,
            repository = repository
        )
        val result = outcome?.primary
        // Only run the heuristic auto-splitter when the LLM was unavailable or
        // returned nothing usable. If the LLM already produced an actionable
        // result, trust it — re-splitting with regex was a source of false
        // positives (e.g. splitting on every verb in a single coherent request).
        val splitCleanTexts = if (outcome == null) {
            repository.updateProcessingBackend(entryId, EntryProcessingBackend.HEURISTIC)
            maybeAutoSplitEntry(entryId, processingText, null, repository)
        } else {
            emptyList()
        }
        var acceptedForTimeline = splitCleanTexts.isNotEmpty()
        val heuristicFallback = if (outcome == null && splitCleanTexts.isEmpty()) {
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
                CaptureLog.event(
                    gate = CaptureLog.Gate.LLM,
                    result = CaptureLog.Result.OK,
                    text = result.cleanText,
                    meta = llmDecisionMeta(
                        entryId = entryId,
                        result = result,
                        decision = "accepted",
                        route = EntryStatus.PENDING,
                        backend = "llm"
                    ) + mapOf(
                        "id" to entryId,
                        "kind" to result.kind,
                        "actionType" to result.actionType,
                        "confidence" to "%.2f".format(result.confidence),
                        "usefulness" to "%.2f".format(result.usefulnessScore),
                        "actionability" to "%.2f".format(result.actionabilityScore)
                    )
                )
                acceptedForTimeline = true
                // Persist any extra actions the LLM extracted from the same note.
                persistLLMExtras(entryId, outcome.extras, repository)
            } else {
                repository.updateAIProcessing(
                    id = entryId,
                    cleanText = result.cleanText,
                    actionType = result.actionType,
                    dueDate = result.dueDate,
                    priority = result.priority,
                    confidence = result.confidence
                )
                routeRejected(entryId, result, repository)
                Log.i(
                    TAG,
                    "Routing entry $entryId after utility classification: '${result.cleanText}' " +
                        "(kind=${result.kind}, actionable=${result.isActionable}, confidence=${result.confidence})"
                )
                CaptureLog.event(
                    gate = CaptureLog.Gate.LLM,
                    result = CaptureLog.Result.REJECT,
                    text = result.cleanText,
                    meta = llmDecisionMeta(
                        entryId = entryId,
                        result = result,
                        decision = "rejected",
                        route = rejectedStatus(result),
                        backend = "llm"
                    ) + mapOf(
                        "id" to entryId,
                        "kind" to result.kind,
                        "isActionable" to result.isActionable,
                        "confidence" to "%.2f".format(result.confidence),
                        "usefulness" to "%.2f".format(result.usefulnessScore),
                        "actionability" to "%.2f".format(result.actionabilityScore),
                        "discardReason" to result.discardReason,
                        "route" to rejectedStatus(result)
                    )
                )
                return false
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
                CaptureLog.event(
                    gate = CaptureLog.Gate.LLM,
                    result = CaptureLog.Result.OK,
                    text = heuristicFallback.cleanText,
                    meta = llmDecisionMeta(
                        entryId = entryId,
                        result = heuristicFallback,
                        decision = "accepted",
                        route = EntryStatus.PENDING,
                        backend = "heuristic_fallback"
                    )
                )
                acceptedForTimeline = true
            } else {
                repository.updateAIProcessing(
                    id = entryId,
                    cleanText = heuristicFallback.cleanText,
                    actionType = heuristicFallback.actionType,
                    dueDate = heuristicFallback.dueDate,
                    priority = heuristicFallback.priority,
                    confidence = heuristicFallback.confidence
                )
                routeRejected(entryId, heuristicFallback, repository)
                Log.i(
                    TAG,
                    "Routing heuristic fallback to review queue for entry $entryId: " +
                        "'${heuristicFallback.cleanText}' (confidence=${heuristicFallback.confidence})"
                )
                CaptureLog.event(
                    gate = CaptureLog.Gate.LLM,
                    result = CaptureLog.Result.REJECT,
                    text = heuristicFallback.cleanText,
                    meta = llmDecisionMeta(
                        entryId = entryId,
                        result = heuristicFallback,
                        decision = "rejected",
                        route = rejectedStatus(heuristicFallback),
                        backend = "heuristic_fallback"
                    )
                )
                return false
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
        return acceptedForTimeline && repository.isVisiblePendingEntry(entryId)
    }

    private suspend fun DiaryRepository.isVisiblePendingEntry(entryId: Long): Boolean {
        val entry = getByIdOnce(entryId) ?: return false
        return entry.status == EntryStatus.PENDING && entry.duplicateOfId == null
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
                    processingBackend = originalEntry.processingBackend ?: EntryProcessingBackend.HEURISTIC,
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
        entryId: Long,
        originalText: String,
        normalizedInput: String,
        recentContext: String,
        repository: DiaryRepository
    ): LLMOutcome? {
        // Try Cloud
        val apiKey = getApiKey()
        if (!apiKey.isNullOrBlank()) {
            try {
                EntryProcessingState.updateBackend(entryId, EntryProcessingState.Backend.CLOUD)
                val outcome = processWithCloud(originalText, normalizedInput, recentContext, apiKey)
                repository.updateProcessingBackend(entryId, EntryProcessingBackend.CLOUD)
                return outcome
            } catch (e: Exception) {
                Log.w(TAG, "Cloud failed: ${e.javaClass.simpleName}", e)
            }
        }

        // Try local on-device model
        if (GemmaClient.isModelAvailable(context)) {
            try {
                EntryProcessingState.updateBackend(entryId, EntryProcessingState.Backend.LOCAL)
                val outcome = processWithLocalModel(originalText, normalizedInput, recentContext)
                repository.updateProcessingBackend(entryId, EntryProcessingBackend.LOCAL)
                return outcome
            } catch (e: Exception) {
                Log.w(TAG, "Local model failed", e)
            }
        }

        return null
    }

    /**
     * Collects live tasks (PENDING), today's completed tasks, and the user's
     * current place (if inside an active dwell) so the LLM can:
     *   - avoid re-extracting tasks already captured,
     *   - resolve references like "esa tarea" or "lo de ayer",
     *   - resolve "aquí" / "en el curro" to a concrete place name,
     *   - reuse consistent names for people/places mentioned earlier.
     *
     * Gemma 3n E4B handles a 32K context comfortably, so we lean into richer
     * grounding instead of aggressive truncation.
     */
    /**
     * Wraps the raw context string produced by [buildRecentContext] into the
     * "CONTEXTO DEL USUARIO (hoy)" block with the behavioural rules that
     * reference it. Safe to pass the result directly into `{{recentContext}}`
     * placeholders (empty string when there is no context to show).
     */
    internal fun buildContextBlock(recentContext: String): String {
        if (recentContext.isBlank()) return ""
        return buildString {
            appendLine("CONTEXTO DEL USUARIO (hoy):")
            appendLine(recentContext)
            appendLine()
            appendLine("Reglas sobre el CONTEXTO:")
            appendLine("- Si la nota es una referencia a una tarea ya en Tareas pendientes o Completadas hoy (ej: \"eso que dije de Pedro\", \"lo de la reunion\"), responde con isActionable=false y confidence<=0.3. No dupliques.")
            appendLine("- Si la nota menciona personas o lugares que ya aparecen en el contexto, reutiliza la misma grafía literal (evita duplicados tipo \"Pedro\" vs \"Pedrito\").")
            appendLine("- Si hay \"Lugar actual del usuario\" y la nota dice \"aquí\", \"en el curro\", \"en casa\", resuelve la referencia a ese lugar literal en cleanText.")
            appendLine()
        }
    }

    internal suspend fun buildRecentContext(
        entryId: Long,
        repository: DiaryRepository
    ): String {
        val pendingItems = try {
            repository.getPendingOnce()
                .asSequence()
                .filter { it.id != entryId }
                .map { (it.cleanText ?: it.displayText).trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_PENDING_CONTEXT)
                .toList()
        } catch (e: Exception) {
            Log.d(TAG, "Could not load pending entries for context", e)
            emptyList()
        }

        val completedItems = try {
            val today = com.trama.shared.util.DayRange.today()
            (repository.getCompletedByCompletedAt(today.startMs, today.endExclusiveMs)
                .firstOrNull() ?: emptyList())
                .asSequence()
                .filter { it.id != entryId }
                .map { (it.cleanText ?: it.displayText).trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_COMPLETED_CONTEXT)
                .toList()
        } catch (e: Exception) {
            Log.d(TAG, "Could not load completed entries for context", e)
            emptyList()
        }

        val placeLine = try {
            resolveCurrentPlace(repository)
        } catch (e: Exception) {
            Log.d(TAG, "Could not resolve current place for context", e)
            null
        }

        val sections = mutableListOf<String>()
        if (placeLine != null) sections += placeLine
        if (pendingItems.isNotEmpty()) {
            sections += "Tareas pendientes:\n" + pendingItems.joinToString("\n") { "- $it" }
        }
        if (completedItems.isNotEmpty()) {
            sections += "Completadas hoy:\n" + completedItems.joinToString("\n") { "- $it" }
        }
        return sections.joinToString("\n\n")
    }

    /**
     * Resolves the user's current place when an active dwell has a known place
     * within ~80m of the anchor. Returns a short line suitable for prompt context
     * or null when we have no reliable signal.
     */
    private suspend fun resolveCurrentPlace(repository: DiaryRepository): String? {
        val dwell = repository.getDwellDetectionState() ?: return null
        if (!dwell.active) return null
        val lat = dwell.anchorLat ?: return null
        val lon = dwell.anchorLon ?: return null

        val deltaLat = PLACE_RADIUS_M / 111_320.0
        val deltaLon = PLACE_RADIUS_M /
            (111_320.0 * kotlin.math.cos(Math.toRadians(lat)).coerceAtLeast(0.1))
        val candidates = repository.findPlacesInBoundingBox(
            minLat = lat - deltaLat,
            maxLat = lat + deltaLat,
            minLon = lon - deltaLon,
            maxLon = lon + deltaLon
        )
        val place = candidates.minByOrNull {
            val dy = (it.latitude - lat) * 111_320.0
            val dx = (it.longitude - lon) *
                111_320.0 * kotlin.math.cos(Math.toRadians(lat))
            kotlin.math.sqrt(dx * dx + dy * dy)
        } ?: return null

        val tag = when {
            place.isHome -> " (casa)"
            place.isWork -> " (trabajo)"
            else -> ""
        }
        return "Lugar actual del usuario: ${place.name}$tag"
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
        val passesGate = isActionableAfterValidation(
            cleanText = suggestion.text,
            actionType = suggestion.actionType,
            modelIsActionable = true
        )
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
        recentContext: String,
        apiKey: String
    ): LLMOutcome {
        val prompt = buildPrompt(originalText, normalizedInput, recentContext)

        val model = GenerativeModel(
            modelName = GeminiConfig.MODEL_NAME,
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.1f
                maxOutputTokens = 512
            }
        )

        val response = model.generateContent(prompt)
        val responseText = response.text?.trim()
            ?: throw Exception("Empty Gemini response")

        Log.d(TAG, "Cloud OK: $responseText")
        return parseResult(responseText, 1.0f, normalizedInput)
    }

    private suspend fun processWithLocalModel(
        originalText: String,
        normalizedInput: String,
        recentContext: String
    ): LLMOutcome? {
        // Use the same structured prompt as Cloud
        val prompt = buildPrompt(originalText, normalizedInput, recentContext)
        val responseText = GemmaClient.generate(context, prompt, maxTokens = 512, responsePrefix = "{") ?: return null
        Log.d(TAG, "Local model response: $responseText")

        return try {
            // Try parsing as JSON (same format as Cloud)
            parseResult(responseText, 0.85f, normalizedInput)
        } catch (e: Exception) {
            // Fallback: use response as clean text + keyword-based inference
            Log.d(TAG, "Local model JSON parse failed, using text fallback", e)
            val cleanText = JsonRepair.extractAndRepair(responseText).trim().removeSurrounding("\"")
            val validatedCleanText = cleanText.takeIf { it.isNotBlank() } ?: return null
            val truncated = withDisplayTrigger(
                validatedCleanText.take(200),
                ManualActionSuggestionExtractor.leadingDisplayTrigger(normalizedInput)
            )
            val inferredActionType = inferActionType(normalizedInput)
            val passesGate = isActionableAfterValidation(
                cleanText = truncated,
                actionType = inferredActionType,
                modelIsActionable = true
            )
            LLMOutcome(
                primary = ProcessingResult(
                    cleanText = truncated,
                    actionType = inferredActionType,
                    dueDate = null,
                    priority = "NORMAL",
                    confidence = if (passesGate) 0.7f else 0.25f,
                    isActionable = passesGate
                ),
                extras = emptyList()
            )
        }
    }

    /** Simple keyword-based action type detection as fallback. */
    private fun inferActionType(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("llamar") || lower.contains("llama") -> "CALL"
            lower.contains("comprar") || lower.contains("hacer la compra") ||
                Regex("""\bcompra\b""").containsMatchIn(lower) -> "BUY"
            lower.contains("enviar") || lower.contains("mandar") ||
                lower.contains("contestar") || lower.contains("responder") -> "SEND"
            lower.contains("reunión") || lower.contains("cita") || lower.contains("evento") -> "EVENT"
            lower.contains("revisar") || lower.contains("mirar") -> "REVIEW"
            lower.contains("hablar con") || lower.contains("decir a") || lower.contains("decirle") -> "TALK_TO"
            else -> "GENERIC"
        }
    }

    private fun buildPrompt(
        originalText: String,
        normalizedInput: String,
        recentContext: String = ""
    ): String {
        val today = dateFormat.format(Calendar.getInstance().time)
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tomorrowStr = dateFormat.format(tomorrow.time)
        val contextBlock = buildContextBlock(recentContext)
        return PromptTemplateStore.render(
            context,
            PromptTemplateStore.ACTION_ITEM,
            mapOf(
                "text" to normalizedInput,
                "originalText" to originalText,
                "normalizedInput" to normalizedInput,
                "today" to today,
                "tomorrow" to tomorrowStr,
                "recentContext" to contextBlock
            )
        )
    }


    private fun parseResult(
        responseText: String,
        confidenceMultiplier: Float,
        triggerSourceText: String = ""
    ): LLMOutcome {
        val parsed = json.decodeFromString<LLMResponse>(JsonRepair.extractAndRepair(responseText))
        val parsedActions = parsed.normalizedActions()
        require(parsedActions.isNotEmpty()) { "LLM returned no cleanText or actions" }
        val displayTrigger = ManualActionSuggestionExtractor.leadingDisplayTrigger(triggerSourceText)
        val firstAction = parsedActions.first()

        val primary = buildProcessingResult(
            cleanText = firstAction.cleanText.trim(),
            actionType = firstAction.actionType,
            dueDate = firstAction.dueDate,
            priority = firstAction.priority,
            confidence = firstAction.effectiveConfidence(parsed.confidence) * confidenceMultiplier,
            modelIsActionable = parsed.isActionable,
            kind = normalizeKind(parsed.kind, parsed.isActionable),
            usefulnessScore = parsed.usefulnessScore,
            actionabilityScore = parsed.actionabilityScore,
            discardReason = parsed.discardReason,
            displayTrigger = displayTrigger
        )
        val extras = if (primary.kind == KIND_TASK) parsedActions.drop(1) else emptyList()
        val llmExtras = extras
            .filter { it.cleanText.isNotBlank() }
            .map { extra ->
                buildProcessingResult(
                    cleanText = extra.cleanText.trim(),
                    actionType = extra.actionType,
                    dueDate = extra.dueDate,
                    priority = extra.priority,
                    confidence = extra.effectiveConfidence(parsed.confidence) * confidenceMultiplier,
                    modelIsActionable = true,
                    kind = KIND_TASK,
                    usefulnessScore = primary.usefulnessScore,
                    actionabilityScore = primary.actionabilityScore,
                    discardReason = null,
                    displayTrigger = displayTrigger
                )
            }
            .filterNot { extra -> actionsOverlap(primary.cleanText, extra.cleanText) }
        val heuristicExtras = if (primary.kind == KIND_TASK) {
            buildHeuristicSupplementalActions(
                sourceText = triggerSourceText,
                primary = primary,
                existingExtras = llmExtras
            )
        } else {
            emptyList()
        }
        val processingExtras = (llmExtras + heuristicExtras)
            .distinctBy { normalizeForComparison(it.cleanText) }
        return LLMOutcome(primary = primary, extras = processingExtras)
    }

    /**
     * The LLM sometimes returns a valid primary task but omits extraActions for
     * compound utterances. Use the conservative local splitter only as a supplement:
     * keep candidates that are not already covered by the primary or LLM extras.
     */
    private fun buildHeuristicSupplementalActions(
        sourceText: String,
        primary: ProcessingResult,
        existingExtras: List<ProcessingResult>
    ): List<ProcessingResult> {
        val suggestions = ManualActionSuggestionExtractor.extract(sourceText)
            .distinctBy { normalizeForComparison(it.text) }
        if (suggestions.size < 2) return emptyList()

        val existing = listOf(primary.cleanText) + existingExtras.map { it.cleanText }
        return suggestions
            .filterNot { suggestion ->
                existing.any { existingText -> actionsOverlap(existingText, suggestion.text) }
            }
            .take(3)
            .map { suggestion ->
                buildProcessingResult(
                    cleanText = suggestion.text,
                    actionType = suggestion.actionType,
                    dueDate = suggestion.dueDate?.let { dateFormat.format(it) },
                    priority = suggestion.priority,
                    confidence = primary.confidence,
                    modelIsActionable = true,
                    kind = KIND_TASK,
                    usefulnessScore = primary.usefulnessScore,
                    actionabilityScore = primary.actionabilityScore,
                    discardReason = null,
                    displayTrigger = null
                )
            }
    }

    @Suppress("unused")
    private fun buildProcessingResult(
        cleanText: String,
        actionType: String,
        dueDate: String?,
        priority: String,
        confidence: Float,
        modelIsActionable: Boolean,
        displayTrigger: String? = null
    ): ProcessingResult = buildProcessingResult(
        cleanText = cleanText,
        actionType = actionType,
        dueDate = dueDate,
        priority = priority,
        confidence = confidence,
        modelIsActionable = modelIsActionable,
        kind = if (modelIsActionable) KIND_TASK else KIND_UNCLEAR,
        usefulnessScore = confidence,
        actionabilityScore = if (modelIsActionable) confidence else 0f,
        discardReason = null,
        displayTrigger = displayTrigger
    )

    private fun buildProcessingResult(
        cleanText: String,
        actionType: String,
        dueDate: String?,
        priority: String,
        confidence: Float,
        modelIsActionable: Boolean,
        kind: String = KIND_TASK,
        usefulnessScore: Float? = null,
        actionabilityScore: Float? = null,
        discardReason: String? = null,
        displayTrigger: String? = null
    ): ProcessingResult {
        val canonicalCleanText = canonicalActionText(cleanText)
        val displayText = withDisplayTrigger(canonicalCleanText, displayTrigger)
        val validatedActionType = validateActionType(actionType)
        val normalizedKind = normalizeKind(kind, modelIsActionable)
        val effectiveUsefulness = usefulnessScore ?: confidence
        val effectiveActionability = actionabilityScore ?: if (modelIsActionable) confidence else 0f
        val modelAcceptsAsTask = normalizedKind == KIND_TASK &&
            modelIsActionable &&
            !looksLikeNegatedObligation(cleanText) &&
            effectiveUsefulness >= USEFULNESS_THRESHOLD &&
            effectiveActionability >= ACTIONABILITY_THRESHOLD
        val passesGate = isActionableAfterValidation(
            cleanText = displayText,
            actionType = validatedActionType,
            modelIsActionable = modelAcceptsAsTask
        )
        val effectiveConfidence = if (!passesGate) minOf(confidence, 0.29f) else confidence
        return ProcessingResult(
            cleanText = displayText,
            actionType = validatedActionType,
            dueDate = parseDateString(dueDate),
            priority = validatePriority(priority),
            confidence = effectiveConfidence,
            isActionable = passesGate,
            kind = normalizedKind,
            usefulnessScore = effectiveUsefulness,
            actionabilityScore = effectiveActionability,
            discardReason = discardReason
        )
    }

    private fun looksLikeNegatedObligation(text: String): Boolean {
        val normalized = Normalizer.normalize(text.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
        return Regex("""\b(?:ya\s+)?no\s+(?:tengo|tenemos|tienes|hay)\s+que\b""")
            .containsMatchIn(normalized) ||
            Regex("""\bno\s+(?:debo|debemos|necesito|necesitamos)\b""")
                .containsMatchIn(normalized)
    }

    private fun withDisplayTrigger(text: String, displayTrigger: String?): String {
        val trimmed = text.trim()
        if (displayTrigger == null || trimmed.isBlank()) return trimmed
        if (trimmed.startsWith(displayTrigger, ignoreCase = true)) return trimmed
        return "$displayTrigger ${trimmed.replaceFirstChar { it.lowercase(Locale.getDefault()) }}"
    }

    private fun canonicalActionText(text: String): String {
        val normalized = text
            .trim()
            .replace(Regex("""\btenemso\b""", RegexOption.IGNORE_CASE), "tenemos")
            .replace(Regex("""\btenés\b""", RegexOption.IGNORE_CASE), "tenes")
        if (normalized.isBlank()) return normalized

        val actionableTrigger = Regex(
            """\b(?:tengo|tenemos|tenes|tienes)\s+que\s+|\bhay\s+que\s+|\b(?:debo|debemos|deberia|debería|necesito|necesitamos)\s+""",
            RegexOption.IGNORE_CASE
        )
        val match = actionableTrigger.findAll(normalized).lastOrNull()
        val actionClause = match
            ?.let { normalized.substring(it.range.last + 1).trim() }
            ?.trimStart(',', ';', ':', '-', ' ')
            ?.takeIf { it.length >= 6 }
            ?: normalized

        return actionClause
            .trim()
            .trimEnd('.', ',', ';', ':', '!', '?', '¿', '¡')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    private fun actionsOverlap(left: String, right: String): Boolean {
        val leftTokens = normalizeForComparison(left).split(" ").filter { it.length >= 3 }.toSet()
        val rightTokens = normalizeForComparison(right).split(" ").filter { it.length >= 3 }.toSet()
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return false
        val shared = leftTokens.intersect(rightTokens)
        val smaller = minOf(leftTokens.size, rightTokens.size)
        if (smaller <= 2) return shared.size == smaller
        return shared.size >= maxOf(2, (smaller * 0.6f).toInt())
    }

    private fun normalizeForComparison(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\b(?:tengo|tenemos|tenemso|tienes|tenes|deberia|debería|hacer|que|para|con|los|las|una|uno|mis|mi|el|la|de|a|y)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun shouldAcceptAsTask(result: ProcessingResult): Boolean =
        result.isActionable && result.confidence >= ACTIONABLE_CONFIDENCE_THRESHOLD

    private suspend fun routeRejected(
        entryId: Long,
        result: ProcessingResult,
        repository: DiaryRepository
    ) {
        if (rejectedStatus(result) == EntryStatus.SUGGESTED) {
            repository.markSuggested(entryId)
        } else {
            repository.markDiscarded(entryId)
        }
    }

    private fun rejectedStatus(result: ProcessingResult): String =
        if (result.kind == KIND_TASK && result.isActionable) {
            EntryStatus.SUGGESTED
        } else {
            EntryStatus.DISCARDED
        }

    private fun llmDecisionMeta(
        entryId: Long,
        result: ProcessingResult,
        decision: String,
        route: String,
        backend: String
    ): Map<String, Any?> = mapOf(
        "entryId" to entryId,
        "decision" to decision,
        "route" to route,
        "backend" to backend,
        "qualityBucket" to qualityBucket(result, decision, route),
        "reviewHint" to reviewHint(result, decision, route),
        "actionType" to result.actionType,
        "kind" to result.kind,
        "isActionable" to result.isActionable,
        "confidence" to "%.2f".format(result.confidence),
        "usefulness" to "%.2f".format(result.usefulnessScore),
        "actionability" to "%.2f".format(result.actionabilityScore),
        "discardReason" to result.discardReason
    )

    private fun qualityBucket(
        result: ProcessingResult,
        decision: String,
        route: String
    ): String = when {
        decision == "accepted" && result.confidence < 0.65f -> "accepted_low_confidence"
        decision == "accepted" -> "accepted_action"
        route == EntryStatus.SUGGESTED -> "ambiguous_suggested"
        result.actionabilityScore >= 0.55f || result.usefulnessScore >= 0.65f -> "discarded_possible_false_negative"
        result.discardReason?.contains("duplic", ignoreCase = true) == true -> "discarded_duplicate_or_context"
        result.discardReason?.contains("neg", ignoreCase = true) == true ||
            result.discardReason?.contains("cancel", ignoreCase = true) == true -> "discarded_negated_or_cancelled"
        result.discardReason?.contains("ruido", ignoreCase = true) == true ||
            result.discardReason?.contains("transcrip", ignoreCase = true) == true -> "discarded_noise_or_asr"
        result.kind == KIND_DISCARD || result.kind == KIND_UNCLEAR -> "discarded_non_action"
        else -> "discarded_other"
    }

    private fun reviewHint(
        result: ProcessingResult,
        decision: String,
        route: String
    ): String = when {
        decision == "accepted" && result.confidence < 0.65f ->
            "Revisar como posible falso positivo: aceptada con confianza baja."
        decision == "accepted" ->
            "Revisar precision: confirmar si esta tarea era realmente util."
        route == EntryStatus.SUGGESTED ->
            "Revisar ambiguedad: el modelo vio tarea, pero no paso el contrato de aceptacion."
        result.actionabilityScore >= 0.55f || result.usefulnessScore >= 0.65f ->
            "Revisar recall: posible tarea util descartada."
        else ->
            "Revisar muestra negativa: confirmar que el descarte era correcto."
    }

    private fun normalizeKind(kind: String?, modelIsActionable: Boolean): String {
        val normalized = kind?.uppercase(Locale.getDefault())?.trim()
        return when (normalized) {
            KIND_TASK, KIND_NOTE, KIND_UNCLEAR, KIND_DISCARD -> normalized
            else -> if (modelIsActionable) KIND_TASK else KIND_UNCLEAR
        }
    }

    /**
     * Inserts additional actions the LLM extracted from the same note as sibling
     * entries, mirroring [maybeAutoSplitEntry]. Each extra passes through the
     * same deterministic gate as the primary; rejected ones are skipped.
     */
    private suspend fun persistLLMExtras(
        entryId: Long,
        extras: List<ProcessingResult>,
        repository: DiaryRepository
    ) {
        if (extras.isEmpty()) return
        val originalEntry = repository.getByIdOnce(entryId) ?: return
        for (extra in extras) {
            if (!shouldAcceptAsTask(extra)) {
                Log.i(
                    TAG,
                    "Skipping non-actionable LLM extra for entry $entryId: " +
                        "'${extra.cleanText}' (confidence=${extra.confidence})"
                )
                continue
            }
            val siblingId = repository.insert(
                DiaryEntry(
                    text = extra.cleanText,
                    keyword = originalEntry.keyword,
                    category = originalEntry.category,
                    confidence = originalEntry.confidence,
                    createdAt = System.currentTimeMillis(),
                    source = originalEntry.source,
                    duration = originalEntry.duration,
                    correctedText = null,
                    wasReviewedByLLM = true,
                    llmConfidence = extra.confidence,
                    processingBackend = originalEntry.processingBackend,
                    status = EntryStatus.PENDING,
                    actionType = extra.actionType,
                    cleanText = extra.cleanText,
                    dueDate = extra.dueDate,
                    priority = extra.priority
                )
            )
            Log.i(TAG, "Persisted LLM extra for entry $entryId as $siblingId: '${extra.cleanText}'")
            try {
                checkForDuplicates(siblingId, extra.cleanText, repository)
            } catch (e: Exception) {
                Log.w(TAG, "Duplicate check failed for LLM extra $siblingId", e)
            }
        }
    }

    /**
     * Deterministic post-LLM check: rejects cleanText strings that are clearly not
     * actionable regardless of what the model reported. Catches the well-known
     * failure modes — temporal-only fragments, verbless stubs, ASR noise — where
     * the model's textual rules are ignored.
     */
    private fun isActionableAfterValidation(
        cleanText: String,
        actionType: String,
        modelIsActionable: Boolean
    ): Boolean = ActionQualityGate.isActionable(
        cleanText = cleanText,
        actionType = actionType,
        modelIsActionable = modelIsActionable
    )

    // ── Duplicate detection ──

    private suspend fun checkForDuplicates(entryId: Long, cleanText: String, repository: DiaryRepository) {
        val existing = repository.getRecentPendingForDedup()
            .filter { it.id != entryId }

        if (existing.isEmpty()) return

        val newDueDate = repository.getByIdOnce(entryId)?.dueDate
        DuplicateHeuristics.findLikelyDuplicate(
            text = cleanText,
            existing = existing,
            ignoreId = entryId,
            newDueDate = newDueDate
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
        private const val USEFULNESS_THRESHOLD = 0.65f
        private const val ACTIONABILITY_THRESHOLD = 0.65f

        private const val KIND_TASK = "TASK"
        private const val KIND_NOTE = "NOTE"
        private const val KIND_UNCLEAR = "UNCLEAR"
        private const val KIND_DISCARD = "DISCARD"

        /** How many pending tasks to pass to the LLM as context. */
        private const val MAX_PENDING_CONTEXT = 40

        /** How many of today's completed tasks to pass as context. */
        private const val MAX_COMPLETED_CONTEXT = 20

        /** Radius for matching the active dwell anchor to a known place. */
        private const val PLACE_RADIUS_M = 80.0

    }

    data class ProcessingResult(
        val cleanText: String,
        val actionType: String,
        val dueDate: Long?,
        val priority: String,
        val confidence: Float,
        val isActionable: Boolean = true,
        val kind: String = KIND_TASK,
        val usefulnessScore: Float = 1f,
        val actionabilityScore: Float = 1f,
        val discardReason: String? = null
    )

    @Serializable
    private data class LLMResponse(
        val kind: String? = null,
        val usefulnessScore: Float? = null,
        val actionabilityScore: Float? = null,
        val discardReason: String? = null,
        val cleanText: String = "",
        val actionType: String = "GENERIC",
        val dueDate: String? = null,
        val priority: String = "NORMAL",
        val confidence: Float = 0.8f,
        val isActionable: Boolean = true,
        val actions: List<LLMAction> = emptyList(),
        val extraActions: List<LLMExtraAction> = emptyList()
    ) {
        fun normalizedActions(): List<LLMAction> {
            val explicit = actions.filter { it.cleanText.isNotBlank() }
            if (explicit.isNotEmpty()) return explicit

            val primary = cleanText
                .takeIf { it.isNotBlank() }
                ?.let {
                    LLMAction(
                        cleanText = it,
                        actionType = actionType,
                        dueDate = dueDate,
                        priority = priority,
                        confidence = confidence
                    )
                }
            return listOfNotNull(primary) + extraActions
                .filter { it.cleanText.isNotBlank() }
                .map {
                    LLMAction(
                        cleanText = it.cleanText,
                        actionType = it.actionType,
                        dueDate = it.dueDate,
                        priority = it.priority,
                        confidence = null
                    )
                }
        }
    }

    @Serializable
    private data class LLMAction(
        val cleanText: String,
        val actionType: String = "GENERIC",
        val dueDate: String? = null,
        val priority: String = "NORMAL",
        val confidence: Float? = null
    ) {
        fun effectiveConfidence(fallback: Float): Float = confidence ?: fallback
    }

    @Serializable
    private data class LLMExtraAction(
        val cleanText: String,
        val actionType: String = "GENERIC",
        val dueDate: String? = null,
        val priority: String = "NORMAL"
    )

    data class LLMOutcome(
        val primary: ProcessingResult,
        val extras: List<ProcessingResult>
    )
}
