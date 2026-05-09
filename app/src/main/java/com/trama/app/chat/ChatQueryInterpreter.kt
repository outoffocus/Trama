package com.trama.app.chat

import com.trama.shared.util.DayRange
import com.trama.shared.model.EntryActionType
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ChatQueryInterpreter(
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {

    fun interpret(question: String): ChatQuery {
        val normalized = normalize(question)
        val dateRange = resolveDateRange(normalized)
        val placeTerms = extractPlaceTerms(question)
        val placeCategory = resolvePlaceCategory(normalized)
        val likedOnly = looksLikeLikedPlacesQuestion(normalized)
        val actionTypeFilter = resolveActionTypeFilter(normalized)
        val searchTerms = extractSearchTerms(normalized)
        val answerMode = resolveAnswerMode(normalized)

        val intent = when {
            likedOnly -> ChatIntent.LIKED_PLACES
            dateRange != null && looksLikeDayPlacesQuestion(normalized) && !looksLikeDurationQuestion(normalized) ->
                ChatIntent.DAY_PLACES
            dateRange != null && looksLikeCompletedTasksQuestion(normalized) -> ChatIntent.COMPLETED_TASKS
            dateRange != null && looksLikeFirstPlaceQuestion(normalized) -> ChatIntent.FIRST_PLACE
            dateRange != null && looksLikeLastPlaceQuestion(normalized) -> ChatIntent.LAST_PLACE
            placeTerms.isNotEmpty() && looksLikeAfterQuestion(normalized) -> ChatIntent.PLACE_AFTER
            placeTerms.size >= 2 && looksLikeOrderQuestion(normalized) -> ChatIntent.PLACE_ORDER
            placeTerms.isNotEmpty() && looksLikeDurationQuestion(normalized) -> ChatIntent.PLACE_DURATION
            answerMode == ChatAnswerMode.DATE_LIST && looksLikeGenericFactQuestion(normalized) ->
                ChatIntent.GENERAL_FACT_SEARCH
            placeTerms.isNotEmpty() && looksLikePlaceQuestion(normalized) -> ChatIntent.PLACE_PRESENCE
            looksLikePlaceListQuestion(normalized) && (dateRange != null || placeTerms.isNotEmpty()) ->
                ChatIntent.PLACE_LIST
            dateRange != null && looksLikeDayQuestion(normalized) -> ChatIntent.DAY_SUMMARY
            looksLikeGenericFactQuestion(normalized) -> ChatIntent.GENERAL_FACT_SEARCH
            else -> ChatIntent.UNKNOWN
        }

        return ChatQuery(
            rawQuestion = question.trim(),
            intent = intent,
            dateRange = dateRange,
            placeTerms = placeTerms,
            placeCategory = placeCategory,
            likedOnly = likedOnly,
            searchTerms = searchTerms,
            actionTypeFilter = actionTypeFilter,
            answerMode = answerMode
        )
    }

    private fun resolveAnswerMode(question: String): ChatAnswerMode =
        if (
            listOf(
                "que dia",
                "qué día",
                "que dias",
                "qué días",
                "cuando",
                "cuándo",
                "en que fecha",
                "en qué fecha"
            ).any(question::contains)
        ) {
            ChatAnswerMode.DATE_LIST
        } else {
            ChatAnswerMode.GENERAL
        }

    private fun resolveActionTypeFilter(question: String): String? = when {
        listOf("compre", "compré", "comprar", "comprado", "compras", "compra").any(question::contains) ->
            EntryActionType.BUY
        listOf("llame", "llamé", "llamar", "llamado", "llamadas", "llamada").any(question::contains) ->
            EntryActionType.CALL
        listOf("envie", "envié", "enviar", "enviado", "mandar", "mande", "mandé").any(question::contains) ->
            EntryActionType.SEND
        listOf("hable", "hablé", "hablar", "reunion", "reunión").any(question::contains) ->
            EntryActionType.TALK_TO
        listOf("revise", "revisé", "revisar", "buscar", "busque", "busqué").any(question::contains) ->
            EntryActionType.REVIEW
        else -> null
    }

    private fun looksLikeGenericFactQuestion(question: String): Boolean {
        val interrogative = listOf(
            "que ",
            "qué ",
            "cuales",
            "cuáles",
            "lista",
            "dime",
            "busca",
            "recuerdas",
            "recuerdame",
            "recuérdame"
        ).any(question::contains)
        val factualTarget = listOf(
            "cosas",
            "tareas",
            "notas",
            "acciones",
            "compre",
            "compré",
            "llame",
            "llamé",
            "envie",
            "envié",
            "hice",
            "dije",
            "registre",
            "registré",
            "apunte",
            "apunté"
        ).any(question::contains)
        return interrogative && (factualTarget || extractSearchTerms(question).isNotEmpty())
    }

    private fun resolvePlaceCategory(question: String): ChatPlaceCategory =
        if (
            listOf(
                "restaurante",
                "restaurantes",
                "bar",
                "bares",
                "cafeteria",
                "cafetería"
            ).any(question::contains)
        ) {
            ChatPlaceCategory.RESTAURANT
        } else {
            ChatPlaceCategory.ANY
        }

    private fun looksLikeLikedPlacesQuestion(question: String): Boolean {
        val liked = listOf(
            "me gustaron",
            "me gusto",
            "me gustó",
            "favoritos",
            "favoritas",
            "mejores",
            "5 estrellas",
            "cinco estrellas",
            "bien valorados",
            "bien valoradas"
        ).any(question::contains)
        val placeish = listOf(
            "restaurante",
            "restaurantes",
            "sitios",
            "lugares",
            "bares",
            "cafeterias",
            "cafeterías"
        ).any(question::contains)
        return liked && placeish
    }

    private fun looksLikePlaceListQuestion(question: String): Boolean =
        listOf(
            "en que ciudades",
            "en qué ciudades",
            "que ciudades",
            "qué ciudades",
            "ciudades",
            "que sitios",
            "qué sitios",
            "que lugares",
            "qué lugares",
            "donde estuve",
            "dónde estuve"
        ).any(question::contains)

    private fun looksLikeDurationQuestion(question: String): Boolean =
        listOf(
            "cuanto tiempo",
            "cuanto estuve",
            "cuantas horas",
            "cuantos minutos",
            "cuanto rato",
            "mas tiempo",
            "más tiempo"
        ).any(question::contains)

    private fun looksLikePlaceQuestion(question: String): Boolean =
        listOf(
            "estuve en",
            "fui a",
            "pase por",
            "pasé por",
            "he estado en",
            "en casa o en",
            "en la oficina o en",
            "en "
        ).any(question::contains)

    private fun looksLikeOrderQuestion(question: String): Boolean =
        listOf(
            "antes",
            "primero",
            "antes a",
            "antes en"
        ).any(question::contains)

    private fun looksLikeAfterQuestion(question: String): Boolean =
        listOf(
            "despues de",
            "después de"
        ).any(question::contains)

    private fun looksLikeFirstPlaceQuestion(question: String): Boolean =
        listOf(
            "primer sitio",
            "primer lugar",
            "donde estuve primero",
            "dónde estuve primero",
            "a donde fui primero",
            "a dónde fui primero"
        ).any(question::contains)

    private fun looksLikeLastPlaceQuestion(question: String): Boolean =
        listOf(
            "ultimo sitio",
            "último sitio",
            "ultimo lugar",
            "último lugar",
            "donde estuve al final",
            "dónde estuve al final",
            "a donde fui al final",
            "a dónde fui al final"
        ).any(question::contains)

    private fun looksLikeDayQuestion(question: String): Boolean =
        listOf(
            "que hice",
            "qué hice",
            "que paso",
            "qué pasó",
            "resumen de",
            "que paso este",
            "qué pasó este",
            "que hice este",
            "qué hice este"
        ).any(question::contains)

    private fun looksLikeDayPlacesQuestion(question: String): Boolean =
        listOf(
            "donde estuve",
            "dónde estuve",
            "a donde fui",
            "a dónde fui",
            "que lugares visite",
            "qué lugares visité",
            "que lugares visite",
            "qué lugares visite"
        ).any(question::contains)

    private fun looksLikeCompletedTasksQuestion(question: String): Boolean =
        listOf(
            "que tareas complete",
            "qué tareas completé",
            "que tareas completadas",
            "qué tareas completadas",
            "tareas complete",
            "tareas completadas",
            "que complete",
            "qué completé"
        ).any(question::contains)

    private fun extractSearchTerms(question: String): List<String> {
        val cleaned = DATE_HINT_WORDS.fold(question) { acc, word ->
            acc.replace(Regex("\\b${Regex.escape(word)}\\b"), " ")
        }
        return cleaned
            .split(Regex("[^\\p{L}0-9]+"))
            .map { it.trim() }
            .filter { it.length >= 3 }
            .filterNot { it in SEARCH_STOPWORDS }
            .distinct()
            .take(12)
    }

    private fun extractPlaceTerms(question: String): List<String> {
        val lowered = question.lowercase(Locale("es"))
        val marker = listOf(
            "estuve en ",
            "fui antes a ",
            "fui a ",
            "despues de ",
            "después de ",
            "pasé por ",
            "pase por ",
            "he estado en "
        )
            .firstOrNull { lowered.contains(it) }
        val tail = if (marker != null) {
            val markerIndex = lowered.indexOf(marker)
            if (markerIndex < 0) return emptyList()
            question.substring(markerIndex + marker.length)
        } else if (looksLikeLikedPlacesQuestion(normalize(question))) {
            val match = Regex("\\ben\\s+([\\p{L}0-9 .'-]+)", RegexOption.IGNORE_CASE).find(question)
            match?.groupValues?.getOrNull(1) ?: return emptyList()
        } else {
            val commaIndex = question.lastIndexOf(',')
            if (commaIndex >= 0 && commaIndex + 1 < question.length) {
                question.substring(commaIndex + 1)
            } else {
                return emptyList()
            }
        }
            .substringBefore("?")
            .substringBefore(".")
            .trim()

        if (tail.isBlank()) return emptyList()

        return tail
            .split(Regex("\\s+o\\s+", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .map { it.trimEnd('?', '¿', '.', ',', ';', ':') }
            .map { it.substringAfterLast(",").trim() }
            .map { it.removePrefix("a ").trim() }
            .map { it.removePrefix("en ").trim() }
            .map { it.removePrefix("el ").removePrefix("la ").removePrefix("los ").removePrefix("las ") }
            .map(::stripTrailingDateHints)
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun resolveDateRange(question: String): ChatDateRange? {
        val now = nowProvider()
        val calendar = Calendar.getInstance().apply { timeInMillis = now }

        parseNumericDateRange(question)?.let { return it }
        parseNamedDayRange(question, calendar)?.let { return it }

        if (question.contains("esta semana")) {
            val startOfWeek = (calendar.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                val dayOfWeek = get(Calendar.DAY_OF_WEEK)
                val diff = when (dayOfWeek) {
                    Calendar.SUNDAY -> -6
                    else -> Calendar.MONDAY - dayOfWeek
                }
                add(Calendar.DAY_OF_YEAR, diff)
            }
            val endOfWeek = (startOfWeek.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, 6)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            return ChatDateRange(
                startMillis = startOfWeek.timeInMillis,
                endMillis = endOfWeek.timeInMillis,
                label = "esta semana"
            )
        }

        if (question.contains("este mes")) {
            val start = (calendar.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val end = (start.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            return ChatDateRange(
                startMillis = start.timeInMillis,
                endMillis = end.timeInMillis,
                label = monthLabel(start.timeInMillis)
            )
        }

        if (question.contains("este año") || question.contains("este ano")) {
            val start = (calendar.clone() as Calendar).apply {
                set(Calendar.MONTH, Calendar.JANUARY)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val end = (start.clone() as Calendar).apply {
                set(Calendar.MONTH, Calendar.DECEMBER)
                set(Calendar.DAY_OF_MONTH, 31)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            return ChatDateRange(
                startMillis = start.timeInMillis,
                endMillis = end.timeInMillis,
                label = start.get(Calendar.YEAR).toString()
            )
        }

        val keywordRange = when {
            question.contains("hoy") -> DayRange.of(now)
            question.contains("ayer") -> DayRange.of(now - DAY_MS)
            question.contains("anteayer") -> DayRange.of(now - 2 * DAY_MS)
            else -> null
        }
        if (keywordRange != null) {
            return ChatDateRange(
                startMillis = keywordRange.startMs,
                endMillis = keywordRange.endInclusiveMs,
                label = humanDate(keywordRange.startMs)
            )
        }

        val weekday = WEEKDAYS.entries.firstOrNull { question.contains(it.key) }
        if (weekday != null) {
            val target = resolveWeekdayMillis(calendar, weekday.value, question)
            val dayRange = DayRange.of(target)
            return ChatDateRange(
                startMillis = dayRange.startMs,
                endMillis = dayRange.endInclusiveMs,
                label = humanDate(dayRange.startMs)
            )
        }

        val monthMatch = MONTHS.entries.firstOrNull { question.contains(it.key) }
        if (monthMatch != null) {
            val targetYear = extractExplicitYear(question)
                ?: inferYearForMonth(calendar, monthMatch.value)
            val monthStart = Calendar.getInstance().apply {
                set(Calendar.YEAR, targetYear)
                set(Calendar.MONTH, monthMatch.value)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val monthEnd = (monthStart.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            return ChatDateRange(
                startMillis = monthStart.timeInMillis,
                endMillis = monthEnd.timeInMillis,
                label = monthLabel(monthStart.timeInMillis)
            )
        }

        val explicitYear = extractExplicitYear(question) ?: return null
        val yearStart = Calendar.getInstance().apply {
            set(Calendar.YEAR, explicitYear)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return ChatDateRange(
            startMillis = yearStart.timeInMillis,
            endMillis = (yearStart.clone() as Calendar).apply {
                set(Calendar.MONTH, Calendar.DECEMBER)
                set(Calendar.DAY_OF_MONTH, 31)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis,
            label = explicitYear.toString()
        )
    }

    private fun parseNamedDayRange(question: String, calendar: Calendar): ChatDateRange? {
        val dayMonthRegex = Regex("\\b(\\d{1,2})\\s+de\\s+([a-záéíóú]+)(?:\\s+de\\s+(20\\d{2}))?\\b")
        val match = dayMonthRegex.find(question) ?: return null
        val day = match.groupValues[1].toIntOrNull() ?: return null
        val monthText = match.groupValues[2]
        val month = MONTHS.entries.firstOrNull { monthText == it.key }?.value ?: return null
        val year = match.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }?.toIntOrNull()
            ?: inferYearForMonth(calendar, month)
        val start = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return ChatDateRange(
            startMillis = start.timeInMillis,
            endMillis = (start.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis,
            label = humanDate(start.timeInMillis)
        )
    }

    private fun parseNumericDateRange(question: String): ChatDateRange? {
        val match = Regex("\\b(\\d{1,2})/(\\d{1,2})/(20\\d{2})\\b").find(question) ?: return null
        val day = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull()?.minus(1) ?: return null
        val year = match.groupValues[3].toIntOrNull() ?: return null
        val start = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return ChatDateRange(
            startMillis = start.timeInMillis,
            endMillis = (start.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis,
            label = humanDate(start.timeInMillis)
        )
    }

    private fun resolveWeekdayMillis(
        calendar: Calendar,
        targetWeekday: Int,
        question: String
    ): Long {
        val base = (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return when {
            question.contains("este ") || question.contains("esta ") -> {
                val startOfWeek = (base.clone() as Calendar).apply {
                    val dayOfWeek = get(Calendar.DAY_OF_WEEK)
                    val diff = when (dayOfWeek) {
                        Calendar.SUNDAY -> -6
                        else -> Calendar.MONDAY - dayOfWeek
                    }
                    add(Calendar.DAY_OF_YEAR, diff)
                }
                val offset = (targetWeekday - Calendar.MONDAY + 7) % 7
                startOfWeek.add(Calendar.DAY_OF_YEAR, offset)
                startOfWeek.timeInMillis
            }
            else -> {
                val result = base
                while (result.get(Calendar.DAY_OF_WEEK) != targetWeekday) {
                    result.add(Calendar.DAY_OF_YEAR, -1)
                }
                result.timeInMillis
            }
        }
    }

    private fun humanDate(epochMs: Long): String =
        SimpleDateFormat("EEEE d 'de' MMMM yyyy", Locale("es"))
            .format(Date(epochMs))
            .replaceFirstChar { it.uppercase() }

    private fun monthLabel(epochMs: Long): String =
        SimpleDateFormat("MMMM yyyy", Locale("es"))
            .format(Date(epochMs))
            .replaceFirstChar { it.uppercase() }

    private fun extractExplicitYear(question: String): Int? =
        Regex("\\b(20\\d{2})\\b").find(question)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun inferYearForMonth(calendar: Calendar, targetMonth: Int): Int {
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)
        return if (targetMonth <= currentMonth) currentYear else currentYear - 1
    }

    private fun stripTrailingDateHints(value: String): String {
        val patterns = listOf(
            Regex("\\s+en\\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)(\\s+de\\s+20\\d{2})?$", RegexOption.IGNORE_CASE),
            Regex("\\s+(este|esta)\\s+(semana|mes|ano|año)$", RegexOption.IGNORE_CASE),
            Regex("\\s+(hoy|ayer|anteayer)$", RegexOption.IGNORE_CASE),
            Regex("\\s+(lunes|martes|miercoles|miércoles|jueves|viernes|sabado|sábado|domingo)$", RegexOption.IGNORE_CASE),
            Regex("\\s+en\\s+20\\d{2}$", RegexOption.IGNORE_CASE),
            Regex("\\s+el\\s+\\d{1,2}/\\d{1,2}/20\\d{2}$", RegexOption.IGNORE_CASE),
            Regex("\\s+el\\s+\\d{1,2}\\s+de\\s+[a-záéíóú]+(\\s+de\\s+20\\d{2})?$", RegexOption.IGNORE_CASE)
        )

        return patterns.fold(value) { acc, regex -> acc.replace(regex, "") }.trim()
    }

    private fun normalize(value: String): String {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
        return normalized.lowercase(Locale("es"))
    }

    private companion object {
        const val DAY_MS = 86_400_000L

        val WEEKDAYS = linkedMapOf(
            "lunes" to Calendar.MONDAY,
            "martes" to Calendar.TUESDAY,
            "miercoles" to Calendar.WEDNESDAY,
            "miércoles" to Calendar.WEDNESDAY,
            "jueves" to Calendar.THURSDAY,
            "viernes" to Calendar.FRIDAY,
            "sabado" to Calendar.SATURDAY,
            "sábado" to Calendar.SATURDAY,
            "domingo" to Calendar.SUNDAY
        )

        val MONTHS = linkedMapOf(
            "enero" to Calendar.JANUARY,
            "febrero" to Calendar.FEBRUARY,
            "marzo" to Calendar.MARCH,
            "abril" to Calendar.APRIL,
            "mayo" to Calendar.MAY,
            "junio" to Calendar.JUNE,
            "julio" to Calendar.JULY,
            "agosto" to Calendar.AUGUST,
            "septiembre" to Calendar.SEPTEMBER,
            "setiembre" to Calendar.SEPTEMBER,
            "octubre" to Calendar.OCTOBER,
            "noviembre" to Calendar.NOVEMBER,
            "diciembre" to Calendar.DECEMBER
        )

        val DATE_HINT_WORDS = setOf(
            "hoy",
            "ayer",
            "anteayer",
            "esta",
            "este",
            "semana",
            "mes",
            "ano",
            "año",
            "lunes",
            "martes",
            "miercoles",
            "miércoles",
            "jueves",
            "viernes",
            "sabado",
            "sábado",
            "domingo"
        ) + MONTHS.keys

        val SEARCH_STOPWORDS = setOf(
            "que",
            "qué",
            "cual",
            "cuál",
            "cuales",
            "cuáles",
            "cuando",
            "cuándo",
            "donde",
            "dónde",
            "como",
            "cómo",
            "cosas",
            "cosa",
            "este",
            "esta",
            "estos",
            "estas",
            "mes",
            "semana",
            "ano",
            "año",
            "del",
            "con",
            "para",
            "por",
            "los",
            "las",
            "una",
            "uno",
            "unos",
            "unas",
            "mis",
            "tus",
            "sus",
            "me",
            "te",
            "se",
            "hay",
            "hice",
            "dime",
            "lista",
            "recuerdas",
            "recuerdame",
            "recuérdame",
            "dia",
            "día",
            "dias",
            "días",
            "compre",
            "compré",
            "comprar",
            "comprado",
            "compras",
            "compra",
            "llame",
            "llamé",
            "llamar",
            "llamado",
            "llamada",
            "llamadas",
            "envie",
            "envié",
            "enviar",
            "enviado",
            "mandar",
            "mande",
            "mandé",
            "hable",
            "hablé",
            "hablar",
            "reunion",
            "reunión",
            "fui",
            "fue",
            "ir",
            "iba",
            "ido",
            "estuve",
            "estar",
            "hacer",
            "hizo"
        )
    }
}
