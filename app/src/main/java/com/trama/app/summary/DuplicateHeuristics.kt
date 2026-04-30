package com.trama.app.summary

import com.trama.shared.model.DiaryEntry
import com.trama.shared.util.DayRange
import java.text.Normalizer
import java.util.Locale

object DuplicateHeuristics {

    /** Minimum similarity score to consider two notes duplicates. */
    private const val SIMILARITY_THRESHOLD = 0.75f

    /** Minimum length of a token that counts as a content word (object/noun). */
    private const val CONTENT_TOKEN_MIN_LEN = 3

    private val actionVerbs: Set<String> = ManualActionSuggestionExtractor
        .ACTION_VERBS
        .map { canonicalizeToken(it) }
        .toSet()

    private val temporalPhrases = listOf(
        "pasado manana",
        "esta manana",
        "esta tarde",
        "esta noche",
        "por la manana",
        "por la tarde",
        "por la noche",
        "a primera hora",
        "al final del dia",
        "la semana que viene",
        "el finde",
        "fin de semana"
    )

    private val actionTriggerRegex = Regex(
        """\b(?:tengo|tenemos|tenemso|tienes|tenes)\s+que\s+|\bhay\s+que\s+|\b(?:debo|debemos|deberia|necesito|necesitamos)\s+""",
        RegexOption.IGNORE_CASE
    )

    private val weakTokens = setOf(
        "recordar",
        "recordarme",
        "recordarme",
        "acordarme",
        "acordarnos",
        "tengo",
        "tenemos",
        "tenemso",
        "tienes",
        "tenes",
        "hay",
        "debo",
        "deberia",
        "necesito",
        "de",
        "con",
        "que",
        "y",
        "hoy",
        "estoy",
        "estuve",
        "hablando",
        "hable",
        "manana",
        "ayer",
        "luego",
        "despues",
        "despues",
        "mas",
        "tarde",
        "noche",
        "manana",
        "semana",
        "finde"
    )

    fun findLikelyDuplicate(
        text: String,
        existing: List<DiaryEntry>,
        ignoreId: Long? = null,
        newDueDate: Long? = null
    ): DiaryEntry? {
        val canonical = canonicalize(text)
        if (canonical.isBlank()) return null
        val newTokens = canonical.split(" ").filter { it.isNotBlank() }.toSet()
        val newDueDay = newDueDate?.let { DayRange.of(it).startMs }

        return existing
            .asSequence()
            .filter { it.id != ignoreId }
            .mapNotNull { candidate ->
                val candidateCanonical = canonicalize(candidate.displayText)
                if (candidateCanonical.isBlank()) return@mapNotNull null

                // Distinct concrete dueDates → different occurrences, not a duplicate.
                val candidateDueDay = candidate.dueDate?.let { DayRange.of(it).startMs }
                if (newDueDay != null && candidateDueDay != null &&
                    newDueDay != candidateDueDay) return@mapNotNull null

                val candidateTokens = candidateCanonical.split(" ")
                    .filter { it.isNotBlank() }
                    .toSet()
                if (!shareContentToken(newTokens, candidateTokens)) return@mapNotNull null

                val score = similarityScore(canonical, candidateCanonical)
                if (score >= SIMILARITY_THRESHOLD) candidate to score else null
            }
            .sortedByDescending { it.second }
            .firstOrNull()
            ?.first
    }

    /**
     * Require at least one shared "content" token (non-verb, length ≥ 3) so
     * bare-verb captures like "comprar" and "comprar leche" don't collapse
     * — the object is what makes two notes actually the same task.
     */
    private fun shareContentToken(left: Set<String>, right: Set<String>): Boolean =
        left.intersect(right).any { token ->
            token.length >= CONTENT_TOKEN_MIN_LEN && token !in actionVerbs
        }

    private fun similarityScore(left: String, right: String): Float {
        if (left == right) return 1.0f

        val leftTokens = left.split(" ").filter { it.isNotBlank() }
        val rightTokens = right.split(" ").filter { it.isNotBlank() }
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0f

        if (left.contains(right) || right.contains(left)) {
            val overlap = overlapRatio(leftTokens, rightTokens)
            if (overlap >= 0.75f) return 0.9f
        }

        val overlap = overlapRatio(leftTokens, rightTokens)
        val edit = normalizedLevenshtein(left, right)
        return (overlap * 0.6f) + (edit * 0.4f)
    }

    private fun overlapRatio(leftTokens: List<String>, rightTokens: List<String>): Float {
        val leftSet = leftTokens.toSet()
        val rightSet = rightTokens.toSet()
        val common = leftSet.intersect(rightSet).size.toFloat()
        val base = minOf(leftSet.size, rightSet.size).coerceAtLeast(1)
        return common / base
    }

    private fun normalizedLevenshtein(left: String, right: String): Float {
        val maxLen = maxOf(left.length, right.length).coerceAtLeast(1)
        val distance = levenshtein(left, right)
        return 1f - (distance.toFloat() / maxLen.toFloat())
    }

    private fun levenshtein(left: String, right: String): Int {
        val costs = IntArray(right.length + 1) { it }
        for (i in left.indices) {
            var previous = i
            costs[0] = i + 1
            for (j in right.indices) {
                val current = costs[j + 1]
                costs[j + 1] = minOf(
                    costs[j + 1] + 1,
                    costs[j] + 1,
                    previous + if (left[i] == right[j]) 0 else 1
                )
                previous = current
            }
        }
        return costs[right.length]
    }

    private fun canonicalizeToken(token: String): String =
        Normalizer.normalize(token.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .replace("[^\\p{L}\\p{N}]".toRegex(), "")

    private fun canonicalize(text: String): String {
        val actionText = actionTriggerRegex.findAll(text).lastOrNull()
            ?.let { text.substring(it.range.last + 1) }
            ?: text
        var normalized = Normalizer.normalize(actionText.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .replace("[^\\p{L}\\p{N}\\s]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

        temporalPhrases.forEach { phrase ->
            normalized = normalized.replace("\\b${Regex.escape(phrase)}\\b".toRegex(), " ")
        }

        normalized = normalized
            .replace("\\b(el|la|los|las|un|una|unos|unas)\\b".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

        val filtered = normalized
            .split(" ")
            .filter { token -> token.isNotBlank() && token !in weakTokens }

        return filtered.joinToString(" ").trim()
    }
}
