package com.trama.app.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeakerIdentityStitcherTest {
    @Test
    fun keepsSameSpeakerAcrossProcessingWindows() {
        val stitcher = SherpaMeetingDiarizer.SpeakerIdentityStitcher(0.60f)

        val first = stitcher.assign(floatArrayOf(1f, 0f, 0f))
        val sameVoice = stitcher.assign(floatArrayOf(0.98f, 0.08f, 0f))
        val otherVoice = stitcher.assign(floatArrayOf(0f, 1f, 0f))

        assertEquals(first, sameVoice)
        assertEquals(1, otherVoice)
    }

    @Test
    fun doesNotCollapseTwoLocalSpeakersIntoOneGlobalSpeaker() {
        val stitcher = SherpaMeetingDiarizer.SpeakerIdentityStitcher(0.60f)
        val first = stitcher.assign(floatArrayOf(1f, 0f))

        val second = stitcher.assign(floatArrayOf(0.99f, 0.01f), excluded = setOf(first))

        assertEquals(1, second)
    }
}
