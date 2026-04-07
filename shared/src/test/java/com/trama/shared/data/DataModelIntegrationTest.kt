package com.trama.shared.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.trama.shared.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Integration tests for cross-model interactions and Room entity annotation correctness.
 */
class DataModelIntegrationTest {

    private fun makeEntry(
        id: Long = 0,
        text: String = "test entry",
        keyword: String = "pendiente",
        category: String = "Pendientes",
        source: Source = Source.PHONE,
        createdAt: Long = 1000L,
        status: String = EntryStatus.PENDING,
        sourceRecordingId: Long? = null
    ) = DiaryEntry(
        id = id, text = text, keyword = keyword, category = category,
        confidence = 0.9f, createdAt = createdAt, source = source,
        duration = 5, status = status, sourceRecordingId = sourceRecordingId
    )

    private fun makeRecording(
        id: Long = 0,
        transcription: String = "test recording",
        durationSeconds: Int = 60,
        source: Source = Source.PHONE,
        createdAt: Long = 1000L,
        processingStatus: String = RecordingStatus.PENDING
    ) = Recording(
        id = id, transcription = transcription, durationSeconds = durationSeconds,
        source = source, createdAt = createdAt, processingStatus = processingStatus
    )

    // ── DiaryEntry <-> Recording linking ──

    @Test
    fun `entry with sourceRecordingId links to a recording`() {
        val recording = makeRecording(id = 42)
        val entry = makeEntry(sourceRecordingId = recording.id)
        assertEquals(42L, entry.sourceRecordingId)
    }

    @Test
    fun `entry without sourceRecordingId has null link`() {
        val entry = makeEntry()
        assertNull(entry.sourceRecordingId)
    }

    @Test
    fun `multiple entries can reference the same recording`() {
        val recording = makeRecording(id = 10)
        val entry1 = makeEntry(id = 1, text = "action 1", sourceRecordingId = recording.id)
        val entry2 = makeEntry(id = 2, text = "action 2", sourceRecordingId = recording.id)
        assertEquals(entry1.sourceRecordingId, entry2.sourceRecordingId)
        assertEquals(recording.id, entry1.sourceRecordingId)
    }

    // ── Recording status transitions ──

    @Test
    fun `recording default status is PENDING`() {
        val recording = makeRecording()
        assertEquals(RecordingStatus.PENDING, recording.processingStatus)
    }

    @Test
    fun `recording can be created with PROCESSING status`() {
        val recording = makeRecording(processingStatus = RecordingStatus.PROCESSING)
        assertEquals(RecordingStatus.PROCESSING, recording.processingStatus)
    }

    @Test
    fun `recording can be created with COMPLETED status`() {
        val recording = makeRecording(processingStatus = RecordingStatus.COMPLETED)
        assertEquals(RecordingStatus.COMPLETED, recording.processingStatus)
    }

    @Test
    fun `recording can be created with FAILED status`() {
        val recording = makeRecording(processingStatus = RecordingStatus.FAILED)
        assertEquals(RecordingStatus.FAILED, recording.processingStatus)
    }

    @Test
    fun `recording status copy simulates PENDING to COMPLETED transition`() {
        val pending = makeRecording(id = 1, processingStatus = RecordingStatus.PENDING)
        val processing = pending.copy(processingStatus = RecordingStatus.PROCESSING)
        val completed = processing.copy(processingStatus = RecordingStatus.COMPLETED)

        assertEquals(RecordingStatus.PENDING, pending.processingStatus)
        assertEquals(RecordingStatus.PROCESSING, processing.processingStatus)
        assertEquals(RecordingStatus.COMPLETED, completed.processingStatus)
        // ID should remain stable through transitions
        assertEquals(pending.id, completed.id)
    }

    @Test
    fun `recording status copy simulates PENDING to FAILED transition`() {
        val pending = makeRecording(id = 1, processingStatus = RecordingStatus.PENDING)
        val processing = pending.copy(processingStatus = RecordingStatus.PROCESSING)
        val failed = processing.copy(processingStatus = RecordingStatus.FAILED)

        assertEquals(RecordingStatus.FAILED, failed.processingStatus)
        assertEquals(pending.id, failed.id)
    }

    // ── Room entity annotations via reflection ──

    @Test
    fun `DiaryEntry has Entity annotation with correct table name`() {
        val entity = DiaryEntry::class.java.getAnnotation(Entity::class.java)
        assertNotNull("DiaryEntry must have @Entity annotation", entity)
        assertEquals("diary_entries", entity!!.tableName)
    }

    @Test
    fun `DiaryEntry id has PrimaryKey annotation with autoGenerate`() {
        val idField = DiaryEntry::class.java.getDeclaredField("id")
        val pk = idField.getAnnotation(PrimaryKey::class.java)
        assertNotNull("DiaryEntry.id must have @PrimaryKey annotation", pk)
        assertTrue("DiaryEntry.id must have autoGenerate = true", pk!!.autoGenerate)
    }

    @Test
    fun `Recording has Entity annotation with correct table name`() {
        val entity = Recording::class.java.getAnnotation(Entity::class.java)
        assertNotNull("Recording must have @Entity annotation", entity)
        assertEquals("recordings", entity!!.tableName)
    }

    @Test
    fun `Recording id has PrimaryKey annotation with autoGenerate`() {
        val idField = Recording::class.java.getDeclaredField("id")
        val pk = idField.getAnnotation(PrimaryKey::class.java)
        assertNotNull("Recording.id must have @PrimaryKey annotation", pk)
        assertTrue("Recording.id must have autoGenerate = true", pk!!.autoGenerate)
    }

    // ── DiaryEntry displayText with recording context ──

    @Test
    fun `entry linked to recording still follows displayText priority`() {
        val entry = makeEntry(
            sourceRecordingId = 10L
        ).copy(
            cleanText = "Clean from recording",
            correctedText = "Corrected"
        )
        assertEquals("Clean from recording", entry.displayText)
    }
}
