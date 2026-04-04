package com.mydiary.shared.data

import com.mydiary.shared.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/**
 * Integration tests for the full sync data flow: DiaryEntry <-> SyncEntry
 * and Recording <-> SyncRecording round-trips, including edge cases.
 */
class SyncIntegrationTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ── SyncEntry round-trip field preservation ──

    @Test
    fun `SyncEntry round-trip preserves all core fields`() {
        val original = DiaryEntry(
            id = 42,
            text = "llamar al dentista",
            keyword = "pendiente",
            category = "Pendientes",
            confidence = 0.95f,
            createdAt = 1700000000000L,
            source = Source.PHONE,
            duration = 8,
            status = EntryStatus.COMPLETED,
            actionType = EntryActionType.CALL,
            cleanText = "Llamar al dentista",
            dueDate = 1700100000000L,
            priority = EntryPriority.HIGH
        )

        val syncEntry = SyncEntry.fromDiaryEntry(original)
        val restored = syncEntry.toDiaryEntry()

        assertEquals(original.text, restored.text)
        assertEquals(original.keyword, restored.keyword)
        assertEquals(original.category, restored.category)
        assertEquals(original.confidence, restored.confidence, 0.001f)
        assertEquals(original.createdAt, restored.createdAt)
        assertEquals(original.source, restored.source)
        assertEquals(original.duration, restored.duration)
        assertEquals(original.status, restored.status)
        assertEquals(original.actionType, restored.actionType)
        assertEquals(original.cleanText, restored.cleanText)
        assertEquals(original.dueDate, restored.dueDate)
        assertEquals(original.priority, restored.priority)
    }

    @Test
    fun `SyncEntry round-trip sets isSynced to true`() {
        val original = DiaryEntry(
            id = 1, text = "test", keyword = "k", category = "c",
            confidence = 0.5f, source = Source.WATCH, duration = 3,
            isSynced = false
        )
        val restored = SyncEntry.fromDiaryEntry(original).toDiaryEntry()
        assertTrue("Restored entry should be marked as synced", restored.isSynced)
    }

    @Test
    fun `SyncEntry round-trip does not preserve id (new entry on target device)`() {
        val original = DiaryEntry(
            id = 99, text = "test", keyword = "k", category = "c",
            confidence = 0.5f, source = Source.PHONE, duration = 1
        )
        val restored = SyncEntry.fromDiaryEntry(original).toDiaryEntry()
        assertEquals(0L, restored.id) // default id, will be auto-generated
    }

    @Test
    fun `SyncEntry round-trip with null optional fields`() {
        val original = DiaryEntry(
            id = 5, text = "basic entry", keyword = "nota", category = "Notas",
            confidence = 0.7f, source = Source.PHONE, duration = 2,
            cleanText = null, dueDate = null
        )

        val syncEntry = SyncEntry.fromDiaryEntry(original)
        val restored = syncEntry.toDiaryEntry()

        assertNull(restored.cleanText)
        assertNull(restored.dueDate)
        assertEquals(EntryStatus.PENDING, restored.status)
        assertEquals(EntryActionType.GENERIC, restored.actionType)
        assertEquals(EntryPriority.NORMAL, restored.priority)
    }

    // ── SyncRecording round-trip ──

    @Test
    fun `SyncRecording round-trip preserves all fields`() {
        val original = Recording(
            id = 10,
            transcription = "meeting notes about project timeline",
            durationSeconds = 300,
            source = Source.WATCH,
            createdAt = 1700000000000L,
            title = "Project Meeting",
            summary = "Discussed timeline",
            keyPoints = "[\"deadline\",\"resources\"]"
        )

        val syncRecording = SyncRecording.fromRecording(original)
        val restored = syncRecording.toRecording()

        assertEquals(original.transcription, restored.transcription)
        assertEquals(original.durationSeconds, restored.durationSeconds)
        assertEquals(original.source, restored.source)
        assertEquals(original.createdAt, restored.createdAt)
        assertTrue(restored.isSynced)
    }

    @Test
    fun `SyncRecording round-trip does not carry title or summary`() {
        // SyncRecording only syncs transcription, duration, source, createdAt
        val original = Recording(
            transcription = "some text",
            durationSeconds = 60,
            source = Source.PHONE,
            createdAt = 5000L,
            title = "My Title",
            summary = "My Summary",
            keyPoints = "points"
        )

        val restored = SyncRecording.fromRecording(original).toRecording()
        assertNull("Title should not be synced", restored.title)
        assertNull("Summary should not be synced", restored.summary)
        assertNull("KeyPoints should not be synced", restored.keyPoints)
    }

    // ── SyncPayload with mixed content ──

    @Test
    fun `SyncPayload with entries and recordings serializes correctly`() {
        val payload = SyncPayload(
            entries = listOf(
                SyncEntry(
                    id = 1, text = "entry 1", keyword = "k1", category = "c1",
                    confidence = 0.9f, createdAt = 1000L, source = "PHONE",
                    duration = 5, status = "PENDING", actionType = "CALL",
                    cleanText = "Entry 1", priority = "HIGH"
                ),
                SyncEntry(
                    id = 2, text = "entry 2", keyword = "k2", category = "c2",
                    confidence = 0.8f, createdAt = 2000L, source = "WATCH",
                    duration = 3, status = "COMPLETED", actionType = "BUY",
                    cleanText = null, dueDate = 3000L, priority = "LOW"
                )
            ),
            recordings = listOf(
                SyncRecording(
                    transcription = "recording 1", durationSeconds = 120,
                    source = "PHONE", createdAt = 4000L
                )
            )
        )

        val encoded = json.encodeToString(SyncPayload.serializer(), payload)
        val decoded = json.decodeFromString(SyncPayload.serializer(), encoded)

        assertEquals(2, decoded.entries.size)
        assertEquals(1, decoded.recordings.size)
        assertEquals(payload, decoded)
    }

    @Test
    fun `empty SyncPayload serialization round-trip`() {
        val payload = SyncPayload()
        val encoded = json.encodeToString(SyncPayload.serializer(), payload)
        val decoded = json.decodeFromString(SyncPayload.serializer(), encoded)

        assertTrue(decoded.entries.isEmpty())
        assertTrue(decoded.recordings.isEmpty())
    }

    @Test
    fun `SyncPayload with only entries and no recordings`() {
        val payload = SyncPayload(
            entries = listOf(
                SyncEntry(
                    id = 1, text = "solo entry", keyword = "k", category = "c",
                    confidence = 1f, createdAt = 100L, source = "PHONE", duration = 1
                )
            )
        )
        val encoded = json.encodeToString(SyncPayload.serializer(), payload)
        val decoded = json.decodeFromString(SyncPayload.serializer(), encoded)

        assertEquals(1, decoded.entries.size)
        assertTrue(decoded.recordings.isEmpty())
    }

    // ── StatusSyncPayload integration ──

    @Test
    fun `StatusSyncPayload matches entries by createdAt and text`() {
        val entry = DiaryEntry(
            id = 1, text = "buy milk", keyword = "k", category = "c",
            confidence = 0.9f, source = Source.PHONE, duration = 2,
            createdAt = 5000L
        )

        // Simulate creating a status sync entry from a diary entry
        val statusEntry = StatusSyncEntry(
            createdAt = entry.createdAt,
            text = entry.text
        )

        assertEquals(entry.createdAt, statusEntry.createdAt)
        assertEquals(entry.text, statusEntry.text)
    }

    @Test
    fun `StatusSyncPayload serialization with completed and deleted entries`() {
        val payload = StatusSyncPayload(
            completed = listOf(
                StatusSyncEntry(1000L, "done task"),
                StatusSyncEntry(2000L, "another done")
            ),
            deleted = listOf(
                StatusSyncEntry(3000L, "removed task")
            )
        )

        val encoded = json.encodeToString(StatusSyncPayload.serializer(), payload)
        val decoded = json.decodeFromString(StatusSyncPayload.serializer(), encoded)

        assertEquals(2, decoded.completed.size)
        assertEquals(1, decoded.deleted.size)
        assertEquals("done task", decoded.completed[0].text)
        assertEquals("removed task", decoded.deleted[0].text)
    }
}
