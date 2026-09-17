package com.trama.app.ui.screens

import java.text.Normalizer
import java.util.Locale

internal object SearchQuery {
    private val stopWords = setOf(
        "de", "del", "el", "la", "los", "las", "en", "un", "una", "que", "qué",
        "mi", "mis", "por", "para", "con", "y", "o"
    )

    fun terms(query: String): List<String> {
        val words = query.trim()
            .split(Regex("[^\\p{L}0-9]+"))
            .map { it.lowercase(Locale("es")) }
            .filter { it.length >= 2 && it !in stopWords }
            .map(::canonicalTerm)
            .distinct()
        return words.ifEmpty { listOf(query.trim()).filter { it.length >= 2 } }
    }

    fun <T, K> intersect(batches: List<List<T>>, key: (T) -> K): List<T> {
        if (batches.isEmpty()) return emptyList()
        val shared = batches.drop(1).fold(batches.first().map(key).toSet()) { ids, batch ->
            ids intersect batch.map(key).toSet()
        }
        return batches.first().filter { key(it) in shared }
    }

    private fun canonicalTerm(value: String): String {
        val plain = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return when (plain) {
            "restaurante", "restaurantes" -> "restaurant"
            "cafeteria", "cafeterias" -> "cafe"
            "bares" -> "bar"
            else -> value
        }
    }
}
