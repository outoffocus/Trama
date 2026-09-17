package com.trama.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecordingTranscriptionCheckpointTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun progressSurvivesRestartAndRejectsChangedAudio() {
        val audio = temporaryFolder.newFile("meeting.pcm").apply {
            writeBytes(ByteArray(32_000))
        }
        val checkpoint = RecordingTranscriptionCheckpoint(
            sourceLength = audio.length(),
            sampleRateHz = 16_000,
            nextChunkIndex = 17,
            acceptedText = listOf("primer bloque", "segundo bloque"),
            totalDecodeMs = 91_000,
            acceptedChunks = 12,
            rejectedChunks = 5,
            rejectReasons = listOf("blank"),
            filterVersion = RecordingTranscriptionCheckpoint.CURRENT_FILTER_VERSION
        )

        RecordingTranscriptionCheckpointStore.save(audio, checkpoint)

        assertEquals(
            checkpoint,
            RecordingTranscriptionCheckpointStore.load(audio, 16_000, expectedChunks = 144)
        )
        assertNull(RecordingTranscriptionCheckpointStore.load(audio, 8_000, expectedChunks = 144))

        audio.appendBytes(byteArrayOf(1, 2))
        assertNull(RecordingTranscriptionCheckpointStore.load(audio, 16_000, expectedChunks = 144))

        RecordingTranscriptionCheckpointStore.clear(audio)
        assertTrue(!RecordingTranscriptionCheckpointStore.fileFor(audio).exists())
    }

    @Test
    fun oldFilterCheckpointIsNotResumed() {
        val audio = temporaryFolder.newFile("old-filter.pcm").apply {
            writeBytes(ByteArray(32_000))
        }
        RecordingTranscriptionCheckpointStore.save(
            audio,
            RecordingTranscriptionCheckpoint(
                sourceLength = audio.length(),
                sampleRateHz = 16_000,
                nextChunkIndex = 1,
                acceptedText = listOf("texto incompleto"),
                totalDecodeMs = 1_000,
                acceptedChunks = 1,
                rejectedChunks = 0,
                rejectReasons = emptyList()
            )
        )

        assertNull(RecordingTranscriptionCheckpointStore.load(audio, 16_000, expectedChunks = 2))
    }
}
