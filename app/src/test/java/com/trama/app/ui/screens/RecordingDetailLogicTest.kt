package com.trama.app.ui.screens

import com.trama.shared.model.Recording
import com.trama.shared.model.RecordingStatus
import com.trama.shared.model.Source
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the pure logic used in RecordingDetailScreen:
 * - StatusBadge mapping (status + processedLocally -> label/color)
 * - Reprocess button visibility
 * - Key points JSON parsing
 * - Duration formatting
 */
class RecordingDetailLogicTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ── Helper ──

    private fun recording(
        transcription: String = "Some transcription",
        title: String? = "Test Recording",
        summary: String? = null,
        keyPoints: String? = null,
        durationSeconds: Int = 120,
        source: Source = Source.PHONE,
        processingStatus: String = RecordingStatus.PENDING,
        processedLocally: Boolean = false
    ) = Recording(
        transcription = transcription,
        title = title,
        summary = summary,
        keyPoints = keyPoints,
        durationSeconds = durationSeconds,
        source = source,
        processingStatus = processingStatus,
        processedLocally = processedLocally
    )

    // ────────────────────────────────────────────────────────────────────────
    // StatusBadge mapping
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Replicates the StatusBadge composable's when logic to derive label.
     */
    private fun statusLabel(rec: Recording): String = when {
        rec.processingStatus == RecordingStatus.COMPLETED && rec.processedLocally ->
            "Procesado localmente"
        rec.processingStatus == RecordingStatus.COMPLETED ->
            "Procesado con Gemini"
        rec.processingStatus == RecordingStatus.PROCESSING ->
            "Procesando..."
        rec.processingStatus == RecordingStatus.FAILED ->
            "Error al procesar"
        else ->
            "Pendiente"
    }

    private fun statusIconName(rec: Recording): String = when {
        rec.processingStatus == RecordingStatus.COMPLETED -> "CheckCircle"
        rec.processingStatus == RecordingStatus.PROCESSING -> "Spinner"
        rec.processingStatus == RecordingStatus.FAILED -> "Error"
        else -> "Schedule"
    }

    @Test
    fun `statusBadge completed locally shows correct label`() {
        val r = recording(processingStatus = RecordingStatus.COMPLETED, processedLocally = true)
        assertEquals("Procesado localmente", statusLabel(r))
    }

    @Test
    fun `statusBadge completed online shows Gemini label`() {
        val r = recording(processingStatus = RecordingStatus.COMPLETED, processedLocally = false)
        assertEquals("Procesado con Gemini", statusLabel(r))
    }

    @Test
    fun `statusBadge processing shows Procesando label`() {
        val r = recording(processingStatus = RecordingStatus.PROCESSING)
        assertEquals("Procesando...", statusLabel(r))
    }

    @Test
    fun `statusBadge failed shows error label`() {
        val r = recording(processingStatus = RecordingStatus.FAILED)
        assertEquals("Error al procesar", statusLabel(r))
    }

    @Test
    fun `statusBadge pending shows Pendiente label`() {
        val r = recording(processingStatus = RecordingStatus.PENDING)
        assertEquals("Pendiente", statusLabel(r))
    }

    @Test
    fun `statusBadge unknown status defaults to Pendiente`() {
        val r = recording(processingStatus = "SOMETHING_ELSE")
        assertEquals("Pendiente", statusLabel(r))
    }

    @Test
    fun `statusBadge icon is CheckCircle for completed`() {
        val r = recording(processingStatus = RecordingStatus.COMPLETED)
        assertEquals("CheckCircle", statusIconName(r))
    }

    @Test
    fun `statusBadge icon is Spinner for processing`() {
        val r = recording(processingStatus = RecordingStatus.PROCESSING)
        assertEquals("Spinner", statusIconName(r))
    }

    @Test
    fun `statusBadge icon is Error for failed`() {
        val r = recording(processingStatus = RecordingStatus.FAILED)
        assertEquals("Error", statusIconName(r))
    }

    @Test
    fun `statusBadge icon is Schedule for pending`() {
        val r = recording(processingStatus = RecordingStatus.PENDING)
        assertEquals("Schedule", statusIconName(r))
    }

    // ────────────────────────────────────────────────────────────────────────
    // Reprocess button visibility: FAILED || processedLocally
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `reprocess button visible when status is FAILED`() {
        val r = recording(processingStatus = RecordingStatus.FAILED, processedLocally = false)
        val showReprocess = r.processingStatus == RecordingStatus.FAILED || r.processedLocally
        assertTrue(showReprocess)
    }

    @Test
    fun `reprocess button visible when processedLocally is true`() {
        val r = recording(processingStatus = RecordingStatus.COMPLETED, processedLocally = true)
        val showReprocess = r.processingStatus == RecordingStatus.FAILED || r.processedLocally
        assertTrue(showReprocess)
    }

    @Test
    fun `reprocess button hidden when completed online`() {
        val r = recording(processingStatus = RecordingStatus.COMPLETED, processedLocally = false)
        val showReprocess = r.processingStatus == RecordingStatus.FAILED || r.processedLocally
        assertFalse(showReprocess)
    }

    @Test
    fun `reprocess button hidden when pending`() {
        val r = recording(processingStatus = RecordingStatus.PENDING, processedLocally = false)
        val showReprocess = r.processingStatus == RecordingStatus.FAILED || r.processedLocally
        assertFalse(showReprocess)
    }

    @Test
    fun `reprocess button hidden when processing`() {
        val r = recording(processingStatus = RecordingStatus.PROCESSING, processedLocally = false)
        val showReprocess = r.processingStatus == RecordingStatus.FAILED || r.processedLocally
        assertFalse(showReprocess)
    }

    @Test
    fun `reprocess button visible when failed AND processedLocally`() {
        val r = recording(processingStatus = RecordingStatus.FAILED, processedLocally = true)
        val showReprocess = r.processingStatus == RecordingStatus.FAILED || r.processedLocally
        assertTrue(showReprocess)
    }

    // ────────────────────────────────────────────────────────────────────────
    // Key points JSON parsing
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `keyPoints parses valid JSON array`() {
        val kp = """["Point one","Point two","Point three"]"""
        val result = json.decodeFromString<List<String>>(kp)
        assertEquals(3, result.size)
        assertEquals("Point one", result[0])
        assertEquals("Point two", result[1])
        assertEquals("Point three", result[2])
    }

    @Test
    fun `keyPoints parses empty JSON array`() {
        val kp = "[]"
        val result = json.decodeFromString<List<String>>(kp)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `keyPoints returns null on invalid JSON`() {
        val kp = "not valid json"
        val result = try {
            json.decodeFromString<List<String>>(kp)
        } catch (_: Exception) {
            null
        }
        assertNull(result)
    }

    @Test
    fun `keyPoints returns null on null input`() {
        val kp: String? = null
        val result = kp?.let {
            try {
                json.decodeFromString<List<String>>(it)
            } catch (_: Exception) {
                null
            }
        }
        assertNull(result)
    }

    @Test
    fun `keyPoints with unicode content parses correctly`() {
        val kp = """["Punto con acentos: cafe","Punto con enie: espanol"]"""
        val result = json.decodeFromString<List<String>>(kp)
        assertEquals(2, result.size)
        assertTrue(result[0].contains("cafe"))
    }

    @Test
    fun `keyPoints section hidden when result is empty list`() {
        val kp = "[]"
        val parsed = json.decodeFromString<List<String>>(kp)
        val showSection = !parsed.isNullOrEmpty()
        assertFalse(showSection)
    }

    @Test
    fun `keyPoints section hidden when parsing fails`() {
        val kp = "invalid"
        val parsed = try {
            json.decodeFromString<List<String>>(kp)
        } catch (_: Exception) {
            null
        }
        val showSection = !parsed.isNullOrEmpty()
        assertFalse(showSection)
    }

    // ────────────────────────────────────────────────────────────────────────
    // Duration formatting (same as RecordingHeader)
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `duration formats correctly for 2 minutes`() {
        val r = recording(durationSeconds = 120)
        val min = r.durationSeconds / 60
        val sec = r.durationSeconds % 60
        assertEquals("2:00", "%d:%02d".format(min, sec))
    }

    @Test
    fun `duration formats correctly for partial seconds`() {
        val r = recording(durationSeconds = 95)
        val min = r.durationSeconds / 60
        val sec = r.durationSeconds % 60
        assertEquals("1:35", "%d:%02d".format(min, sec))
    }

    // ────────────────────────────────────────────────────────────────────────
    // Title display
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `title fallback to Grabacion when null`() {
        val r = recording(title = null)
        val displayTitle = r.title ?: "Grabacion"
        assertEquals("Grabacion", displayTitle)
    }

    @Test
    fun `title shown when present`() {
        val r = recording(title = "Important meeting")
        val displayTitle = r.title ?: "Grabacion"
        assertEquals("Important meeting", displayTitle)
    }

    // ────────────────────────────────────────────────────────────────────────
    // Summary section visibility
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `summary section visible when summary is not null`() {
        val r = recording(summary = "This is a summary")
        assertTrue(r.summary != null)
    }

    @Test
    fun `summary section hidden when summary is null`() {
        val r = recording(summary = null)
        assertFalse(r.summary != null)
    }
}
