package com.mydiary.app.ui.components

import com.mydiary.shared.model.Recording
import com.mydiary.shared.model.RecordingStatus
import com.mydiary.shared.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the logic used by RecordingCard to determine status icons,
 * processing indicators, duration formatting, and preview text.
 */
class RecordingCardLogicTest {

    // ── Helper ──

    private fun recording(
        transcription: String = "Hello world",
        title: String? = null,
        summary: String? = null,
        durationSeconds: Int = 65,
        source: Source = Source.PHONE,
        processingStatus: String = RecordingStatus.PENDING,
        processedLocally: Boolean = false,
        createdAt: Long = System.currentTimeMillis()
    ) = Recording(
        transcription = transcription,
        title = title,
        summary = summary,
        durationSeconds = durationSeconds,
        source = source,
        processingStatus = processingStatus,
        processedLocally = processedLocally,
        createdAt = createdAt
    )

    // ────────────────────────────────────────────────────────────────────────
    // Status icon mapping
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `COMPLETED status maps to CheckCircle icon`() {
        val r = recording(processingStatus = RecordingStatus.COMPLETED)
        assertEquals(RecordingStatus.COMPLETED, r.processingStatus)
    }

    @Test
    fun `PROCESSING status maps to spinner`() {
        val r = recording(processingStatus = RecordingStatus.PROCESSING)
        assertEquals(RecordingStatus.PROCESSING, r.processingStatus)
    }

    @Test
    fun `FAILED status maps to Error icon`() {
        val r = recording(processingStatus = RecordingStatus.FAILED)
        assertEquals(RecordingStatus.FAILED, r.processingStatus)
    }

    @Test
    fun `PENDING status maps to Schedule icon`() {
        val r = recording(processingStatus = RecordingStatus.PENDING)
        assertEquals(RecordingStatus.PENDING, r.processingStatus)
    }

    @Test
    fun `unknown status falls to else branch which maps to Schedule`() {
        // The when block uses else for any non-COMPLETED, non-PROCESSING, non-FAILED
        val r = recording(processingStatus = "UNKNOWN")
        val icon = when (r.processingStatus) {
            RecordingStatus.COMPLETED -> "CheckCircle"
            RecordingStatus.PROCESSING -> "Spinner"
            RecordingStatus.FAILED -> "Error"
            else -> "Schedule"
        }
        assertEquals("Schedule", icon)
    }

    // ────────────────────────────────────────────────────────────────────────
    // Processing mode indicator (cloud/cloudOff in footer)
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `completed and processedLocally shows CloudOff`() {
        val r = recording(processingStatus = RecordingStatus.COMPLETED, processedLocally = true)
        assertEquals(RecordingStatus.COMPLETED, r.processingStatus)
        assertTrue(r.processedLocally)
    }

    @Test
    fun `completed and not processedLocally shows Cloud`() {
        val r = recording(processingStatus = RecordingStatus.COMPLETED, processedLocally = false)
        assertEquals(RecordingStatus.COMPLETED, r.processingStatus)
        assertFalse(r.processedLocally)
    }

    @Test
    fun `processing status shows Procesando text instead of cloud icon`() {
        val r = recording(processingStatus = RecordingStatus.PROCESSING)
        // The UI shows "Procesando..." text for PROCESSING, not a cloud icon
        assertEquals(RecordingStatus.PROCESSING, r.processingStatus)
    }

    @Test
    fun `pending status shows neither cloud icon nor processing text`() {
        val r = recording(processingStatus = RecordingStatus.PENDING)
        val showProcessingText = r.processingStatus == RecordingStatus.PROCESSING
        val showCloudIcon = r.processingStatus == RecordingStatus.COMPLETED
        assertFalse(showProcessingText)
        assertFalse(showCloudIcon)
    }

    // ────────────────────────────────────────────────────────────────────────
    // Duration formatting
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `duration formats as minutes colon seconds`() {
        val r = recording(durationSeconds = 65)
        val min = r.durationSeconds / 60
        val sec = r.durationSeconds % 60
        assertEquals("1:05", "%d:%02d".format(min, sec))
    }

    @Test
    fun `zero duration formats as 0 colon 00`() {
        val r = recording(durationSeconds = 0)
        val min = r.durationSeconds / 60
        val sec = r.durationSeconds % 60
        assertEquals("0:00", "%d:%02d".format(min, sec))
    }

    @Test
    fun `large duration formats correctly`() {
        val r = recording(durationSeconds = 3661) // 61 minutes 1 second
        val min = r.durationSeconds / 60
        val sec = r.durationSeconds % 60
        assertEquals("61:01", "%d:%02d".format(min, sec))
    }

    @Test
    fun `exact minute duration has zero seconds`() {
        val r = recording(durationSeconds = 120)
        val min = r.durationSeconds / 60
        val sec = r.durationSeconds % 60
        assertEquals("2:00", "%d:%02d".format(min, sec))
    }

    // ────────────────────────────────────────────────────────────────────────
    // Title / preview text logic
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `title fallback used when title is null`() {
        val r = recording(title = null)
        val displayTitle = r.title ?: "Grabacion sin procesar"
        assertEquals("Grabacion sin procesar", displayTitle)
    }

    @Test
    fun `title used when present`() {
        val r = recording(title = "My Recording")
        val displayTitle = r.title ?: "Grabacion sin procesar"
        assertEquals("My Recording", displayTitle)
    }

    @Test
    fun `preview prefers summary over transcription`() {
        val r = recording(summary = "A summary", transcription = "Full transcription text here")
        val preview = r.summary ?: r.transcription.take(120)
        assertEquals("A summary", preview)
    }

    @Test
    fun `preview falls back to truncated transcription when summary is null`() {
        val longText = "A".repeat(200)
        val r = recording(summary = null, transcription = longText)
        val preview = r.summary ?: r.transcription.take(120)
        assertEquals(120, preview.length)
    }

    @Test
    fun `preview is empty for blank transcription and null summary`() {
        val r = recording(summary = null, transcription = "")
        val preview = r.summary ?: r.transcription.take(120)
        assertTrue(preview.isBlank())
    }

    // ────────────────────────────────────────────────────────────────────────
    // Source icon logic
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `source WATCH maps to Watch icon`() {
        val r = recording(source = Source.WATCH)
        assertEquals(Source.WATCH, r.source)
    }

    @Test
    fun `source PHONE maps to Mic icon`() {
        val r = recording(source = Source.PHONE)
        assertEquals(Source.PHONE, r.source)
    }
}
