package com.trama.app.ui.components

import com.trama.shared.model.Recording
import com.trama.shared.model.RecordingStatus
import com.trama.shared.model.Source
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingCardStatusTest {
    @Test
    fun `processing phases state that audio is already saved`() {
        assertEquals(
            "Audio guardado · transcribiendo",
            recordingStatusLabel(recording(RecordingStatus.TRANSCRIBING, "/audio/meeting.pcm"))
        )
        assertEquals(
            "Audio guardado · preparando notas",
            recordingStatusLabel(recording(RecordingStatus.PROCESSING, "/audio/meeting.pcm"))
        )
    }

    @Test
    fun `failed processing distinguishes preserved audio`() {
        assertEquals(
            "Error al procesar · audio conservado",
            recordingStatusLabel(recording(RecordingStatus.FAILED, "/audio/meeting.pcm"))
        )
        assertEquals(
            "Error de grabación",
            recordingStatusLabel(recording(RecordingStatus.FAILED, null))
        )
    }

    private fun recording(status: String, path: String?) = Recording(
        transcription = "",
        durationSeconds = 30,
        source = Source.PHONE,
        processingStatus = status,
        audioFilePath = path
    )
}
