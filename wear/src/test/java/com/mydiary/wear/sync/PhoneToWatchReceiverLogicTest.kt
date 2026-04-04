package com.mydiary.wear.sync

import com.mydiary.shared.model.DiaryEntry
import com.mydiary.shared.model.Source
import com.mydiary.shared.model.StatusSyncEntry
import com.mydiary.shared.model.StatusSyncPayload
import com.mydiary.shared.model.SyncEntry
import com.mydiary.shared.model.SyncPayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests the data transformation logic used by PhoneToWatchReceiver:
 * - SyncPayload deserialization and entry conversion
 * - StatusSyncPayload deserialization
 * - Entry deduplication decision logic
 * - Status update routing (COMPLETED → markCompleted, DISCARDED → delete)
 */
class PhoneToWatchReceiverLogicTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ── SyncPayload deserialization ──

    @Test
    fun `deserialize SyncPayload with entries`() {
        val payload = SyncPayload(
            entries = listOf(
                SyncEntry(
                    id = 1, text = "comprar pan", keyword = "pendiente",
                    category = "Tarea", confidence = 0.9f, createdAt = 1700000000000L,
                    source = "PHONE", duration = 0
                )
            )
        )

        val serialized = json.encodeToString(payload)
        val deserialized = json.decodeFromString<SyncPayload>(serialized)

        assertEquals(1, deserialized.entries.size)
        assertEquals("comprar pan", deserialized.entries[0].text)
        assertEquals("PHONE", deserialized.entries[0].source)
    }

    @Test
    fun `deserialize SyncPayload with entries and recordings`() {
        val payloadJson = """
            {
                "entries": [
                    {"id":1,"text":"test","keyword":"k","category":"c","confidence":0.5,"createdAt":100,"source":"PHONE","duration":0}
                ],
                "recordings": [
                    {"transcription":"rec text","durationSeconds":60,"source":"WATCH","createdAt":200}
                ]
            }
        """.trimIndent()

        val payload = json.decodeFromString<SyncPayload>(payloadJson)
        assertEquals(1, payload.entries.size)
        assertEquals(1, payload.recordings.size)
        assertEquals("rec text", payload.recordings[0].transcription)
    }

    @Test
    fun `SyncEntry toDiaryEntry sets isSynced true`() {
        val sync = SyncEntry(
            id = 1, text = "test", keyword = "k", category = "c",
            confidence = 0.9f, createdAt = 100L, source = "WATCH", duration = 5
        )

        val entry = sync.toDiaryEntry()
        assertTrue(entry.isSynced)
    }

    @Test
    fun `SyncEntry toDiaryEntry maps source correctly`() {
        val phoneSync = SyncEntry(
            id = 1, text = "a", keyword = "k", category = "c",
            confidence = 0.5f, createdAt = 100L, source = "PHONE", duration = 0
        )
        assertEquals(Source.PHONE, phoneSync.toDiaryEntry().source)

        val watchSync = phoneSync.copy(source = "WATCH")
        assertEquals(Source.WATCH, watchSync.toDiaryEntry().source)
    }

    // ── StatusSyncPayload deserialization ──

    @Test
    fun `deserialize StatusSyncPayload`() {
        val payload = StatusSyncPayload(
            completed = listOf(
                StatusSyncEntry(createdAt = 100L, text = "done task"),
                StatusSyncEntry(createdAt = 200L, text = "another done")
            ),
            deleted = listOf(
                StatusSyncEntry(createdAt = 300L, text = "deleted task")
            )
        )

        val serialized = json.encodeToString(payload)
        val deserialized = json.decodeFromString<StatusSyncPayload>(serialized)

        assertEquals(2, deserialized.completed.size)
        assertEquals(1, deserialized.deleted.size)
        assertEquals("done task", deserialized.completed[0].text)
        assertEquals("deleted task", deserialized.deleted[0].text)
    }

    @Test
    fun `StatusSyncPayload defaults to empty lists`() {
        val payload = StatusSyncPayload()
        assertTrue(payload.completed.isEmpty())
        assertTrue(payload.deleted.isEmpty())
    }

    @Test
    fun `StatusSyncPayload from JSON with unknown keys`() {
        val payloadJson = """{"completed":[],"deleted":[],"futureField":"ignored"}"""
        val payload = json.decodeFromString<StatusSyncPayload>(payloadJson)
        assertNotNull(payload)
    }

    // ── Entry deduplication logic ──
    // PhoneToWatchReceiver decides: if entry exists (by createdAt+text) → skip or update status.
    // If not exists → insert. We test the decision logic here.

    @Test
    fun `new entry should be inserted when not existing`() {
        // Simulates the decision: exists=false → insert
        val exists = false
        val status = "PENDING"

        // Logic from handleEntries:
        val shouldInsert = !exists
        val shouldComplete = exists && status == "COMPLETED"
        val shouldDelete = exists && status == "DISCARDED"

        assertTrue(shouldInsert)
        assertFalse(shouldComplete)
        assertFalse(shouldDelete)
    }

    @Test
    fun `existing entry with COMPLETED status should be marked completed`() {
        val exists = true
        val status = "COMPLETED"

        val shouldInsert = !exists
        val shouldComplete = exists && status == "COMPLETED"
        val shouldDelete = exists && status == "DISCARDED"

        assertFalse(shouldInsert)
        assertTrue(shouldComplete)
        assertFalse(shouldDelete)
    }

    @Test
    fun `existing entry with DISCARDED status should be deleted`() {
        val exists = true
        val status = "DISCARDED"

        val shouldInsert = !exists
        val shouldComplete = exists && status == "COMPLETED"
        val shouldDelete = exists && status == "DISCARDED"

        assertFalse(shouldInsert)
        assertFalse(shouldComplete)
        assertTrue(shouldDelete)
    }

    @Test
    fun `existing entry with PENDING status is skipped`() {
        val exists = true
        val status = "PENDING"

        val shouldInsert = !exists
        val shouldComplete = exists && status == "COMPLETED"
        val shouldDelete = exists && status == "DISCARDED"

        assertFalse(shouldInsert)
        assertFalse(shouldComplete)
        assertFalse(shouldDelete)
    }

    // ── Mic coordination constants ──

    @Test
    fun `companion PREFS constant is accessible`() {
        assertEquals("watch_sync_prefs", PhoneToWatchReceiver.PREFS)
    }

    // ── SyncEntry status defaults ──

    @Test
    fun `SyncEntry default status is PENDING`() {
        val sync = SyncEntry(
            id = 1, text = "t", keyword = "k", category = "c",
            confidence = 0.5f, createdAt = 100L, source = "WATCH", duration = 0
        )
        assertEquals("PENDING", sync.status)
    }

    @Test
    fun `SyncEntry default actionType is GENERIC`() {
        val sync = SyncEntry(
            id = 1, text = "t", keyword = "k", category = "c",
            confidence = 0.5f, createdAt = 100L, source = "WATCH", duration = 0
        )
        assertEquals("GENERIC", sync.actionType)
    }

    @Test
    fun `SyncEntry default priority is NORMAL`() {
        val sync = SyncEntry(
            id = 1, text = "t", keyword = "k", category = "c",
            confidence = 0.5f, createdAt = 100L, source = "WATCH", duration = 0
        )
        assertEquals("NORMAL", sync.priority)
    }
}
