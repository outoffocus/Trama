package com.trama.shared.speech

/**
 * Heuristic validation for transcriptions — no network, no ML dependencies.
 * Shared between phone and watch. Filters obvious noise (radio, TV, fragments)
 * and accepts clear personal speech.
 *
 * Returns null for ambiguous cases (phone can escalate to LLM).
 */
object EntryValidatorHeuristics {

    private const val MIN_WORDS = 3

    // First person / "we" indicators — strong signal of personal speech
    private val PERSONAL_INDICATORS = listOf(
        "tengo", "necesito", "quiero", "debo", "debería", "voy a",
        "me ", "mi ", "mis ", "mío", "hay que", "tendría",
        "nosotros", "tenemos", "necesitamos", "deberíamos", "vamos a",
        "nuestro", "nuestra", "nuestros", "nuestras", "nos ",
        "podríamos", "hagamos", "compremos",
        "recuérdame", "recordar", "no olvidar", "apuntar", "anotar",
        "i need", "i have to", "i should", "we need", "we should",
        "let's", "remind me", "don't forget"
    )

    // Radio/TV/noise indicators
    private val NOISE_INDICATORS = listOf(
        "a continuación", "sintonice", "patrocinado", "publicidad",
        "la temperatura", "grados celsius", "pronóstico",
        "breaking news", "noticias de última hora",
        "suscríbete", "dale like", "canal de youtube",
        "bienvenidos al programa", "estamos en directo",
        "el gobierno", "el presidente", "la ministra",
        "millones de euros", "bolsa de valores",
        "el partido", "el equipo", "la liga",
        "según fuentes", "según informa", "fuentes oficiales"
    )

    data class HeuristicResult(
        val isValid: Boolean,
        val confidence: Float,
        val reason: String
    )

    /**
     * Check if a transcription is clearly personal, clearly noise, or ambiguous.
     * @return result for clear cases, null for ambiguous (needs LLM on phone)
     */
    fun check(text: String): HeuristicResult? {
        val lower = text.lowercase().trim()
        val words = lower.split("\\s+".toRegex())

        // Too short — likely a fragment
        if (words.size < MIN_WORDS) {
            return HeuristicResult(
                isValid = false,
                confidence = 0.9f,
                reason = "Fragmento muy corto (${words.size} palabras)"
            )
        }

        // Clear noise indicators
        val noiseMatch = NOISE_INDICATORS.firstOrNull { lower.contains(it) }
        if (noiseMatch != null) {
            return HeuristicResult(
                isValid = false,
                confidence = 0.85f,
                reason = "Detectado como ruido: '$noiseMatch'"
            )
        }

        // Clear personal indicator → accept
        val personalMatch = PERSONAL_INDICATORS.firstOrNull { lower.contains(it) }
        if (personalMatch != null) {
            return HeuristicResult(
                isValid = true,
                confidence = 0.9f,
                reason = "Indicador personal: '$personalMatch'"
            )
        }

        // Ambiguous
        return null
    }
}
