package com.trama.shared.speech

import java.text.Normalizer

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
        private const val MIN_PARTIAL_LENGTH = 8
        private const val FUZZY_PREFIX_LENGTH = 5
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
        val normalizedText = normalize(lowerText)

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

            if (pattern.matchesFuzzy(normalizedText)) {
                return DetectionResult(
                    pattern = pattern,
                    customKeyword = null,
                    capturedText = text,
                    label = pattern.label
                )
            }
        }

        // 2. Check custom keywords (simple contains, backward compat)
        for (keyword in customKeywords) {
            val normalizedKeyword = normalize(keyword)
            if (lowerText.contains(keyword.lowercase()) || normalizedText.contains(normalizedKeyword)) {
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
        if (text.length < MIN_PARTIAL_LENGTH) return null
        return detect(text)
    }

    private fun IntentPattern.matchesFuzzy(normalizedText: String): Boolean {
        return normalizedTriggers.any { trigger ->
            trigger in normalizedText ||
                tokenWindowMatches(normalizedText, trigger) ||
                prefixNearMatch(normalizedText, trigger)
        }
    }

    private fun tokenWindowMatches(normalizedText: String, trigger: String): Boolean {
        val triggerTokens = trigger.split(' ').filter { it.isNotBlank() }
        if (triggerTokens.isEmpty()) return false

        val textTokens = normalizedText.split(' ').filter { it.isNotBlank() }
        if (textTokens.size < triggerTokens.size) return false

        for (start in 0..textTokens.size - triggerTokens.size) {
            val window = textTokens.subList(start, start + triggerTokens.size)
            val tokenMatches = window.zip(triggerTokens).all { (actual, expected) ->
                actual == expected || editDistanceAtMostOne(actual, expected)
            }
            if (tokenMatches) return true
        }
        return false
    }

    private fun prefixNearMatch(normalizedText: String, trigger: String): Boolean {
        // Prefix fuzziness is useful for single-word ASR slips ("recorda" →
        // "recordar"), but dangerous for multi-word triggers: "tengo que
        // acordarme de" would otherwise match any utterance containing "tengo".
        if (trigger.contains(' ')) return false
        val joined = trigger.replace(" ", "")
        if (joined.length < FUZZY_PREFIX_LENGTH) return false
        val triggerPrefix = joined.take(FUZZY_PREFIX_LENGTH)
        return normalizedText
            .split(' ')
            .filter { it.length >= FUZZY_PREFIX_LENGTH }
            .any { token ->
                editDistanceAtMostOne(token.take(FUZZY_PREFIX_LENGTH), triggerPrefix)
            }
    }

    private fun editDistanceAtMostOne(left: String, right: String): Boolean {
        if (left == right) return true
        if (kotlin.math.abs(left.length - right.length) > 1) return false

        var i = 0
        var j = 0
        var edits = 0

        while (i < left.length && j < right.length) {
            if (left[i] == right[j]) {
                i++
                j++
                continue
            }

            if (++edits > 1) return false

            when {
                left.length > right.length -> i++
                right.length > left.length -> j++
                else -> {
                    i++
                    j++
                }
            }
        }

        if (i < left.length || j < right.length) edits++
        return edits <= 1
    }

    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        return decomposed
            .replace("\\p{M}+".toRegex(), "")
            .replace("[^\\p{L}\\p{N}\\s]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }
}
