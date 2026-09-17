package com.trama.app.summary

import java.util.Locale

/**
 * Turns a model-produced action into the short, executable text shown to the user.
 *
 * Small local models sometimes copy the whole conversational window into the action
 * field. This post-condition keeps the clause that contains the commitment and removes
 * speech before and after it. It is intentionally shared by every extraction surface.
 */
object ActionTextNormalizer {
    private val commitmentTrigger = Regex(
        """\b(?:tengo|tenemos|tenes|tienes)\s+que\s+|\bhay\s+que\s+|""" +
            """\b(?:debo|debemos|deberia|debería|necesito|necesitamos)\s+|""" +
            """\b(?:recordar|recordarme|recuerdame|recuérdame|acordarme|acordarnos)(?:\s+de|\s+que)?\s+|""" +
            """\b(?:me\s+qued[oó]|queda|qued[oó])\s+pendiente(?:\s+de)?\s+""",
        RegexOption.IGNORE_CASE
    )

    private val actionVerb = Regex(
        """\b(?:llamar|telefonear|comprar|enviar|mandar|escribir|contestar|responder|""" +
            """hablar\s+con|decirle|avisar|pagar|reservar|pedir|recoger|llevar|traer|""" +
            """revisar|mirar|buscar|firmar|entregar|confirmar|cancelar|actualizar|preparar|""" +
            """organizar|solicitar|renovar|ir\s+a|quedar\s+con)\b""",
        RegexOption.IGNORE_CASE
    )

    private val hardBoundary = Regex(
        """[!?¡¿;\n]+|\.(?=\s+(?:despu[eé]s|luego|entonces|bueno|vale|""" +
            """por\s+cierto|seguimos|estuvimos|est[aá]bamos|hablamos|comentamos)\b)""",
        RegexOption.IGNORE_CASE
    )
    private val conversationAfterAction = Regex(
        """\s*(?:,|\s)\s*(?:y\s+)?(?:despu[eé]s|luego|entonces|por\s+cierto|""" +
            """a\s+partir\s+de\s+ah[ií])\s+(?=(?:seguimos|seguimos\s+hablando|hablamos|""" +
            """estuvimos|estamos|comentamos|dijimos|nos\s+pusimos|cambiamos|pasamos)\b)""",
        RegexOption.IGNORE_CASE
    )

    fun focus(text: String): String {
        val normalized = text
            .replace(Regex("""\btenemso\b""", RegexOption.IGNORE_CASE), "tenemos")
            .replace(Regex("""\btenés\b""", RegexOption.IGNORE_CASE), "tenes")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (normalized.isBlank()) return normalized

        val trigger = commitmentTrigger.findAll(normalized).lastOrNull()
        var focused = trigger
            ?.let { normalized.substring(it.range.last + 1) }
            ?: normalized.let { value ->
                val verb = actionVerb.find(value)
                if (verb != null && verb.range.first > 0 && value.length > 140) {
                    value.substring(verb.range.first)
                } else {
                    value
                }
            }

        focused = focused.trimStart(',', ';', ':', '-', ' ')
        val sentenceEnd = hardBoundary.find(focused)?.range?.first
        val conversationEnd = conversationAfterAction.find(focused)?.range?.first
        val end = listOfNotNull(sentenceEnd, conversationEnd)
            .filter { it >= 6 }
            .minOrNull()
        if (end != null) focused = focused.substring(0, end)

        // A task title should remain scannable. Prefer a word boundary and keep
        // enough room for names, places, quantities and short reasons.
        if (focused.length > MAX_VISIBLE_ACTION_CHARS) {
            val cut = focused.lastIndexOf(' ', MAX_VISIBLE_ACTION_CHARS)
                .takeIf { it >= MIN_SAFE_CUT_CHARS }
                ?: MAX_VISIBLE_ACTION_CHARS
            focused = focused.substring(0, cut)
        }

        return focused
            .trim()
            .trimEnd('.', ',', ';', ':', '!', '?', '¿', '¡', '-', ' ')
            .replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
    }

    private const val MAX_VISIBLE_ACTION_CHARS = 240
    private const val MIN_SAFE_CUT_CHARS = 120
}
