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
        val debugText: String
    )

    private data class Pattern(
        val intentId: String,
        val label: String,
        val reason: String,
        val variants: List<String>
    )

    private val patterns = listOf(
        Pattern(
            intentId = "phonetic_tengo_que",
            label = "Acción",
            reason = "tengo que",
            variants = listOf("tengoke", "teŋgoke")
        ),
        Pattern(
            intentId = "phonetic_tenemos_que",
            label = "Acción",
            reason = "tenemos que",
            variants = listOf("tenemoske", "tenemohke")
        ),
        Pattern(
            intentId = "phonetic_recordar",
            label = "Recordatorio",
            reason = "recordar",
            variants = listOf("rekoɾdaɾ", "rekordaɾ", "ɾekoɾdaɾ", "ɾekordaɾ")
        ),
        Pattern(
            intentId = "phonetic_acordar",
            label = "Recordatorio",
            reason = "acordar",
            variants = listOf("akoɾdaɾ", "akordaɾ")
        ),
        Pattern(
            intentId = "phonetic_action_verb",
            label = "Acción",
            reason = "verbo accionable",
            variants = listOf(
                "kompɾaɾ", "komprar",
                "ʎamaɾ", "ʝamaɾ", "jamaɾ",
                "pagaɾ", "pagar",
                "enbiaɾ", "embiaɾ", "enbjaɾ",
                "rekoxeɾ", "rekoχeɾ", "ɾekoxeɾ", "ɾekoχeɾ"
            )
        )
    )

    fun detect(rawText: String): Match? {
        val compact = normalize(rawText)
        if (compact.isBlank()) return null

        val pattern = patterns.firstOrNull { candidate ->
            candidate.variants.any { variant -> compact.contains(variant) }
        } ?: return null

        return Match(
            intentId = pattern.intentId,
            label = pattern.label,
            reason = pattern.reason,
            debugText = rawText.take(80)
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
}
