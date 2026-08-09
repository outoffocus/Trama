package com.trama.wear.service

/**
 * Tiny detector for phoneme streams emitted by the Bookbot Spanish Sherpa gate.
 *
 * The watch only uses this as a recall gate: a match triggers audio transfer to
 * the phone, where Whisper/LLM still decide whether anything real is actionable.
 */
object PhoneticIntentDetector {

    data class Match(
        val intentId: String,
        val label: String,
        val reason: String,
        val debugText: String,
        val confidence: Float
    )

    private data class Pattern(
        val intentId: String,
        val label: String,
        val reason: String,
        val variants: List<String>,
        val confidence: Float
    )

    private val patterns = listOf(
        Pattern(
            intentId = "phonetic_tengo_que",
            label = "Acción",
            reason = "tengo que",
            variants = listOf("tengoke", "teŋgoke"),
            confidence = 0.88f
        ),
        Pattern(
            intentId = "phonetic_tenemos_que",
            label = "Acción",
            reason = "tenemos que",
            variants = listOf("tenemoske", "tenemohke"),
            confidence = 0.88f
        ),
        Pattern(
            intentId = "phonetic_recordar",
            label = "Recordatorio",
            reason = "recordar",
            variants = listOf("rekoɾdaɾ", "rekordaɾ", "ɾekoɾdaɾ", "ɾekordaɾ"),
            confidence = 0.84f
        ),
        Pattern(
            intentId = "phonetic_acordar",
            label = "Recordatorio",
            reason = "acordar",
            variants = listOf("akoɾdaɾ", "akordaɾ"),
            confidence = 0.82f
        )
    )

    fun detect(rawText: String): Match? {
        val compact = normalize(rawText)
        if (compact.isBlank()) return null

        val pattern = patterns
            .filter { candidate ->
                candidate.variants.any { variant -> compact.indexOf(variant) >= 0 }
            }
            .maxByOrNull { it.confidence }
            ?.takeIf { it.confidence >= MIN_CONFIDENCE }
            ?: return null

        return Match(
            intentId = pattern.intentId,
            label = pattern.label,
            reason = pattern.reason,
            debugText = rawText.take(80),
            confidence = pattern.confidence
        )
    }

    private fun normalize(text: String): String {
        return text
            .lowercase()
            .replace("\\s+".toRegex(), "")
            .replace("_", "")
            .replace("|", "")
            .replace("ˈ", "")
            .replace("'", "")
    }

    private const val MIN_CONFIDENCE = 0.75f
}
