package com.trama.shared.speech

import java.text.Normalizer

/**
 * Detects semantic intents in transcribed speech using scored lexical candidates.
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
        private const val MIN_CONFIDENCE = 0.70f
        private const val ORDER_BUCKET = 10_000
        private const val CUSTOM_ORDER_OFFSET = 1_000_000
        private val OWNERSHIP_CONTEXT = Regex(
            "(?:^|\\s)(tengo|tenemos|necesito|necesitamos|me|nos|he)(?=\\s|$)"
        )
        private val SPECIFIC_COMMUNICATION = Regex("hablar con|mensaje|contestar|responder|avisar")
    }

    @Volatile
    private var patterns: List<IntentPattern> = IntentPattern.DEFAULTS

    /** User-added keywords participate as high-priority, word-bounded candidates. */
    @Volatile
    private var customKeywords: List<String> = emptyList()

    @Volatile
    private var captureProfile: CaptureProfile = CaptureProfile.STRICT

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

    fun setCaptureProfile(profile: CaptureProfile) {
        captureProfile = profile
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
        val label: String,
        /** Deterministic detector confidence, independent from the later LLM review. */
        val confidence: Float = 1f,
        /** Exact normalized trigger that produced the winning candidate. */
        val matchedTrigger: String? = null,
        /** Stable diagnostic reasons that contributed to the score. */
        val scoreReasons: List<String> = emptyList()
    )

    private data class Candidate(
        val pattern: IntentPattern?,
        val customKeyword: String?,
        val label: String,
        val trigger: String,
        val confidence: Float,
        val reasons: List<String>,
        val order: Int
    )

    /**
     * Detect if the given text contains any known intent.
     *
     * Evaluates every active trigger and returns the strongest candidate. Matching
     * happens over normalized tokens, so a keyword such as "cita" cannot match
     * inside "necesitar".
     */
    fun detect(text: String): DetectionResult? {
        if (text.length < MIN_TEXT_LENGTH) return null

        val normalizedText = normalize(text)
        if (normalizedText.isBlank()) return null
        val candidates = buildList {
            CaptureIntentRules.find(normalizedText, captureProfile)?.let { rule ->
                patterns.firstOrNull { it.id == rule.intentId && it.enabled }?.let { matchedPattern ->
                    add(
                        Candidate(
                            pattern = matchedPattern,
                            customKeyword = null,
                            label = rule.label,
                            trigger = rule.trigger,
                            confidence = rule.confidence,
                            reasons = rule.reasons,
                            order = -1
                        )
                    )
                }
            }
            patterns.forEachIndexed { patternIndex, pattern ->
                if (!pattern.enabled) return@forEachIndexed
                pattern.normalizedTriggers.forEachIndexed { triggerIndex, trigger ->
                    findBounded(normalizedText, trigger)?.let { match ->
                        add(
                            scoreCandidate(
                                normalizedText = normalizedText,
                                trigger = trigger,
                                matchEndExclusive = match.last + 1,
                                pattern = pattern,
                                customKeyword = null,
                                label = pattern.label,
                                order = patternIndex * ORDER_BUCKET + triggerIndex
                            )
                        )
                    }
                }
            }
            customKeywords.forEachIndexed { index, keyword ->
                val trigger = normalize(keyword)
                findBounded(normalizedText, trigger)?.let { match ->
                    add(
                        scoreCandidate(
                            normalizedText = normalizedText,
                            trigger = trigger,
                            matchEndExclusive = match.last + 1,
                            pattern = null,
                            customKeyword = keyword,
                            label = keyword,
                            order = CUSTOM_ORDER_OFFSET + index
                        )
                    )
                }
            }
        }

        val winner = candidates
            .filter { it.confidence >= MIN_CONFIDENCE }
            .maxWithOrNull(
                compareBy<Candidate> { it.confidence }
                    .thenBy { it.trigger.length }
                    .thenBy { -it.order }
            ) ?: return null
        val captured = if (winner.pattern?.captureAll == false) {
            winner.pattern.triggers
                .asSequence()
                .filter { normalize(it) == winner.trigger }
                .mapNotNull { trigger ->
                    IntentPattern.buildRegex(listOf(trigger)).find(text)?.range?.first
                }
                .minOrNull()
                ?.let(text::substring)
                ?: text
        } else {
            text
        }
        return DetectionResult(
            pattern = winner.pattern,
            customKeyword = winner.customKeyword,
            capturedText = captured,
            label = winner.label,
            confidence = winner.confidence,
            matchedTrigger = winner.trigger,
            scoreReasons = winner.reasons
        )
    }

    /**
     * Detect intent in partial results (streaming).
     * Requires longer text to reduce false positives.
     */
    fun detectPartial(text: String): DetectionResult? {
        if (text.length < MIN_PARTIAL_LENGTH) return null
        return detect(text)
    }

    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        return decomposed
            .replace("\\p{M}+".toRegex(), "")
            .replace("[^\\p{L}\\p{N}\\s]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun findBounded(text: String, trigger: String): IntRange? {
        if (trigger.isBlank()) return null
        var start = text.indexOf(trigger)
        while (start >= 0) {
            val endExclusive = start + trigger.length
            val startsOnBoundary = start == 0 || text[start - 1] == ' '
            val endsOnBoundary = endExclusive == text.length || text[endExclusive] == ' '
            if (startsOnBoundary && endsOnBoundary) return start until endExclusive
            start = text.indexOf(trigger, startIndex = start + 1)
        }
        return null
    }

    private fun scoreCandidate(
        normalizedText: String,
        trigger: String,
        matchEndExclusive: Int,
        pattern: IntentPattern?,
        customKeyword: String?,
        label: String,
        order: Int
    ): Candidate {
        val reasons = mutableListOf<String>()
        val tokenCount = trigger.split(" ").count { it.isNotBlank() }
        var score = if (customKeyword != null) {
            reasons += "user_keyword"
            0.70f
        } else {
            reasons += "category:${pattern?.id}"
            0.72f
        }
        if (tokenCount > 1) {
            score += minOf(0.12f, (tokenCount - 1) * 0.03f)
            reasons += "multiword:$tokenCount"
        }
        if (customKeyword != null && tokenCount == 1) {
            if (captureProfile == CaptureProfile.SENSITIVE) {
                score += 0.03f
                reasons += "sensitive_single_word_keyword"
            } else {
                score -= 0.08f
                reasons += "single_word_keyword"
            }
        }
        if (OWNERSHIP_CONTEXT.containsMatchIn(trigger)) {
            score += 0.06f
            reasons += "owned_context"
        }
        if (
            pattern?.id == "tareas" &&
            trigger.startsWithAny("pendiente de", "falta por", "queda pendiente")
        ) {
            score -= 0.06f
            reasons += "weak_ownership"
        }
        val categoryBonus = when (pattern?.id) {
            "recordatorios" -> 0.05f
            "compromisos" -> 0.04f
            "tareas" -> 0.03f
            "comunicacion" -> if (SPECIFIC_COMMUNICATION.containsMatchIn(trigger)) 0.04f else -0.02f
            else -> 0f
        }
        if (categoryBonus != 0f) {
            score += categoryBonus
            reasons += "category_specificity"
        }
        val hasComplement = normalizedText.substring(matchEndExclusive)
            .trim()
            .split(" ")
            .any { it.length >= 2 }
        if (hasComplement) {
            score += 0.05f
            reasons += "has_complement"
        } else if (customKeyword == null) {
            score -= 0.12f
            reasons += "missing_complement"
        }
        return Candidate(
            pattern = pattern,
            customKeyword = customKeyword,
            label = label,
            trigger = trigger,
            confidence = score.coerceIn(0f, 0.99f),
            reasons = reasons,
            order = order
        )
    }

    private fun String.startsWithAny(vararg prefixes: String): Boolean = prefixes.any(::startsWith)

}
