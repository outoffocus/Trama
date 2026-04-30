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
        if (TRAILING_INCOMPLETE_PATTERNS.any { it.containsMatchIn(normalized) }) return false
        if (looksLikeNonPersonalConversation(normalized)) return false

        val tokens = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size < 2) return false

        if (actionType == EntryActionType.EVENT && hasEventSignal(normalized, tokens)) return true
        if (!hasTaskShape(normalized, tokens, actionType)) return false
        return hasConcreteComplement(tokens)
    }

    private fun looksLikeNonPersonalConversation(normalized: String): Boolean {
        val hasConversationFrame = CONVERSATION_FRAME_PATTERNS.any { it.containsMatchIn(normalized) }
        val hasDelegatedInstruction = DELEGATED_INSTRUCTION_PATTERNS.any { it.containsMatchIn(normalized) }
        val hasImplementationExample = IMPLEMENTATION_EXAMPLE_PATTERNS.any { it.containsMatchIn(normalized) }
        return (hasConversationFrame && (hasDelegatedInstruction || hasImplementationExample)) ||
            (hasDelegatedInstruction && hasImplementationExample)
    }

    private fun hasTaskShape(
        normalized: String,
        tokens: List<String>,
        actionType: String
    ): Boolean {
        if (OBLIGATION_PATTERNS.any { it.containsMatchIn(normalized) }) return true
        if (hasInfinitiveActionWithFollowingComplement(tokens)) return true
        return actionType in NON_GENERIC_ACTION_TYPES
    }

    private fun hasInfinitiveActionWithFollowingComplement(tokens: List<String>): Boolean =
        tokens.dropLast(1).withIndex().any { (index, token) ->
            token.isCandidateActionToken() &&
                tokens.getOrNull(index - 1) !in PRE_ACTION_NOUN_MARKERS &&
                tokens.drop(index + 1).any { it.isConcreteComplementToken() }
        }

    private fun String.isCandidateActionToken(): Boolean {
        val stripped = trim('.', ',', ';', ':', '!', '?', '¿', '¡')
        return stripped !in TEMPORAL_TOKENS &&
            stripped !in FILLER_TOKENS &&
            stripped !in VAGUE_TOKENS &&
            stripped.looksLikeSpanishInfinitive()
    }

    private fun hasConcreteComplement(tokens: List<String>): Boolean {
        return tokens.any { token ->
            token.isConcreteComplementToken() && !token.looksLikeSpanishInfinitive()
        }
    }

    private fun String.isConcreteComplementToken(): Boolean {
        val stripped = trim('.', ',', ';', ':', '!', '?', '¿', '¡')
        return stripped.length >= 3 &&
            stripped !in TEMPORAL_TOKENS &&
            stripped !in FILLER_TOKENS &&
            stripped !in VAGUE_TOKENS
    }

    private fun String.looksLikeSpanishInfinitive(): Boolean {
        val stripped = trim('.', ',', ';', ':', '!', '?', '¿', '¡')
        val base = stripped.withoutSingleEncliticPronoun()
        return base.length >= 2 &&
            (base.endsWith("ar") || base.endsWith("er") || base.endsWith("ir"))
    }

    private fun String.withoutSingleEncliticPronoun(): String {
        val pronouns = listOf("melo", "mela", "melos", "melas", "selo", "sela", "selos", "selas")
        pronouns.firstOrNull { endsWith(it) }?.let { return dropLast(it.length) }
        return when {
            length > 4 && endsWith("les") -> dropLast(3)
            length > 4 && (endsWith("los") || endsWith("las")) -> dropLast(3)
            length > 3 && (endsWith("me") || endsWith("te") || endsWith("se") ||
                endsWith("le") || endsWith("lo") || endsWith("la") ||
                endsWith("os") || endsWith("nos")) -> dropLast(if (endsWith("nos")) 3 else 2)
            else -> this
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
        Regex("""\b(?:ya\s+)?no\s+(?:tengo|tenemos|tienes|hay)\s+que\b"""),
        Regex("""\bno\s+(?:debo|debemos|necesito|necesitamos)\b"""),
        Regex("""\bme\s+toc[oó]\b"""),
        Regex("""\bno\s+(?:queria|quería|quiero|escucho|entiendo|se)\b"""),
        Regex("""\b(?:ahi|ahí|aqui|aquí)\s+no\s+\w+"""),
        Regex("""\bvoy\s+a\s+hablar\s+como\s+sale\b"""),
        Regex("""\b(?:sale|salga)\s+(?:como|lo\s+que)\s+(?:sale|salga)\b"""),
        Regex("""\b(?:que|qué)\s+(?:barato|caro)\b"""),
        Regex("""\b(?:me|te|le)\s+asiste\b"""),
        Regex("""^funci[oó]n\s+de\b""")
    )

    private val TRAILING_INCOMPLETE_PATTERNS = listOf(
        Regex("""\b(?:un|una|unos|unas|el|la|los|las|de|del|con|para|que|a|al)$""")
    )

    private val CONVERSATION_FRAME_PATTERNS = listOf(
        Regex("""\bme\s+refiero\b"""),
        Regex("""\blo\s+que\s+estamos\s+diciendo\b"""),
        Regex("""\bestamos\s+diciendo\b"""),
        Regex("""\bpor\s+ejemplo\b"""),
        Regex("""\beso\s+se\s+puede\s+hacer\b""")
    )

    private val DELEGATED_INSTRUCTION_PATTERNS = listOf(
        Regex("""\b(?:tu|tú)\s+(?:creas|haces|envias|envías|mandas|llamas|tienes|debes)\b"""),
        Regex("""\b(?:vale|bueno),?\s+pues\s+\p{L}+,?\s+(?:tu|tú)\b""")
    )

    private val IMPLEMENTATION_EXAMPLE_PATTERNS = listOf(
        Regex("""\bfunci[oó]n\s+de\s+(?:enviar|mandar|llamar|crear)\b"""),
        Regex("""\bdescripci[oó]n\s+del\s+plan\b"""),
        Regex("""\bcontenido\s+que\s+pongamos\b""")
    )

    private val OBLIGATION_PATTERNS = listOf(
        Regex("""\b(?:tengo|tenemos|tienes|teneis|tenéis|tiene|tienen)\s+que\b"""),
        Regex("""\bhay\s+que\b"""),
        Regex("""\b(?:debo|debemos|debes|deberia|debería|necesito|necesitamos)\b"""),
        Regex("""\b(?:recordar|acordarme|acordarnos|pendiente)\b""")
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
        "que", "y", "o", "u", "si", "no", "ya", "está", "esta", "lo", "me", "te", "se",
        "mi", "mis", "tu", "tus", "su", "sus",
        "tengo", "tenemos", "tienes", "teneis", "tenéis", "tiene", "tienen",
        "hay", "debo", "debemos", "debes", "deberia", "debería",
        "necesito", "necesitamos", "queda", "quedo", "quedó", "quedara", "quedará", "pendiente"
    )

    private val PRE_ACTION_NOUN_MARKERS = setOf(
        "el", "la", "los", "las", "un", "una", "unos", "unas",
        "de", "del", "al", "para", "con", "sobre"
    )

    private val VAGUE_TOKENS = setOf(
        "algo", "cosa", "cosas", "esto", "esta", "este", "eso", "esa", "ese",
        "nada", "nadie", "ninguno", "ninguna",
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

    private val NON_GENERIC_ACTION_TYPES = setOf(
        EntryActionType.CALL,
        EntryActionType.BUY,
        EntryActionType.SEND,
        EntryActionType.REVIEW,
        EntryActionType.TALK_TO
    )
}
