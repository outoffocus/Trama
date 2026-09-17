package com.trama.shared.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakerTurnsTest {
    @Test
    fun roundTripsTimestampedSpeakerTurns() {
        val turns = listOf(
            SpeakerTurn(0, 0, 4_500, "Abrimos la reunión."),
            SpeakerTurn(1, 4_500, 9_000, "Reviso el presupuesto.")
        )

        assertEquals(turns, SpeakerTurns.decode(SpeakerTurns.encode(turns)))
    }

    @Test
    fun malformedDiarizationDoesNotBreakMeetingDetail() {
        assertTrue(SpeakerTurns.decode("not-json").isEmpty())
    }
}
