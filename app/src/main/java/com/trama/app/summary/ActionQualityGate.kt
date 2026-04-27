package com.trama.app.summary

import com.trama.shared.model.EntryActionType
import java.util.Locale

/**
 * Deterministic post-LLM gate for action candidates.
 *
 * The LLM is good at repair and interpretation, but it still needs a hard
 * acceptance contract before we put something in the user's task list.
 */
object ActionQualityGate {

    fun isActionable(
        cleanText: String,
        actionType: String,
        modelIsActionable: Boolean = true
    ): Boolean {
        if (!modelIsActionable) return false
        val normalized = cleanText
            .lowercase(Locale.getDefault())
            .trim()
            .trim('.', ',', ';', ':', '!', '?', '¿', '¡', '-', ' ')

        if (normalized.length < 6) return false
        if (normalized in TEMPORAL_ONLY_PHRASES) return false
        if (NOISE_SENTENCE_PATTERNS.any { it.containsMatchIn(normalized) }) return false

        val tokens = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size < 2) return false

        if (actionType == EntryActionType.EVENT && hasEventSignal(normalized, tokens)) return true
        if (!hasActionVerb(normalized)) return false
        return hasConcreteComplement(tokens)
    }

    private fun hasActionVerb(normalized: String): Boolean =
        ManualActionSuggestionExtractor.ACTION_VERBS.any { verb ->
            Regex("(?<![\\p{L}])${Regex.escape(verb)}(?![\\p{L}])").containsMatchIn(normalized)
        }

    private fun hasConcreteComplement(tokens: List<String>): Boolean {
        val actionRoots = ManualActionSuggestionExtractor.ACTION_VERBS
            .map { it.split(" ").first() }
            .toSet()
        return tokens.any { token ->
            val stripped = token.trim('.', ',', ';', ':', '!', '?', '¿', '¡')
            stripped.length >= 3 &&
                stripped !in TEMPORAL_TOKENS &&
                stripped !in FILLER_TOKENS &&
                stripped !in VAGUE_TOKENS &&
                actionRoots.none { root -> stripped.startsWith(root) }
        }
    }

    private fun hasEventSignal(normalized: String, tokens: List<String>): Boolean {
        val hasEventNoun = EVENT_NOUNS.any { noun ->
            Regex("(?<![\\p{L}])${Regex.escape(noun)}(?![\\p{L}])").containsMatchIn(normalized)
        }
        if (!hasEventNoun) return false
        return tokens.any { token ->
            token.length >= 4 &&
                token !in TEMPORAL_TOKENS &&
                token !in EVENT_NOUNS &&
                token !in EVENT_FILLER_TOKENS &&
                token !in VAGUE_TOKENS
        }
    }

    private val NOISE_SENTENCE_PATTERNS = listOf(
        Regex("""\bno\s+(?:queria|quería|quiero|escucho|entiendo|se)\b"""),
        Regex("""\b(?:ahi|ahí|aqui|aquí)\s+no\s+\w+"""),
        Regex("""\bvoy\s+a\s+hablar\s+como\s+sale\b"""),
        Regex("""\b(?:sale|salga)\s+(?:como|lo\s+que)\s+(?:sale|salga)\b"""),
        Regex("""\b(?:que|qué)\s+(?:barato|caro)\b"""),
        Regex("""\b(?:me|te|le)\s+asiste\b""")
    )

    private val TEMPORAL_ONLY_PHRASES = setOf(
        "mañana", "manana", "hoy", "ayer", "anoche",
        "esta tarde", "esta noche", "esta mañana", "esta manana",
        "mañana por la mañana", "manana por la manana",
        "mañana por la tarde", "manana por la tarde",
        "mañana por la noche", "manana por la noche",
        "pasado mañana", "pasado manana",
        "hay que", "tengo que", "deberia", "debería",
        "recordar", "acordarme", "acordarnos",
        "por la mañana", "por la manana", "por la tarde", "por la noche",
        "todos los dias", "todos los días", "cada dia", "cada día",
        "cada mañana", "cada manana", "cada tarde", "cada noche",
        "a veces", "de vez en cuando"
    )

    private val TEMPORAL_TOKENS = setOf(
        "hoy", "ayer", "anoche", "mañana", "manana", "tarde", "noche",
        "esta", "este", "pasado", "pasada",
        "luego", "después", "despues", "antes",
        "siempre", "nunca", "veces", "vez",
        "todos", "todas", "cada"
    )

    private val FILLER_TOKENS = setOf(
        "los", "las", "el", "la", "un", "una", "unos", "unas",
        "por", "de", "en", "a", "al", "del", "con", "para",
        "que", "y", "o", "u", "si", "no", "lo", "me", "te", "se",
        "mi", "mis", "tu", "tus", "su", "sus"
    )

    private val VAGUE_TOKENS = setOf(
        "algo", "cosa", "cosas", "esto", "esta", "este", "eso", "esa", "ese",
        "aquello", "aquella", "aquel", "ahi", "ahí", "aqui", "aquí",
        "como", "sale", "salga", "verla", "verlo", "ver", "escucho",
        "oir", "oír", "hablar", "digo", "dije", "queria", "quería"
    )

    private val EVENT_NOUNS = setOf(
        "cita", "reunion", "reunión", "evento", "quedada", "reserva", "visita"
    )

    private val EVENT_FILLER_TOKENS = setOf(
        "con", "para", "una", "uno", "unos", "unas",
        "ese", "esa", "eso", "aquel", "aquella", "otro", "otra"
    )
}
