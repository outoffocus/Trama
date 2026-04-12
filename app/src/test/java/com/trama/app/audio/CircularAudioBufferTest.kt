package com.trama.app.audio

import com.trama.shared.audio.CircularAudioBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class CircularAudioBufferTest {

    @Test
    fun snapshotLast_returnsMostRecentSamples() {
        val buffer = CircularAudioBuffer(sampleRateHz = 4, maxSeconds = 2)

        buffer.append(shortArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        buffer.append(shortArrayOf(9, 10))

        assertArrayEquals(shortArrayOf(3, 4, 5, 6, 7, 8, 9, 10), buffer.snapshotLast(2, 4))
        assertArrayEquals(shortArrayOf(7, 8, 9, 10), buffer.snapshotLast(1, 4))
    }
}
