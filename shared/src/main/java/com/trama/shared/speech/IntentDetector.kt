package com.trama.shared.speech

/**
 * Detects semantic intents in transcribed speech using regex-based categories.
 *
 * Replaces exact keyword matching with flexible category matching that captures
 * natural language variations. For example, a "recordatorios" category can
 * match phrases like "recordar" or "acordarme de".
 *
 * Shared between phone and watch modules.
 * Thread-safe: patterns can be updated while detection runs.
 */
class IntentDetector {

    companion object {
        private const val TAG = "IntentDetector"

        /** Minimum text length to consider (avoids false positives on very short fragments) */
        private const val MIN_TEXT_LENGTH = 4
    }

    @Volatile
    private var patterns: List<IntentPattern> = IntentPattern.DEFAULTS

    /** Also support user-added custom keywords (simple contains matching) */
    @Volatile
    private var customKeywords: List<String> = emptyList()

    /**
     * Update the active patterns.
     */
    fun setPatterns(patterns: List<IntentPattern>) {
        this.patterns = patterns
    }

    /**
     * Set custom keywords added by the user (simple exact matching, backward compat).
     */
    fun setCustomKeywords(keywords: List<String>) {
        this.customKeywords = keywords.filter { it.isNotBlank() }
    }

    /**
     * Result of intent detection.
     */
    data class DetectionResult(
        /** The pattern that matched, or null if a custom keyword matched */
        val pattern: IntentPattern?,
        /** The custom keyword that matched, or null if a pattern matched */
        val customKeyword: String?,
        /** The captured text to save as the diary entry */
        val capturedText: String,
        /** Label for display (pattern label or custom keyword) */
        val label: String
    )

    /**
     * Detect if the given text contains any known intent.
     *
     * Checks patterns first (more specific), then custom keywords.
     * Returns the first match, or null if no intent is detected.
     */
    fun detect(text: String): DetectionResult? {
        if (text.length < MIN_TEXT_LENGTH) return null

        val lowerText = text.lowercase()

        // 1. Check regex patterns (most specific, highest priority)
        for (pattern in patterns) {
            if (!pattern.enabled) continue

            val match = pattern.regex.find(lowerText)
            if (match != null) {
                val captured = if (pattern.captureAll) {
                    text // Full utterance
                } else {
                    text.substring(match.range.first)
                }

                return DetectionResult(
                    pattern = pattern,
                    customKeyword = null,
                    capturedText = captured,
                    label = pattern.label
                )
            }
        }

        // 2. Check custom keywords (simple contains, backward compat)
        for (keyword in customKeywords) {
            if (lowerText.contains(keyword.lowercase())) {
                return DetectionResult(
                    pattern = null,
                    customKeyword = keyword,
                    capturedText = text,
                    label = keyword
                )
            }
        }

        return null
    }

    /**
     * Detect intent in partial results (streaming).
     * Requires longer text to reduce false positives.
     */
    fun detectPartial(text: String): DetectionResult? {
        if (text.length < 8) return null
        return detect(text)
    }
}
