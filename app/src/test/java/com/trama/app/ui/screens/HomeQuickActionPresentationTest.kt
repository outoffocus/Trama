package com.trama.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeQuickActionPresentationTest {
    @Test
    fun `idle exposes all three actions`() {
        val result = presentation()

        assertEquals("Escuchar", result.listeningLabel)
        assertEquals("Reunión", result.recordingLabel)
        assertEquals("Reloj", result.deviceLabel)
        assertTrue(result.listeningEnabled)
        assertTrue(result.recordingEnabled)
        assertTrue(result.deviceEnabled)
    }

    @Test
    fun `recording keeps only stop enabled and shows elapsed time`() {
        val result = presentation(isRecording = true, recordingElapsed = 125)

        assertFalse(result.listeningEnabled)
        assertEquals("Detener 2:05", result.recordingLabel)
        assertTrue(result.recordingEnabled)
        assertFalse(result.deviceEnabled)
    }

    @Test
    fun `watch control exposes recovery without conflicting actions`() {
        val result = presentation(watchActive = true)

        assertFalse(result.listeningEnabled)
        assertFalse(result.recordingEnabled)
        assertEquals("Recuperar", result.deviceLabel)
        assertTrue(result.deviceEnabled)
    }

    @Test
    fun `transfer blocks every action until its result`() {
        val result = presentation(transferInProgress = true)

        assertFalse(result.listeningEnabled)
        assertFalse(result.recordingEnabled)
        assertFalse(result.deviceEnabled)
        assertEquals("Transfiriendo…", result.deviceLabel)
    }

    private fun presentation(
        serviceRunning: Boolean = false,
        isRecording: Boolean = false,
        isRecordingProcessing: Boolean = false,
        recordingElapsed: Long = 0,
        watchActive: Boolean = false,
        transferInProgress: Boolean = false
    ) = homeQuickActionPresentation(
        serviceRunning,
        isRecording,
        isRecordingProcessing,
        recordingElapsed,
        watchActive,
        transferInProgress
    )
}
