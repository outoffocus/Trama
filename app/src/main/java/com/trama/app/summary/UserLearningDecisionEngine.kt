package com.trama.app.summary

import java.text.Normalizer
import java.util.Locale

/**
 * Final local arbiter for user feedback.
 *
 * The LLM extracts and repairs; this layer only decides whether an already-built
 * candidate should be created, suggested, or hidden based on local feedback.
 */
object UserLearningDecisionEngine {

    enum class Route {
        KEEP,
        DEMOTE_TO_SUGGESTED,
        DISCARD
    }

    data class Candidate(
        val originalText: String,
        val cleanText: String,
        val actionType: String,
        val confidence: Float,
        val usefulnessScore: Float,
        val actionabilityScore: Float,
        val kind: String,
        val isActionable: Boolean,
        val assessment: DeletionFeedbackStore.LearningAssessment
    )

    data class LearningDecision(
        val route: Route,
        val scoreDelta: Float,
        val reason: String,
        val matchedReason: String?,
        val similarity: Float
    )

    fun decide(candidate: Candidate): LearningDecision {
        val assessment = candidate.assessment
        val matchedReason = assessment.matchedReason
        val similarity = assessment.similarity
        val strongPersonalAction = hasStrongPersonalAction(candidate.originalText, candidate.cleanText)
        val actionableEnough = candidate.isActionable || candidate.actionabilityScore >= 0.55f

        if (matchedReason == DeletionFeedbackStore.Reason.NOISE.storageKey ||
            matchedReason == DeletionFeedbackStore.Reason.NOT_FOR_ME.storageKey
        ) {
            if (similarity >= STRONG_NEGATIVE_MATCH) {
                return if (strongPersonalAction && actionableEnough) {
                    decision(Route.DEMOTE_TO_SUGGESTED, -0.25f, "strong_personal_action_protected", matchedReason, similarity)
                } else {
                    decision(Route.DISCARD, -0.45f, "strong_negative_feedback_match", matchedReason, similarity)
                }
            }
            if (similarity >= SOFT_NEGATIVE_MATCH && actionableEnough) {
                return decision(Route.DEMOTE_TO_SUGGESTED, -0.20f, "soft_negative_feedback_match", matchedReason, similarity)
            }
        }

        if (matchedReason == DeletionFeedbackStore.Reason.BAD_TRANSCRIPTION.storageKey &&
            similarity >= BAD_ASR_MATCH
        ) {
            return if (strongPersonalAction) {
                decision(Route.DEMOTE_TO_SUGGESTED, -0.15f, "bad_asr_but_action_protected", matchedReason, similarity)
            } else {
                decision(Route.DISCARD, -0.30f, "bad_asr_like_candidate", matchedReason, similarity)
            }
        }

        if (!candidate.isActionable &&
            assessment.acceptedSimilarity >= ACCEPTED_BORDERLINE_MATCH &&
            looksLikeBorderlineAction(candidate)
        ) {
            return decision(
                Route.DEMOTE_TO_SUGGESTED,
                0.12f,
                "accepted_feedback_rescued_borderline",
                "accepted",
                assessment.acceptedSimilarity
            )
        }

        return decision(Route.KEEP, assessment.scoreDelta, "no_learning_override", matchedReason, similarity)
    }

    private fun decision(
        route: Route,
        scoreDelta: Float,
        reason: String,
        matchedReason: String?,
        similarity: Float
    ): LearningDecision = LearningDecision(
        route = route,
        scoreDelta = scoreDelta,
        reason = reason,
        matchedReason = matchedReason,
        similarity = similarity
    )

    private fun looksLikeBorderlineAction(candidate: Candidate): Boolean {
        if (candidate.kind.equals("DISCARD", ignoreCase = true)) return false
        if (candidate.usefulnessScore >= 0.55f || candidate.actionabilityScore >= 0.55f) return true
        return hasStrongPersonalAction(candidate.originalText, candidate.cleanText)
    }

    private fun hasStrongPersonalAction(originalText: String, cleanText: String): Boolean {
        val normalized = normalize("$originalText $cleanText")
        if (normalized.isBlank()) return false
        val hasTrigger = STRONG_PERSONAL_ACTION_PATTERNS.any { it.containsMatchIn(normalized) } ||
            cleanTextActionTypeShape(cleanText)
        if (!hasTrigger) return false
        return hasConcreteObject(normalized) || STRONG_ACTION_TIME_RE.containsMatchIn(normalized)
    }

    private fun cleanTextActionTypeShape(cleanText: String): Boolean {
        val normalized = normalize(cleanText)
        return ACTION_VERBS.any { verb ->
            Regex("""\b$verb\b""").containsMatchIn(normalized)
        }
    }

    private fun hasConcreteObject(normalized: String): Boolean {
        val tokens = normalized
            .split(Regex("\\s+"))
            .map { it.trim('.', ',', ';', ':', '!', '?', '¿', '¡') }
            .filter { it.length >= 4 && it !in NON_OBJECT_TOKENS }
        return tokens.size >= 3
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private const val STRONG_NEGATIVE_MATCH = 0.70f
    private const val SOFT_NEGATIVE_MATCH = 0.45f
    private const val BAD_ASR_MATCH = 0.55f
    private const val ACCEPTED_BORDERLINE_MATCH = 0.50f

    private val STRONG_PERSONAL_ACTION_PATTERNS = listOf(
        Regex("""\b(?:recordar|recuerdame|acordarme|acordarnos)\b"""),
        Regex("""\b(?:tengo|tenemos|debo|debemos|necesito|necesitamos)\s+que\b"""),
        Regex("""\bhay\s+que\b""")
    )

    private val STRONG_ACTION_TIME_RE =
        Regex("""\b(?:hoy|manana|mañana|lunes|martes|miercoles|miércoles|jueves|viernes|sabado|sábado|domingo|\d{1,2}/\d{1,2})\b""")

    private val ACTION_VERBS = setOf(
        "llamar", "comprar", "enviar", "mandar", "escribir", "recoger", "pagar",
        "reservar", "pedir", "revisar", "buscar", "llevar", "traer", "arreglar",
        "actualizar", "felicitar", "contestar", "hacer", "ir"
    )

    private val NON_OBJECT_TOKENS = setOf(
        "tengo", "tenemos", "debo", "debemos", "necesito", "necesitamos",
        "recordar", "recuerdame", "acordarme", "acordarnos", "para", "porque",
        "cuando", "mañana", "manana", "luego", "esta", "este", "esto", "algo",
        "hacer", "llamar", "comprar", "enviar", "mandar", "escribir", "recoger",
        "pagar", "reservar", "pedir", "revisar", "buscar", "llevar", "traer",
        "arreglar", "actualizar", "felicitar", "contestar"
    )
}
