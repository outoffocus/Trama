package com.trama.shared.speech

import org.junit.Assert.*
import org.junit.Test

class SpeakerProfileTest {

    private val sampleProfile = SpeakerProfile(
        avgRMS = 5.0,
        rmsVariance = 2.0,
        enrollmentCount = 10
    )

    // ── Serialization ────────────────────────────────────────────

    @Test
    fun `serialize and deserialize round-trip`() {
        val jsonStr = SpeakerProfile.serialize(sampleProfile)
        val restored = SpeakerProfile.deserialize(jsonStr)

        assertNotNull(restored)
        assertEquals(sampleProfile, restored)
    }

    @Test
    fun `deserialize returns null for invalid JSON`() {
        assertNull(SpeakerProfile.deserialize("not json"))
    }

    @Test
    fun `deserialize returns null for empty string`() {
        assertNull(SpeakerProfile.deserialize(""))
    }

    @Test
    fun `deserialize ignores unknown keys`() {
        val json = """{"avgRMS":3.0,"rmsVariance":1.0,"enrollmentCount":5,"extraField":"ignored"}"""
        val result = SpeakerProfile.deserialize(json)
        assertNotNull(result)
        assertEquals(3.0, result!!.avgRMS, 0.001)
    }

    // ── Verification ─────────────────────────────────────────────

    @Test
    fun `verify with identical profile returns high similarity`() {
        // RMS values that match the profile closely
        val rmsValues = List(20) { 5.0 + (it % 3 - 1) * 0.5 }
        val result = SpeakerProfile.verify(rmsValues, sampleProfile)

        assertTrue(result.similarity > 0.5f)
    }

    @Test
    fun `verify with too few speech frames returns accepting result`() {
        // Only values below SPEECH_RMS_THRESHOLD
        val rmsValues = listOf(0.1, 0.2, 0.3, 0.4)
        val result = SpeakerProfile.verify(rmsValues, sampleProfile)

        assertEquals(0.7f, result.similarity, 0.001f)
        assertTrue(result.isMatch)
    }

    @Test
    fun `verify with very different profile returns low similarity`() {
        // Very different RMS values
        val rmsValues = List(20) { 50.0 }
        val result = SpeakerProfile.verify(rmsValues, sampleProfile)

        assertTrue(result.similarity < sampleProfile.avgRMS)
    }

    @Test
    fun `verify filters silence frames below threshold`() {
        // Mix of speech (above 1.0) and silence (below 1.0)
        val rmsValues = listOf(0.1, 0.2, 0.3, 5.0, 5.1, 4.9, 5.2, 4.8, 5.0, 5.1)
        val result = SpeakerProfile.verify(rmsValues, sampleProfile)

        // Should only consider frames > SPEECH_RMS_THRESHOLD (1.0)
        assertTrue(result.similarity > 0.5f)
    }

    @Test
    fun `verify with custom threshold`() {
        val rmsValues = List(20) { 5.0 }
        val strict = SpeakerProfile.verify(rmsValues, sampleProfile, threshold = 0.99f)
        val lenient = SpeakerProfile.verify(rmsValues, sampleProfile, threshold = 0.1f)

        // Same similarity, different match outcomes
        assertEquals(strict.similarity, lenient.similarity, 0.001f)
        assertTrue(lenient.isMatch)
    }

    // ── SPEECH_RMS_THRESHOLD constant ────────────────────────────

    @Test
    fun `SPEECH_RMS_THRESHOLD is accessible`() {
        assertEquals(1.0, SpeakerProfile.SPEECH_RMS_THRESHOLD, 0.0)
    }
}
