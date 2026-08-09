package com.trama.wear.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneticIntentDetectorTest {

    @Test
    fun detectsTengQueCue() {
        val match = PhoneticIntentDetector.detect("t e n g o k e k o m p ɾ a ɾ")

        assertNotNull(match)
        assertEquals("phonetic_tengo_que", match?.intentId)
        assertTrue(match!!.confidence >= 0.8f)
    }

    @Test
    fun detectsRecordarCue() {
        val match = PhoneticIntentDetector.detect("ɾ e k o ɾ d a ɾ")

        assertNotNull(match)
        assertEquals("phonetic_recordar", match?.intentId)
    }

    @Test
    fun ignoresUnrelatedPhonemes() {
        val match = PhoneticIntentDetector.detect("b u e n o s d i a s")

        assertNull(match)
    }

    @Test
    fun ignoresIsolatedActionVerbWithoutGrammaticalCue() {
        val match = PhoneticIntentDetector.detect("k o m p ɾ a ɾ")

        assertNull(match)
    }
}
