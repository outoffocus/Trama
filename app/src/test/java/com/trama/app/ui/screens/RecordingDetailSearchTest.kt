package com.trama.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingDetailSearchTest {
    @Test
    fun `transcript search counts case insensitive occurrences`() {
        assertEquals(3, countTextMatches("Acción uno, acción dos. ACCIÓN tres.", "acción"))
    }

    @Test
    fun `blank transcript query has no matches`() {
        assertEquals(0, countTextMatches("texto", "   "))
    }
}
