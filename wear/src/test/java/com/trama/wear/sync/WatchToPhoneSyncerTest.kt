package com.trama.wear.sync

import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.Recording
import com.trama.shared.model.Source
import com.trama.shared.model.SyncEntry
import com.trama.shared.model.SyncPayload
import com.trama.shared.model.SyncRecording
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests the payload transformation logic used by WatchToPhoneSyncer.
 * The actual sync (Wearable DataClient) requires Android; here we test
 * the data mapping: DiaryEntry → SyncEntry and Recording → SyncRecording.
 */
class WatchToPhoneSyncerTest {

    // ── SyncEntry.fromDiaryEntry ──

    @Test
    fun `SyncEntry fromDiaryEntry maps all fields`() {
        val entry = DiaryEntry(
            id = 42,
            text = "comprar leche",
            keyword = "pendiente",
            category = "Tarea",
            confidence = 0.95f,
            createdAt = 1700000000000L,
            source = Source.WATCH,
            duration = 5,
            status = "PENDING",
            actionType = "BUY",
            cleanText = "Comprar leche",
            dueDate = 1700100000000L,
            priority = "HIGH"
        )

        val sync = SyncEntry.fromDiaryEntry(entry)

        assertEquals(42L, sync.id)
        assertEquals("comprar leche", sync.text)
        assertEquals("pendiente", sync.keyword)
        assertEquals("Tarea", sync.category)
        assertEquals(0.95f, sync.confidence, 0.001f)
        assertEquals(1700000000000L, sync.createdAt)
        assertEquals("WATCH", sync.source)
        assertEquals(5, sync.duration)
        assertEquals("PENDING", sync.status)
        assertEquals("BUY", sync.actionType)
        assertEquals("Comprar leche", sync.cleanText)
        assertEquals(1700100000000L, sync.dueDate)
        assertEquals("HIGH", sync.priority)
    }

    @Test
    fun `SyncEntry fromDiaryEntry handles null optional fields`() {
        val entry = DiaryEntry(
            text = "nota simple",
            keyword = "nota",
            category = "Nota",
            confidence = 0.8f,
            source = Source.PHONE,
            duration = 0
        )

        val sync = SyncEntry.fromDiaryEntry(entry)

        assertNull(sync.cleanText)
        assertNull(sync.dueDate)
        assertEquals("PENDING", sync.status)
        assertEquals("GENERIC", sync.actionType)
        assertEquals("NORMAL", sync.priority)
    }

    @Test
    fun `SyncEntry toDiaryEntry roundtrip preserves data`() {
        val original = DiaryEntry(
            id = 10,
            text = "llamar al médico",
            keyword = "recordar",
            category = "Recordar",
            confidence = 0.9f,
            createdAt = 1700000000000L,
            source = Source.WATCH,
            duration = 3,
            status = "COMPLETED",
            actionType = "CALL",
            cleanText = "Llamar al médico",
            dueDate = 1700200000000L,
            priority = "URGENT"
        )

        val sync = SyncEntry.fromDiaryEntry(original)
        val restored = sync.toDiaryEntry()

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
        assertTrue(restored.isSynced) // toDiaryEntry always sets isSynced=true
    }

    // ── SyncRecording.fromRecording ──

    @Test
    fun `SyncRecording fromRecording maps all fields`() {
        val recording = Recording(
            id = 7,
            transcription = "reunión de equipo",
            durationSeconds = 120,
            source = Source.WATCH,
            createdAt = 1700000000000L
        )

        val sync = SyncRecording.fromRecording(recording)

        assertEquals("reunión de equipo", sync.transcription)
        assertEquals(120, sync.durationSeconds)
        assertEquals("WATCH", sync.source)
        assertEquals(1700000000000L, sync.createdAt)
    }

    @Test
    fun `SyncRecording toRecording roundtrip preserves data`() {
        val original = Recording(
            transcription = "nota de voz",
            durationSeconds = 30,
            source = Source.PHONE,
            createdAt = 1700000000000L
        )

        val sync = SyncRecording.fromRecording(original)
        val restored = sync.toRecording()

        assertEquals(original.transcription, restored.transcription)
        assertEquals(original.durationSeconds, restored.durationSeconds)
        assertEquals(original.source, restored.source)
        assertEquals(original.createdAt, restored.createdAt)
        assertTrue(restored.isSynced) // toRecording always sets isSynced=true
    }

    // ── SyncPayload construction ──

    @Test
    fun `SyncPayload with entries and recordings`() {
        val entries = listOf(
            SyncEntry(
                id = 1, text = "a", keyword = "k", category = "c",
                confidence = 0.9f, createdAt = 100L, source = "WATCH", duration = 0
            ),
            SyncEntry(
                id = 2, text = "b", keyword = "k", category = "c",
                confidence = 0.8f, createdAt = 200L, source = "WATCH", duration = 0
            )
        )
        val recordings = listOf(
            SyncRecording(
                transcription = "rec", durationSeconds = 60,
                source = "WATCH", createdAt = 300L
            )
        )

        val payload = SyncPayload(entries = entries, recordings = recordings)

        assertEquals(2, payload.entries.size)
        assertEquals(1, payload.recordings.size)
    }

    @Test
    fun `SyncPayload defaults to empty lists`() {
        val payload = SyncPayload()
        assertTrue(payload.entries.isEmpty())
        assertTrue(payload.recordings.isEmpty())
    }

    @Test
    fun `source string roundtrip PHONE`() {
        val entry = DiaryEntry(
            text = "test", keyword = "k", category = "c",
            confidence = 0.5f, source = Source.PHONE, duration = 0
        )
        val sync = SyncEntry.fromDiaryEntry(entry)
        assertEquals("PHONE", sync.source)
        assertEquals(Source.PHONE, sync.toDiaryEntry().source)
    }

    @Test
    fun `source string roundtrip WATCH`() {
        val entry = DiaryEntry(
            text = "test", keyword = "k", category = "c",
            confidence = 0.5f, source = Source.WATCH, duration = 0
        )
        val sync = SyncEntry.fromDiaryEntry(entry)
        assertEquals("WATCH", sync.source)
        assertEquals(Source.WATCH, sync.toDiaryEntry().source)
    }
}
