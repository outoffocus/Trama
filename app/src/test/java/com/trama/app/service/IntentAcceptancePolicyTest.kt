package com.trama.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentAcceptancePolicyTest {
    @Test
    fun `strong lexical grammatical and speaker evidence saves directly`() {
        val decision = IntentAcceptancePolicy.evaluate(
            detectorConfidence = 0.9f,
            ownerVerified = true,
            weakGrammaticalOwnership = false
        )

        assertFalse(decision.routeToSuggested)
        assertTrue(decision.reasons.isEmpty())
    }

    @Test
    fun `unverified speaker routes candidate to suggestions`() {
        val decision = IntentAcceptancePolicy.evaluate(0.9f, false, false)

        assertTrue(decision.routeToSuggested)
        assertTrue("speaker_not_verified" in decision.reasons)
    }

    @Test
    fun `low lexical confidence or weak ownership routes to suggestions`() {
        val lowConfidence = IntentAcceptancePolicy.evaluate(0.75f, true, false)
        val weakOwnership = IntentAcceptancePolicy.evaluate(0.9f, true, true)

        assertTrue(lowConfidence.routeToSuggested)
        assertTrue("low_detector_confidence" in lowConfidence.reasons)
        assertTrue(weakOwnership.routeToSuggested)
        assertTrue("weak_grammatical_ownership" in weakOwnership.reasons)
    }
}
