package com.trama.app.audio

import com.trama.shared.audio.CircularAudioBuffer
import com.trama.shared.audio.ContextualCaptureAssembler
import com.trama.shared.audio.ContextualCaptureConfig
import com.trama.shared.audio.CapturedAudioWindow
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ContextualCaptureAssemblerTest {

    @Test
    fun finalizeWindow_mergesPreRollAndCapturedAudio() {
        val config = ContextualCaptureConfig(preRollSeconds = 1, postRollSeconds = 2, sampleRateHz = 4)
        val buffer = CircularAudioBuffer(sampleRateHz = 4, maxSeconds = 3)
        buffer.append(shortArrayOf(1, 2, 3, 4, 5, 6))

        val assembler = ContextualCaptureAssembler(config)
        val preRoll = assembler.beginCapture(buffer)
        assembler.appendPostRoll(shortArrayOf(7, 8, 9))
        assembler.appendPostRoll(shortArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18))

        val finalWindow = assembler.finalizeWindow(preRoll)

        assertArrayEquals(shortArrayOf(3, 4, 5, 6), finalWindow.preRollPcm)
        assertArrayEquals(shortArrayOf(7, 8, 9, 10, 11, 12, 13, 14), finalWindow.livePcm)
        assertArrayEquals(
            shortArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14),
            finalWindow.mergedPcm()
        )
    }

    @Test
    fun tailWindow_returnsOnlyRequestedTailDuration() {
        val window = CapturedAudioWindow(
            preRollPcm = shortArrayOf(1, 2, 3, 4),
            livePcm = shortArrayOf(5, 6, 7, 8, 9, 10),
            sampleRateHz = 2
        )

        val tail = window.tailWindow(2_000)

        assertEquals(0, tail.preRollPcm.size)
        assertArrayEquals(shortArrayOf(7, 8, 9, 10), tail.livePcm)
    }
}
