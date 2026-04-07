package com.trama.shared.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SyncPayloadTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `empty SyncPayload has empty lists`() {
        val payload = SyncPayload()
        assertTrue(payload.entries.isEmpty())
        assertTrue(payload.recordings.isEmpty())
    }

    @Test
    fun `SyncPayload serialization round-trip`() {
        val payload = SyncPayload(
            entries = listOf(
                SyncEntry(
                    id = 1, text = "llamar", keyword = "pendiente",
                    category = "Pendientes", confidence = 0.9f,
                    createdAt = 1000L, source = "PHONE", duration = 5
                )
            ),
            recordings = listOf(
                SyncRecording(
                    transcription = "test recording",
                    durationSeconds = 60,
                    source = "WATCH",
                    createdAt = 2000L
                )
            )
        )

        val encoded = json.encodeToString(SyncPayload.serializer(), payload)
        val decoded = json.decodeFromString(SyncPayload.serializer(), encoded)

        assertEquals(payload, decoded)
    }

    @Test
    fun `SyncEntry default status is PENDING`() {
        val entry = SyncEntry(
            id = 1, text = "t", keyword = "k", category = "c",
            confidence = 1f, createdAt = 0, source = "PHONE", duration = 1
        )
        assertEquals("PENDING", entry.status)
        assertEquals("GENERIC", entry.actionType)
        assertEquals("NORMAL", entry.priority)
    }

    @Test
    fun `SyncEntry toDiaryEntry conversion`() {
        val sync = SyncEntry(
            id = 5, text = "comprar leche", keyword = "compra",
            category = "Compras", confidence = 0.8f,
            createdAt = 5000L, source = "WATCH", duration = 3,
            status = "COMPLETED", actionType = "BUY",
            cleanText = "Comprar leche", priority = "HIGH"
        )
        val diary = sync.toDiaryEntry()

        assertEquals("comprar leche", diary.text)
        assertEquals(Source.WATCH, diary.source)
        assertTrue(diary.isSynced)
        assertEquals("COMPLETED", diary.status)
        assertEquals("BUY", diary.actionType)
        assertEquals("Comprar leche", diary.cleanText)
        assertEquals("HIGH", diary.priority)
    }

    @Test
    fun `SyncEntry fromDiaryEntry conversion`() {
        val diary = DiaryEntry(
            id = 10, text = "test", keyword = "k", category = "c",
            confidence = 0.7f, createdAt = 100L, source = Source.PHONE,
            duration = 2, status = "DISCARDED", actionType = "CALL",
            cleanText = "Clean", priority = "URGENT"
        )
        val sync = SyncEntry.fromDiaryEntry(diary)

        assertEquals(10L, sync.id)
        assertEquals("test", sync.text)
        assertEquals("PHONE", sync.source)
        assertEquals("DISCARDED", sync.status)
        assertEquals("CALL", sync.actionType)
        assertEquals("Clean", sync.cleanText)
        assertEquals("URGENT", sync.priority)
    }

    @Test
    fun `SyncRecording round-trip conversion`() {
        val recording = Recording(
            transcription = "meeting notes",
            durationSeconds = 300,
            source = Source.PHONE,
            createdAt = 9000L
        )
        val sync = SyncRecording.fromRecording(recording)
        val restored = sync.toRecording()

        assertEquals(recording.transcription, restored.transcription)
        assertEquals(recording.durationSeconds, restored.durationSeconds)
        assertEquals(recording.source, restored.source)
        assertEquals(recording.createdAt, restored.createdAt)
        assertTrue(restored.isSynced)
    }
}

class StatusSyncPayloadTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `StatusSyncPayload serialization round-trip`() {
        val payload = StatusSyncPayload(
            completed = listOf(StatusSyncEntry(1000L, "entry 1")),
            deleted = listOf(StatusSyncEntry(2000L, "entry 2"))
        )
        val encoded = json.encodeToString(StatusSyncPayload.serializer(), payload)
        val decoded = json.decodeFromString(StatusSyncPayload.serializer(), encoded)

        assertEquals(payload, decoded)
        assertEquals(1, decoded.completed.size)
        assertEquals(1, decoded.deleted.size)
    }

    @Test
    fun `empty StatusSyncPayload defaults`() {
        val payload = StatusSyncPayload()
        assertTrue(payload.completed.isEmpty())
        assertTrue(payload.deleted.isEmpty())
    }
}
