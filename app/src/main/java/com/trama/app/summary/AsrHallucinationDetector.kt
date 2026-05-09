package com.trama.app.summary

import java.util.Locale

/**
 * Cheap detector for Whisper non-speech tokens and classic hallucinations.
 * Shared by live captures and manual/offline recordings so "[Musica]" never
 * becomes a completed note.
 */
object AsrHallucinationDetector {
    private const val MIN_TEXT_LENGTH = 24
    private const val MIN_TOKENS = 5
    private const val REPETITION_RATIO = 0.5f

    private val bracketOnlyRe = Regex("""^\s*[\[(][^\]\)]{1,40}[\])]\s*$""")
    private val dashLineStartRe = Regex("""(?:^|[\s.!?])-\s*[¿¡\p{L}]""")

    fun detect(text: String, singleWordIsHallucination: Boolean = true): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        if (bracketOnlyRe.matches(trimmed)) {
            val tag = trimmed.trim('[', ']', '(', ')').lowercase(Locale.ROOT).take(20)
            return "bracket_token:$tag"
        }

        val dashLineStarts = dashLineStartRe.findAll(trimmed).count()
        if (dashLineStarts >= 2) {
            return "multi_speaker_dialog"
        }

        if (trimmed.startsWith("¿") && trimmed.endsWith("?")) {
            return "pure_question"
        }

        val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (singleWordIsHallucination && tokens.size == 1) {
            val cleaned = tokens[0].trim { c ->
                c == '.' || c == ',' || c == '!' || c == '?' ||
                    c == '¡' || c == '¿' || c == ';' || c == ':'
            }
            return "single_word:${cleaned.lowercase(Locale.ROOT).take(20)}"
        }

        if (trimmed.length < MIN_TEXT_LENGTH) return null
        if (tokens.size < MIN_TOKENS) return null

        val lettered = tokens.filter { token -> token.any(Char::isLetter) }
        if (lettered.size >= MIN_TOKENS &&
            lettered.all { it == it.uppercase(Locale.ROOT) }
        ) {
            return "all_caps"
        }

        val byCount = tokens.groupingBy { it.lowercase(Locale.ROOT) }.eachCount()
        val mostFrequent = byCount.maxByOrNull { it.value } ?: return null
        if (mostFrequent.value.toFloat() / tokens.size >= REPETITION_RATIO) {
            return "repetition:${mostFrequent.key.take(20)}"
        }

        return null
    }
}
