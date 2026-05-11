package com.trama.app.summary

import org.junit.Assert.assertEquals
import org.junit.Test

class UserLearningDecisionEngineTest {

    @Test
    fun `noise feedback discards similar weak candidate`() {
        val decision = decide(
            text = "Totalmente, si, si, y todo que recordar",
            cleanText = "Totalmente, si, si, y todo que recordar",
            isActionable = false,
            matchedReason = DeletionFeedbackStore.Reason.NOISE.storageKey,
            similarity = 0.82f
        )

        assertEquals(UserLearningDecisionEngine.Route.DISCARD, decision.route)
    }

    @Test
    fun `strong task similar to noise is protected as suggested`() {
        val decision = decide(
            text = "Recordar actualizar app para comunicar cuando esta reproduciendo un video o Spotify",
            cleanText = "Actualizar app para comunicar cuando esta reproduciendo un video o Spotify",
            isActionable = true,
            actionabilityScore = 0.84f,
            matchedReason = DeletionFeedbackStore.Reason.NOISE.storageKey,
            similarity = 0.78f
        )

        assertEquals(UserLearningDecisionEngine.Route.DEMOTE_TO_SUGGESTED, decision.route)
    }

    @Test
    fun `bad asr does not discard clear personal action`() {
        val decision = decide(
            text = "Tengo que arreglar el coche de mi padre y hacerla",
            cleanText = "Arreglar el coche de mi padre",
            isActionable = true,
            actionabilityScore = 0.76f,
            matchedReason = DeletionFeedbackStore.Reason.BAD_TRANSCRIPTION.storageKey,
            similarity = 0.72f
        )

        assertEquals(UserLearningDecisionEngine.Route.DEMOTE_TO_SUGGESTED, decision.route)
    }

    @Test
    fun `other feedback does not block candidate`() {
        val decision = decide(
            text = "Comprar una cafetera",
            cleanText = "Comprar una cafetera",
            isActionable = true,
            matchedReason = DeletionFeedbackStore.Reason.OTHER.storageKey,
            similarity = 0.9f
        )

        assertEquals(UserLearningDecisionEngine.Route.KEEP, decision.route)
    }

    @Test
    fun `accepted examples only rescue borderline to suggested`() {
        val decision = decide(
            text = "Buscarlo de la oferta de Longhi",
            cleanText = "Buscar la oferta de Longhi",
            isActionable = false,
            usefulnessScore = 0.58f,
            actionabilityScore = 0.56f,
            acceptedSimilarity = 0.65f
        )

        assertEquals(UserLearningDecisionEngine.Route.DEMOTE_TO_SUGGESTED, decision.route)
    }

    @Test
    fun `long conversation without concrete action is discarded on strong noise match`() {
        val decision = decide(
            text = "Vale, y como un poco las posible, mirando todo un rato, entonces no tenemos nada ni tenemos la dirección por parte de Renault",
            cleanText = "Vale, mirando todo un rato no tenemos nada",
            isActionable = false,
            matchedReason = DeletionFeedbackStore.Reason.NOISE.storageKey,
            similarity = 0.74f
        )

        assertEquals(UserLearningDecisionEngine.Route.DISCARD, decision.route)
    }

    private fun decide(
        text: String,
        cleanText: String,
        isActionable: Boolean,
        usefulnessScore: Float = if (isActionable) 0.8f else 0.2f,
        actionabilityScore: Float = if (isActionable) 0.8f else 0.2f,
        matchedReason: String? = null,
        similarity: Float = 0f,
        acceptedSimilarity: Float = 0f
    ): UserLearningDecisionEngine.LearningDecision =
        UserLearningDecisionEngine.decide(
            UserLearningDecisionEngine.Candidate(
                originalText = text,
                cleanText = cleanText,
                actionType = "GENERIC",
                confidence = if (isActionable) 0.8f else 0.29f,
                usefulnessScore = usefulnessScore,
                actionabilityScore = actionabilityScore,
                kind = if (isActionable) "TASK" else "UNCLEAR",
                isActionable = isActionable,
                assessment = DeletionFeedbackStore.LearningAssessment(
                    matchedReason = matchedReason,
                    similarity = similarity,
                    acceptedSimilarity = acceptedSimilarity
                )
            )
        )
}
