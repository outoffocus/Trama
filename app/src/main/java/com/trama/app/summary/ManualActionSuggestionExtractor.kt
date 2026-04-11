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
    private val weekdayPatterns = listOf(
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
    private val splitVerbs = listOf(
        "llamar",
        "telefonear",
        "comprar",
        "enviar",
        "mandar",
        "hablar con",
        "decirle",
        "decir a",
        "revisar",
        "mirar",
        "buscar",
        "pagar",
        "reservar",
        "pedir",
        "escribir",
        "contestar",
        "avisar",
        "recordar",
        "acordarme de",
        "acordarnos de"
    )
    private val sentenceSplitRegex = Regex("""[.\n]+""")
    private val splitVerbPattern = splitVerbs
        .map { trigger ->
            trigger.trim()
                .split("\\s+".toRegex())
                .joinToString("\\s+") { Regex.escape(it) }
        }
        .joinToString("|")
    private val inlineSplitRegex = Regex(
        """(?:,\s*|;\s*|\s+(?:y|e|ademas|además|tambien|también|luego|despues|después)\s+)(?=(?:$splitVerbPattern)\b)""",
        RegexOption.IGNORE_CASE
    )
    private val leadingTriggerRegex = Regex(
        pattern = """^(recordar|recordarme(?:\s+de)?|recuerdame|recuérdame|acordarme(?:\s+de)?|acordarnos(?:\s+de)?|me olvid[eé]|se me fue la olla|tengo que|hay que|debo|deberia|debería|necesito)\s+""",
        option = RegexOption.IGNORE_CASE
    )
    private val explicitTimeRegex = Regex("""\ba\s+las\s+(\d{1,2})(?::(\d{2}))?\b""", RegexOption.IGNORE_CASE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun extract(text: String): List<ManualActionSuggestion> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return emptyList()

        val normalized = trimmed.replaceFirst(leadingTriggerRegex, "").trim()

        val parts = sentenceSplitRegex
            .split(normalized)
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
            lower.contains("reunion") || lower.contains("reunión") ||
                lower.contains("cita") || lower.contains("reserva") ||
                lower.contains("evento") || lower.contains("quedada") -> EntryActionType.EVENT
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
            "pasado mañana" in lower || "pasado manana" in lower -> {
                cal.add(Calendar.DAY_OF_YEAR, 2)
                maybeApplyTimeOfDay(lower, cal)
                startOfDayOrTime(cal, lower)
            }
            "hoy" in lower -> startOfDay(cal)
            "mañana" in lower || "manana" in lower || "mañanas" in lower || "mananas" in lower -> {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                maybeApplyTimeOfDay(lower, cal)
                startOfDayOrTime(cal, lower)
            }
            "esta tarde" in lower || "esta noche" in lower || "esta mañana" in lower || "esta manana" in lower -> {
                maybeApplyTimeOfDay(lower, cal)
                startOfDayOrTime(cal, lower)
            }
            "fin de semana" in lower || "finde" in lower -> {
                advanceToWeekend(cal)
                maybeApplyTimeOfDay(lower, cal)
                startOfDayOrTime(cal, lower)
            }
            "semana que viene" in lower || "la semana que viene" in lower ||
                "próxima semana" in lower || "proxima semana" in lower -> {
                cal.add(Calendar.DAY_OF_YEAR, 7)
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                startOfDay(cal)
            }
            else -> {
                val match = weekdayPatterns.firstOrNull { weekdayMentioned(lower, it.first) } ?: return null
                val forceNextWeek = lower.contains("próximo ${match.first}") ||
                    lower.contains("proximo ${match.first}") ||
                    lower.contains("la próxima ${match.first}") ||
                    lower.contains("la proxima ${match.first}")
                advanceToWeekday(cal, match.second, forceNextWeek = forceNextWeek)
                maybeApplyTimeOfDay(lower, cal)
                startOfDayOrTime(cal, lower)
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

    private fun advanceToWeekday(cal: Calendar, target: Int, forceNextWeek: Boolean = false) {
        while (cal.get(Calendar.DAY_OF_WEEK) != target) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        if (forceNextWeek || cal.time.before(dateFormat.parse(dateFormat.format(Calendar.getInstance().time)))) {
            cal.add(Calendar.DAY_OF_YEAR, 7)
        }
    }

    private fun weekdayMentioned(lower: String, weekday: String): Boolean {
        return lower.contains(weekday) || lower.contains("${weekday}s")
    }

    private fun advanceToWeekend(cal: Calendar) {
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    private fun maybeApplyTimeOfDay(lower: String, cal: Calendar) {
        explicitTimeRegex.find(lower)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull()
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            if (hour != null && hour in 0..23 && minute in 0..59) {
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                return
            }
        }
        when {
            "mañana por la mañana" in lower || "manana por la mañana" in lower ||
                "mañana por la manana" in lower || "manana por la manana" in lower ||
                "esta mañana" in lower || "esta manana" in lower -> {
                cal.set(Calendar.HOUR_OF_DAY, 9)
                cal.set(Calendar.MINUTE, 0)
            }
            "mañana por la tarde" in lower || "manana por la tarde" in lower ||
                "esta tarde" in lower -> {
                cal.set(Calendar.HOUR_OF_DAY, 17)
                cal.set(Calendar.MINUTE, 0)
            }
            "mañana por la noche" in lower || "manana por la noche" in lower ||
                "esta noche" in lower -> {
                cal.set(Calendar.HOUR_OF_DAY, 21)
                cal.set(Calendar.MINUTE, 0)
            }
        }
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
    }

    private fun startOfDayOrTime(cal: Calendar, lower: String): Long {
        val hasExplicitClockTime = explicitTimeRegex.containsMatchIn(lower)
        val hasExplicitTimeBucket =
            lower.contains("mañana por la mañana") ||
                lower.contains("manana por la mañana") ||
                lower.contains("mañana por la manana") ||
                lower.contains("manana por la manana") ||
                lower.contains("mañana por la tarde") ||
                lower.contains("manana por la tarde") ||
                lower.contains("mañana por la noche") ||
                lower.contains("manana por la noche") ||
                lower.contains("esta mañana") ||
                lower.contains("esta manana") ||
                lower.contains("esta tarde") ||
                lower.contains("esta noche")

        return if (hasExplicitClockTime || hasExplicitTimeBucket) {
            cal.timeInMillis
        } else {
            startOfDay(cal)
        }
    }
}
