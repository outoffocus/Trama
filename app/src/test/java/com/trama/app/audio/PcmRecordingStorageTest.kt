package com.trama.app.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PcmRecordingStorageTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `reads little endian PCM in bounded windows`() {
        val file = temporaryFolder.newFile("capture.pcm")
        val samples = shortArrayOf(1, -2, Short.MAX_VALUE, Short.MIN_VALUE, 25)
        file.outputStream().use { output ->
            samples.forEach { sample ->
                val value = sample.toInt()
                output.write(value and 0xff)
                output.write((value ushr 8) and 0xff)
            }
        }

        val windows = PcmRecordingStorage.readWindows(
            file = file,
            chunkDurationMs = 2_000L,
            sampleRateHz = 2
        ).toList()

        assertEquals(2, windows.size)
        assertArrayEquals(samples.copyOfRange(0, 4), windows[0].livePcm)
        assertArrayEquals(samples.copyOfRange(4, 5), windows[1].livePcm)
    }

    @Test
    fun `finalizing pending PCM atomically changes its durable name`() {
        val pending = temporaryFolder.newFile("recording.pcm.part")
        pending.writeBytes(byteArrayOf(1, 2, 3, 4))

        val finalized = PcmRecordingStorage.finalizePending(pending)

        assertEquals("recording.pcm", finalized.name)
        assertTrue(finalized.exists())
        assertFalse(pending.exists())
        assertEquals(4L, finalized.length())
    }

    @Test
    fun `duration is derived from persisted sample bytes`() {
        val file = temporaryFolder.newFile("duration.pcm")
        file.writeBytes(ByteArray(32_000))

        assertEquals(1, PcmRecordingStorage.durationSeconds(file, sampleRateHz = 16_000))
    }

    @Test
    fun `audio statistics are calculated by streaming little endian samples`() {
        val file = temporaryFolder.newFile("stats.pcm")
        file.writeBytes(byteArrayOf(0, 0, 0xE8.toByte(), 0x03, 0x18, 0xFC.toByte()))

        val stats = PcmRecordingStorage.audioStats(file)

        assertEquals(3L, stats.sampleCount)
        assertEquals(816.5, stats.rms, 0.1)
        assertEquals(1_000, stats.peak)
    }
}
