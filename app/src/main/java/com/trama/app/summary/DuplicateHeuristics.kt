package com.trama.app.summary

import com.trama.shared.model.DiaryEntry
import java.text.Normalizer
import java.util.Locale

object DuplicateHeuristics {

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

    private val weakTokens = setOf(
        "recordar",
        "recordarme",
        "recordarme",
        "acordarme",
        "acordarnos",
        "de",
        "que",
        "hoy",
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
        ignoreId: Long? = null
    ): DiaryEntry? {
        val canonical = canonicalize(text)
        if (canonical.isBlank()) return null

        return existing
            .asSequence()
            .filter { it.id != ignoreId }
            .mapNotNull { candidate ->
                val candidateCanonical = canonicalize(candidate.displayText)
                if (candidateCanonical.isBlank()) return@mapNotNull null
                val score = similarityScore(canonical, candidateCanonical)
                if (score >= 0.86f) candidate to score else null
            }
            .sortedByDescending { it.second }
            .firstOrNull()
            ?.first
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

    private fun canonicalize(text: String): String {
        var normalized = Normalizer.normalize(text.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
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
