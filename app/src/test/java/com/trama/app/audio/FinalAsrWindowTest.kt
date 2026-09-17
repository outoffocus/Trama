package com.trama.app.audio

import com.trama.shared.audio.CapturedAudioWindow
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class FinalAsrWindowTest {
    @Test
    fun `removes old contextual audio but retains the speech onset`() {
        val window = CapturedAudioWindow(
            preRollPcm = shortArrayOf(1, 2, 3, 4, 5),
            livePcm = shortArrayOf(6, 7, 8),
            sampleRateHz = 2
        )

        val focused = window.forFinalAsr(maxPreRollMs = 1_000, maxDurationMs = 10_000)

        assertArrayEquals(shortArrayOf(4, 5), focused.preRollPcm)
        assertArrayEquals(shortArrayOf(6, 7, 8), focused.livePcm)
    }

    @Test
    fun `caps the focused window after trimming pre-roll`() {
        val window = CapturedAudioWindow(
            preRollPcm = shortArrayOf(1, 2, 3, 4),
            livePcm = shortArrayOf(5, 6, 7, 8, 9, 10),
            sampleRateHz = 2
        )

        val focused = window.forFinalAsr(maxPreRollMs = 500, maxDurationMs = 2_000)

        assertEquals(2_000L, focused.durationMs())
        assertArrayEquals(shortArrayOf(7, 8, 9, 10), focused.livePcm)
    }
}
