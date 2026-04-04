package com.mydiary.shared.model

import org.junit.Assert.*
import org.junit.Test

class RecordingTest {

    private fun makeRecording(
        transcription: String = "Reunión con el equipo de producto",
        durationSeconds: Int = 120,
        source: Source = Source.PHONE
    ) = Recording(
        transcription = transcription,
        durationSeconds = durationSeconds,
        source = source
    )

    @Test
    fun `default id is zero`() {
        assertEquals(0L, makeRecording().id)
    }

    @Test
    fun `default processingStatus is PENDING`() {
        assertEquals(RecordingStatus.PENDING, makeRecording().processingStatus)
    }

    @Test
    fun `default processedLocally is false`() {
        assertFalse(makeRecording().processedLocally)
    }

    @Test
    fun `default isSynced is false`() {
        assertFalse(makeRecording().isSynced)
    }

    @Test
    fun `nullable fields default to null`() {
        val r = makeRecording()
        assertNull(r.title)
        assertNull(r.summary)
        assertNull(r.keyPoints)
    }

    @Test
    fun `can create recording with all fields`() {
        val r = Recording(
            id = 42,
            title = "Standup",
            transcription = "text",
            summary = "summary",
            keyPoints = """["punto 1","punto 2"]""",
            durationSeconds = 300,
            source = Source.WATCH,
            createdAt = 1000L,
            processingStatus = RecordingStatus.COMPLETED,
            processedLocally = true,
            isSynced = true
        )
        assertEquals(42L, r.id)
        assertEquals("Standup", r.title)
        assertEquals(RecordingStatus.COMPLETED, r.processingStatus)
        assertTrue(r.processedLocally)
        assertTrue(r.isSynced)
        assertEquals(Source.WATCH, r.source)
    }
}

class RecordingStatusTest {

    @Test
    fun `status constants have correct values`() {
        assertEquals("PENDING", RecordingStatus.PENDING)
        assertEquals("PROCESSING", RecordingStatus.PROCESSING)
        assertEquals("COMPLETED", RecordingStatus.COMPLETED)
        assertEquals("FAILED", RecordingStatus.FAILED)
    }

    @Test
    fun `all four statuses are distinct`() {
        val statuses = setOf(
            RecordingStatus.PENDING,
            RecordingStatus.PROCESSING,
            RecordingStatus.COMPLETED,
            RecordingStatus.FAILED
        )
        assertEquals(4, statuses.size)
    }
}
