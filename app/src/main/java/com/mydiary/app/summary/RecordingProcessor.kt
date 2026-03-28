package com.mydiary.app.summary

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.mydiary.shared.data.DiaryRepository
import com.mydiary.shared.model.DiaryEntry
import com.mydiary.shared.model.RecordingStatus
import com.mydiary.shared.model.Source
import com.mydiary.app.service.RecordingState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Processes a recording transcription:
 * 1. Tries Gemini for best results (title, summary, key points, action items)
 * 2. Falls back to local heuristic processing if Gemini is unavailable
 */
class RecordingProcessor(private val context: Context) {

    companion object {
        private const val TAG = "RecordingProcessor"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

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

        val apiKey = getApiKey()

        // Try Gemini first
        if (!apiKey.isNullOrBlank()) {
            val geminiOk = tryGemini(recordingId, recording.transcription, recording.source, apiKey, repository)
            if (geminiOk) return
        }

        // Fallback: local processing
        Log.i(TAG, "Using local processing for recording $recordingId")
        processLocally(recordingId, recording.transcription, recording.source, repository)
        RecordingState.notifyError("Procesado local (sin Gemini). Resultado básico.")
    }

    // ── Gemini ──

    private suspend fun tryGemini(
        recordingId: Long,
        transcription: String,
        source: Source,
        apiKey: String,
        repository: DiaryRepository
    ): Boolean {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Calendar.getInstance().time)
        val tomorrow = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.time)

        val prompt = """Analiza esta transcripción de una grabación de voz y responde SOLO con JSON válido:
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
- title: resumir el tema principal en pocas palabras
- summary: contexto, decisiones, temas discutidos. Neutro y conciso
- keyPoints: máximo 7 puntos. Frases cortas con la información más importante
- actionItems: TODAS las tareas, compromisos, cosas por hacer mencionadas. Si no hay, lista vacía
  - text: acción limpia y concisa
  - actionType: CALL=llamar, BUY=comprar, SEND=enviar, EVENT=cita/reunión, REVIEW=revisar, TALK_TO=hablar con, GENERIC=otro
  - dueDate: si mencionan fecha, convertir a YYYY-MM-DD. Hoy=$today, mañana=$tomorrow. Si no hay, null
  - priority: URGENT si urgente/ya/ahora. HIGH si importante. LOW si cuando pueda. NORMAL en el resto

Transcripción:
\"\"\"
$transcription
\"\"\""""

        return try {
            val model = GenerativeModel(
                modelName = "gemini-2.5-flash",
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.2f
                    maxOutputTokens = 4096
                }
            )

            val response = model.generateContent(prompt)
            val responseText = response.text?.trim() ?: throw Exception("Empty Gemini response")

            Log.d(TAG, "Gemini response: ${responseText.take(200)}")

            val jsonStr = responseText
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()

            val result = json.decodeFromString<RecordingAnalysis>(jsonStr)

            repository.updateRecordingResult(
                id = recordingId,
                title = result.title,
                summary = result.summary,
                keyPoints = result.keyPoints.joinToString("\n"),
                status = RecordingStatus.COMPLETED
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
                        confidence = 0.9f,
                        source = source,
                        duration = 0,
                        cleanText = action.text,
                        actionType = validateActionType(action.actionType),
                        priority = validatePriority(action.priority),
                        dueDate = dueDate,
                        wasReviewedByLLM = true,
                        llmConfidence = 0.9f,
                        sourceRecordingId = recordingId
                    )
                )
                Log.d(TAG, "Action created: ${action.text} (${action.actionType})")
            }

            Log.i(TAG, "Recording $recordingId processed via Gemini: '${result.title}', ${result.actionItems.size} actions")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Gemini failed for recording $recordingId, falling back to local: ${e.message}")
            false
        }
    }

    // ── Local fallback ──

    private suspend fun processLocally(
        recordingId: Long,
        transcription: String,
        source: Source,
        repository: DiaryRepository
    ) {
        try {
            val sentences = transcription
                .replace("...", ".")
                .split(Regex("[.!?]+"))
                .map { it.trim() }
                .filter { it.length > 3 }

            // Title: first meaningful words
            val title = generateLocalTitle(sentences, transcription)

            // Summary: first 3 sentences or full text if short
            val summary = if (sentences.size <= 3) {
                transcription
            } else {
                sentences.take(3).joinToString(". ") + "."
            }

            // Key points: longest/most meaningful sentences
            val keyPoints = sentences
                .filter { it.length > 15 }
                .sortedByDescending { it.length }
                .take(5)
                .joinToString("\n")

            repository.updateRecordingResult(
                id = recordingId,
                title = title,
                summary = summary,
                keyPoints = keyPoints.ifBlank { null },
                status = RecordingStatus.COMPLETED,
                processedLocally = true
            )

            // Extract action items via keyword matching
            val actions = extractLocalActions(sentences, transcription)
            for (action in actions) {
                repository.insert(
                    DiaryEntry(
                        text = action.text,
                        keyword = "grabación",
                        category = "Grabación",
                        confidence = 0.6f,
                        source = source,
                        duration = 0,
                        cleanText = action.text,
                        actionType = action.actionType,
                        priority = action.priority,
                        dueDate = parseDateHeuristic(action.dateHint),
                        wasReviewedByLLM = false,
                        llmConfidence = 0.0f,
                        sourceRecordingId = recordingId
                    )
                )
                Log.d(TAG, "Local action: ${action.text} (${action.actionType})")
            }

            Log.i(TAG, "Recording $recordingId processed locally: '$title', ${actions.size} actions")
        } catch (e: Exception) {
            Log.e(TAG, "Local processing also failed for $recordingId", e)
            repository.updateRecordingStatus(recordingId, RecordingStatus.FAILED)
            RecordingState.notifyError("Error procesando grabación: ${e.message?.take(100)}")
        }
    }

    private fun generateLocalTitle(sentences: List<String>, full: String): String {
        // Take first sentence, cap at 8 words
        val first = sentences.firstOrNull() ?: full.take(50)
        val words = first.split(" ").take(8)
        return words.joinToString(" ").let {
            if (it.length > 50) it.take(47) + "..." else it
        }
    }

    private data class LocalAction(
        val text: String,
        val actionType: String,
        val priority: String,
        val dateHint: String?
    )

    private fun extractLocalActions(sentences: List<String>, full: String): List<LocalAction> {
        val actions = mutableListOf<LocalAction>()
        val lowerFull = full.lowercase()

        // Action patterns (Spanish)
        val patterns = listOf(
            // CALL
            ActionPattern(
                Regex("(?:tengo que|hay que|debo|necesito|debería|falta)\\s+(?:llamar|telefonear)\\s+(?:a\\s+)?(.{3,40})", RegexOption.IGNORE_CASE),
                "CALL"
            ),
            ActionPattern(
                Regex("(?:llamar|telefonear)\\s+(?:a\\s+)?(.{3,40})", RegexOption.IGNORE_CASE),
                "CALL"
            ),
            // BUY
            ActionPattern(
                Regex("(?:tengo que|hay que|debo|necesito|falta)\\s+(?:comprar|pillar|coger)\\s+(.{3,40})", RegexOption.IGNORE_CASE),
                "BUY"
            ),
            ActionPattern(
                Regex("(?:comprar|pillar)\\s+(.{3,40})", RegexOption.IGNORE_CASE),
                "BUY"
            ),
            // SEND
            ActionPattern(
                Regex("(?:tengo que|hay que|debo|necesito)\\s+(?:enviar|mandar|escribir)\\s+(.{3,40})", RegexOption.IGNORE_CASE),
                "SEND"
            ),
            ActionPattern(
                Regex("(?:enviar|mandar)\\s+(.{3,40})", RegexOption.IGNORE_CASE),
                "SEND"
            ),
            // TALK_TO
            ActionPattern(
                Regex("(?:tengo que|hay que|debo|necesito)\\s+(?:hablar|quedar|reunir)\\s+(?:con\\s+)?(.{3,40})", RegexOption.IGNORE_CASE),
                "TALK_TO"
            ),
            // EVENT
            ActionPattern(
                Regex("(?:tengo|hay)\\s+(?:una\\s+)?(?:reunión|cita|evento|quedada)\\s+(.{3,40})", RegexOption.IGNORE_CASE),
                "EVENT"
            ),
            // REVIEW
            ActionPattern(
                Regex("(?:tengo que|hay que|debo|necesito)\\s+(?:revisar|mirar|repasar|comprobar)\\s+(.{3,40})", RegexOption.IGNORE_CASE),
                "REVIEW"
            ),
            // GENERIC: "tengo que/hay que/debo/necesito" + verb
            ActionPattern(
                Regex("(?:tengo que|hay que|debo|necesito|debería|no olvidar|recordar|acuérdate de|acordarme de)\\s+(.{5,60})", RegexOption.IGNORE_CASE),
                "GENERIC"
            ),
        )

        val seen = mutableSetOf<String>()

        for (pattern in patterns) {
            for (match in pattern.regex.findAll(full)) {
                val actionText = match.value.trim()
                    .replaceFirst(Regex("^(?:tengo que|hay que|debo|necesito|debería|no olvidar|recordar|acuérdate de|acordarme de)\\s+", RegexOption.IGNORE_CASE), "")
                    .replaceFirst(Regex("[.,;:]+$"), "")
                    .trim()
                    .replaceFirstChar { it.uppercase() }

                if (actionText.length < 4) continue
                val key = actionText.lowercase()
                if (key in seen) continue
                seen.add(key)

                val priority = when {
                    lowerFull.contains("urgente") || lowerFull.contains("ya mismo") || lowerFull.contains("ahora mismo") -> "URGENT"
                    lowerFull.contains("importante") -> "HIGH"
                    else -> "NORMAL"
                }

                val dateHint = extractDateHint(full, match.range)

                actions.add(LocalAction(actionText, pattern.type, priority, dateHint))
            }
        }

        return actions.take(10) // cap at 10
    }

    private data class ActionPattern(val regex: Regex, val type: String)

    private fun extractDateHint(text: String, matchRange: IntRange): String? {
        // Look at text around the match for date keywords
        val start = (matchRange.first - 30).coerceAtLeast(0)
        val end = (matchRange.last + 50).coerceAtMost(text.length)
        val context = text.substring(start, end).lowercase()

        return when {
            context.contains("hoy") -> "hoy"
            context.contains("mañana") -> "mañana"
            context.contains("pasado mañana") -> "pasado_mañana"
            context.contains("lunes") -> "lunes"
            context.contains("martes") -> "martes"
            context.contains("miércoles") || context.contains("miercoles") -> "miércoles"
            context.contains("jueves") -> "jueves"
            context.contains("viernes") -> "viernes"
            context.contains("sábado") || context.contains("sabado") -> "sábado"
            context.contains("domingo") -> "domingo"
            else -> null
        }
    }

    private fun parseDateHeuristic(hint: String?): Long? {
        if (hint == null) return null
        val cal = Calendar.getInstance()
        when (hint) {
            "hoy" -> { /* already today */ }
            "mañana" -> cal.add(Calendar.DAY_OF_YEAR, 1)
            "pasado_mañana" -> cal.add(Calendar.DAY_OF_YEAR, 2)
            "lunes" -> advanceToWeekday(cal, Calendar.MONDAY)
            "martes" -> advanceToWeekday(cal, Calendar.TUESDAY)
            "miércoles" -> advanceToWeekday(cal, Calendar.WEDNESDAY)
            "jueves" -> advanceToWeekday(cal, Calendar.THURSDAY)
            "viernes" -> advanceToWeekday(cal, Calendar.FRIDAY)
            "sábado" -> advanceToWeekday(cal, Calendar.SATURDAY)
            "domingo" -> advanceToWeekday(cal, Calendar.SUNDAY)
            else -> return null
        }
        return cal.timeInMillis
    }

    private fun advanceToWeekday(cal: Calendar, target: Int) {
        val current = cal.get(Calendar.DAY_OF_WEEK)
        var daysAhead = target - current
        if (daysAhead <= 0) daysAhead += 7
        cal.add(Calendar.DAY_OF_YEAR, daysAhead)
    }

    // ── Shared helpers ──

    private fun getApiKey(): String? {
        return context.getSharedPreferences("daily_summary", Context.MODE_PRIVATE)
            .getString("gemini_api_key", null)
    }

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
}
