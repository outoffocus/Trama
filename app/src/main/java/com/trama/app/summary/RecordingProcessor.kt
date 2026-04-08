package com.trama.app.summary

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.trama.app.GeminiConfig
import com.google.ai.client.generativeai.type.generationConfig
import com.trama.shared.data.DiaryRepository
import com.trama.shared.model.DiaryEntry
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
            saveResult(recordingId, result, source, "CLOUD", 0.9f, repository)

            Log.i(TAG, "Recording $recordingId processed via Cloud: '${result.title}', ${result.actionItems.size} actions")
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
                retryWithSimplePrompt(transcription)
            }

            repository.deleteByRecordingId(recordingId)
            saveResult(recordingId, result, source, "LOCAL", 0.8f, repository)

            Log.i(TAG, "Recording $recordingId processed via local model: '${result.title}', ${result.actionItems.size} actions")
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
    private suspend fun retryWithSimplePrompt(transcription: String): RecordingAnalysis {
        Log.i(TAG, "Retrying with simplified prompts")

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Calendar.getInstance().time)

        // Call 1: title + summary (plain text, no JSON needed)
        val titlePrompt = """Escribe un titulo breve para esta nota.
- Maximo 8 palabras
- Debe describir el tema principal
- Responde SOLO con el titulo, sin comillas ni texto extra

$transcription"""
        val title = GemmaClient.generate(context, titlePrompt, maxTokens = 32)
            ?.trim()?.removeSurrounding("\"")?.take(80)
            ?: "Nota de voz"

        val summaryPrompt = """Resume esta nota en 2 o 3 frases.
- Se fiel al contenido
- No inventes informacion
- Responde SOLO con el resumen, sin encabezados ni viñetas

$transcription"""
        val summary = GemmaClient.generate(context, summaryPrompt, maxTokens = 256)
            ?.trim() ?: transcription.take(200)

        // Call 2: extract actions as simple JSON array
        val actionsPrompt = """Extrae las tareas o cosas por hacer de este texto.
Responde SOLO con un array JSON valido y nada mas.
Ejemplo: [{"text":"Comprar leche","type":"BUY"}]
Si no hay tareas, responde [].
Tipos validos: CALL, BUY, SEND, EVENT, REVIEW, TALK_TO, GENERIC
Reglas:
- text debe ser breve, claro y accionable
- corrige errores obvios de transcripcion
- no inventes tareas
- no incluyas contexto innecesario
- si una fecha aparece de forma implicita o poco clara, ignorala
Hoy es $today.

Texto: "$transcription""""

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
            items.map { ActionItem(text = it.text, actionType = it.type ?: "GENERIC") }
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

        return """Analiza esta transcripcion de una grabacion de voz y devuelve SOLO un objeto JSON valido.
- No añadas explicaciones, markdown, backticks ni texto fuera del JSON.
- No inventes hechos, fechas ni tareas.

Formato exacto:
{
  "title": "Título breve y descriptivo (max 8 palabras)",
  "summary": "Resumen de 2-3 párrafos del contenido principal",
  "keyPoints": ["Punto clave 1", "Punto clave 2"],
  "actionItems": [
    {
      "text": "Descripción de la tarea",
      "actionType": "CALL|BUY|SEND|EVENT|REVIEW|TALK_TO|GENERIC",
      "priority": "LOW|NORMAL|HIGH|URGENT",
      "dueDate": "YYYY-MM-DD o null"
    }
  ]
}

Reglas:
- title:
  - resume el tema principal en pocas palabras
  - maximo 8 palabras
  - no uses comillas
- summary:
  - resume el contenido principal en 2 o 3 parrafos cortos
  - tono neutro y fiel al contenido
  - incluye contexto, decisiones o temas tratados solo si aparecen de verdad
- keyPoints:
  - maximo 7 puntos
  - frases cortas con informacion realmente importante
  - si hay poco contenido, usa menos puntos
- actionItems:
  - incluye TODAS las tareas, compromisos o cosas por hacer mencionadas claramente
  - si no hay tareas, usa []
  - text: acción limpia y concisa
  - actionType: CALL=llamar, BUY=comprar, SEND=enviar, EVENT=cita/reunión, REVIEW=revisar, TALK_TO=hablar con, GENERIC=otro
  - dueDate: SOLO si mencionan una fecha o momento temporal claro y explicito (hoy, mañana, lunes, 5 de abril, etc.), convertir a YYYY-MM-DD. Hoy=$today, mañana=$tomorrow. Si NO mencionan ninguna fecha concreta, devuelve null. "Recordar" o "no olvidar" NO implican fecha
  - priority: URGENT si urgente/ya/ahora/cuanto antes. HIGH si importante. LOW si cuando pueda. NORMAL en el resto
  - no crees dos tareas si en realidad es la misma accion expresada dos veces

Transcripción:
\"\"\"
$transcription
\"\"\""""
    }


    private fun parseResponse(responseText: String): RecordingAnalysis {
        val jsonStr = JsonRepair.extractAndRepair(responseText)
        return json.decodeFromString<RecordingAnalysis>(jsonStr)
    }

    private suspend fun saveResult(
        recordingId: Long,
        result: RecordingAnalysis,
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
        for (action in result.actionItems) {
            val dueDate = action.dueDate?.let {
                try { dateFormat.parse(it)?.time } catch (_: Exception) { null }
            }
            repository.insert(
                DiaryEntry(
                    text = action.text,
                    keyword = "grabación",
                    category = "Grabación",
                    confidence = confidence,
                    source = source,
                    duration = 0,
                    cleanText = action.text,
                    actionType = validateActionType(action.actionType),
                    priority = validatePriority(action.priority),
                    dueDate = dueDate,
                    wasReviewedByLLM = true,
                    llmConfidence = confidence,
                    sourceRecordingId = recordingId,
                    status = EntryStatus.SUGGESTED
                )
            )
        }
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
