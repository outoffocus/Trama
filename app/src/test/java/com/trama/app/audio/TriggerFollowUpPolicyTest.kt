package com.trama.app.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerFollowUpPolicyTest {
    @Test
    fun `trigger by itself arms the following phrase`() {
        assertTrue(TriggerFollowUpPolicy.needsFollowUp("Trama", "trama"))
        assertTrue(TriggerFollowUpPolicy.needsFollowUp("Oye, Trama.", "trama"))
    }

    @Test
    fun `trigger followed by an order stays in the current segment`() {
        assertFalse(TriggerFollowUpPolicy.needsFollowUp("Trama recuerda comprar leche", "trama"))
    }

    @Test
    fun `unrelated transcript cannot arm follow up`() {
        assertFalse(TriggerFollowUpPolicy.needsFollowUp("comprar leche", "trama"))
    }
}
