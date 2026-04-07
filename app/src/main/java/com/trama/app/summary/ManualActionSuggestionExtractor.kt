package com.trama.app.summary

import com.trama.shared.model.EntryActionType
import com.trama.shared.model.EntryPriority
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class ManualActionSuggestion(
    val text: String,
    val actionType: String,
    val priority: String,
    val dueDate: Long?
)

/**
 * Lightweight local extractor for turning a reminder/transcription into actionable items.
 * Keeps the source reminder untouched; callers decide whether to persist suggestions.
 */
object ManualActionSuggestionExtractor {
    private val sentenceSplitRegex = Regex("""[.\n]+""")
    private val inlineSplitRegex = Regex("""\s+(?:y|ademas|además|tambien|también)\s+(?=(?:llamar|comprar|enviar|mandar|hablar|revisar|recordar|acordarme|acordarnos))""", RegexOption.IGNORE_CASE)
    private val leadingTriggerRegex = Regex(
        pattern = """^(recordar|acordarme de|acordarnos de|me olvid[eé]|se me fue la olla|tengo que|hay que|debo|deberia|debería|necesito)\s+""",
        option = RegexOption.IGNORE_CASE
    )
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun extract(text: String): List<ManualActionSuggestion> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return emptyList()

        val parts = sentenceSplitRegex
            .split(trimmed)
            .flatMap { sentence -> inlineSplitRegex.split(sentence) }
            .map { it.trim() }
            .filter { it.length >= 4 }

        val suggestions = linkedMapOf<String, ManualActionSuggestion>()
        for (part in parts) {
            val cleanText = cleanText(part)
            if (cleanText.isBlank()) continue
            val suggestion = ManualActionSuggestion(
                text = cleanText,
                actionType = inferActionType(part),
                priority = inferPriority(part),
                dueDate = inferDueDate(part)
            )
            suggestions.putIfAbsent(cleanText.lowercase(Locale.getDefault()), suggestion)
        }

        return suggestions.values.take(8)
    }

    private fun cleanText(raw: String): String {
        val noTrigger = raw.trim().replaceFirst(leadingTriggerRegex, "")
        return noTrigger
            .trim()
            .trimStart(',', ':', ';', '-', ' ')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    private fun inferActionType(text: String): String {
        val lower = text.lowercase(Locale.getDefault())
        return when {
            lower.contains("llamar") || lower.contains("telefonear") -> EntryActionType.CALL
            lower.contains("comprar") -> EntryActionType.BUY
            lower.contains("enviar") || lower.contains("mandar") -> EntryActionType.SEND
            lower.contains("reunion") || lower.contains("reunión") || lower.contains("cita") || lower.contains("reserva") -> EntryActionType.EVENT
            lower.contains("revisar") || lower.contains("mirar") || lower.contains("buscar") -> EntryActionType.REVIEW
            lower.contains("hablar con") || lower.contains("decirle") || lower.contains("decir a") -> EntryActionType.TALK_TO
            else -> EntryActionType.GENERIC
        }
    }

    private fun inferPriority(text: String): String {
        val lower = text.lowercase(Locale.getDefault())
        return when {
            "urgente" in lower || "ahora mismo" in lower || "cuanto antes" in lower -> EntryPriority.URGENT
            "importante" in lower || "no olvidar" in lower -> EntryPriority.HIGH
            "cuando pueda" in lower || "sin prisa" in lower -> EntryPriority.LOW
            else -> EntryPriority.NORMAL
        }
    }

    private fun inferDueDate(text: String): Long? {
        val lower = text.lowercase(Locale.getDefault())
        val cal = Calendar.getInstance()
        return when {
            "hoy" in lower -> startOfDay(cal)
            "mañana" in lower || "manana" in lower -> {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                startOfDay(cal)
            }
            "pasado mañana" in lower || "pasado manana" in lower -> {
                cal.add(Calendar.DAY_OF_YEAR, 2)
                startOfDay(cal)
            }
            else -> {
                val weekdays = listOf(
                    "lunes" to Calendar.MONDAY,
                    "martes" to Calendar.TUESDAY,
                    "miércoles" to Calendar.WEDNESDAY,
                    "miercoles" to Calendar.WEDNESDAY,
                    "jueves" to Calendar.THURSDAY,
                    "viernes" to Calendar.FRIDAY,
                    "sábado" to Calendar.SATURDAY,
                    "sabado" to Calendar.SATURDAY,
                    "domingo" to Calendar.SUNDAY
                )
                val match = weekdays.firstOrNull { it.first in lower } ?: return null
                advanceToWeekday(cal, match.second)
                startOfDay(cal)
            }
        }
    }

    private fun startOfDay(cal: Calendar): Long {
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun advanceToWeekday(cal: Calendar, target: Int) {
        while (cal.get(Calendar.DAY_OF_WEEK) != target) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        if (cal.time.before(dateFormat.parse(dateFormat.format(Calendar.getInstance().time)))) {
            cal.add(Calendar.DAY_OF_YEAR, 7)
        }
    }
}
