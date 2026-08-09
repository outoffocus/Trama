package com.trama.app.service

/** Combines lexical confidence, grammatical ownership and speaker evidence. */
object IntentAcceptancePolicy {
    const val DIRECT_SAVE_CONFIDENCE = 0.82f

    data class Decision(
        val routeToSuggested: Boolean,
        val reasons: List<String>
    )

    fun evaluate(
        detectorConfidence: Float,
        ownerVerified: Boolean,
        weakGrammaticalOwnership: Boolean
    ): Decision {
        val reasons = buildList {
            if (detectorConfidence < DIRECT_SAVE_CONFIDENCE) add("low_detector_confidence")
            if (!ownerVerified) add("speaker_not_verified")
            if (weakGrammaticalOwnership) add("weak_grammatical_ownership")
        }
        return Decision(routeToSuggested = reasons.isNotEmpty(), reasons = reasons)
    }
}
