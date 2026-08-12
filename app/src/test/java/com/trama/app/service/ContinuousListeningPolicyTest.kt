package com.trama.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousListeningPolicyTest {

    @Test
    fun `disabling continuous audio preserves useful low cost sources`() {
        val availability = ContinuousListeningPolicy.availability(
            continuousListeningEnabled = false,
            ambientContextConfigured = true
        )

        assertFalse(availability.continuousAudio)
        assertFalse(availability.ambientContext)
        assertTrue(availability.manualRecording)
        assertTrue(availability.calendarSync)
        assertTrue(availability.locationTrace)
    }

    @Test
    fun `ambient context requires continuous audio`() {
        assertFalse(
            ContinuousListeningPolicy.availability(
                continuousListeningEnabled = true,
                ambientContextConfigured = false
            ).ambientContext
        )
        assertTrue(
            ContinuousListeningPolicy.availability(
                continuousListeningEnabled = true,
                ambientContextConfigured = true
            ).ambientContext
        )
    }

    @Test
    fun `boot reminder requires both listening intent and reminder preference`() {
        assertFalse(ContinuousListeningPolicy.shouldRequestBootReactivation(false, false))
        assertFalse(ContinuousListeningPolicy.shouldRequestBootReactivation(false, true))
        assertFalse(ContinuousListeningPolicy.shouldRequestBootReactivation(true, false))
        assertTrue(ContinuousListeningPolicy.shouldRequestBootReactivation(true, true))
    }
}
