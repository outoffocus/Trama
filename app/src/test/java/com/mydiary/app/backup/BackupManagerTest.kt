package com.mydiary.app.backup

import com.mydiary.app.backup.BackupManager.Backup
import com.mydiary.app.backup.BackupManager.BackupEntry
import com.mydiary.app.backup.BackupManager.BackupRecording
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class BackupManagerTest {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    // ── BackupEntry serialization ──

    @Test
    fun `BackupEntry serializes and deserializes with all fields`() {
        val entry = BackupEntry(
            text = "Buy milk",
            keyword = "comprar",
            category = "shopping",
            confidence = 0.95f,
            source = "PHONE",
            duration = 5,
            createdAt = 1700000000000L,
            correctedText = "Comprar leche",
            wasReviewedByLLM = true,
            llmConfidence = 0.88f,
            status = "PENDING",
            actionType = "BUY",
            cleanText = "Comprar leche",
            dueDate = 1700100000000L,
            completedAt = null,
            priority = "HIGH",
            isManual = false
        )

        val jsonStr = json.encodeToString(entry)
        val decoded = json.decodeFromString<BackupEntry>(jsonStr)

        assertEquals(entry, decoded)
    }

    @Test
    fun `BackupEntry uses correct defaults for optional fields`() {
        val entry = BackupEntry(
            text = "Test",
            keyword = "test",
            category = "general",
            confidence = 0.5f,
            source = "WATCH",
            duration = 3,
            createdAt = 1700000000000L
        )

        assertNull(entry.correctedText)
        assertFalse(entry.wasReviewedByLLM)
        assertNull(entry.llmConfidence)
        assertNull(entry.status)
        assertNull(entry.actionType)
        assertNull(entry.cleanText)
        assertNull(entry.dueDate)
        assertNull(entry.completedAt)
        assertNull(entry.priority)
        assertFalse(entry.isManual)
    }

    @Test
    fun `BackupEntry deserializes with unknown fields ignored`() {
        val jsonStr = """
            {
                "text": "Hello",
                "keyword": "kw",
                "category": "cat",
                "confidence": 0.9,
                "source": "PHONE",
                "duration": 2,
                "createdAt": 1700000000000,
                "unknownField": "should be ignored"
            }
        """.trimIndent()

        val decoded = json.decodeFromString<BackupEntry>(jsonStr)
        assertEquals("Hello", decoded.text)
        assertEquals("kw", decoded.keyword)
    }

    // ── BackupRecording serialization ──

    @Test
    fun `BackupRecording serializes and deserializes`() {
        val recording = BackupRecording(
            title = "Meeting notes",
            transcription = "We discussed the roadmap...",
            summary = "Roadmap discussion",
            keyPoints = """["roadmap","timeline"]""",
            durationSeconds = 300,
            source = "PHONE",
            createdAt = 1700000000000L,
            processingStatus = "COMPLETED",
            processedLocally = true
        )

        val jsonStr = json.encodeToString(recording)
        val decoded = json.decodeFromString<BackupRecording>(jsonStr)

        assertEquals(recording, decoded)
    }

    @Test
    fun `BackupRecording uses correct defaults`() {
        val recording = BackupRecording(
            transcription = "some text",
            durationSeconds = 60,
            source = "WATCH",
            createdAt = 1700000000000L
        )

        assertNull(recording.title)
        assertNull(recording.summary)
        assertNull(recording.keyPoints)
        assertEquals("PENDING", recording.processingStatus)
        assertFalse(recording.processedLocally)
    }

    // ── Backup (top-level) serialization ──

    @Test
    fun `Backup serializes with entries and recordings`() {
        val backup = Backup(
            version = 2,
            exportedAt = 1700000000000L,
            entries = listOf(
                BackupEntry(
                    text = "entry1", keyword = "kw", category = "cat",
                    confidence = 0.9f, source = "PHONE", duration = 5,
                    createdAt = 1700000000000L
                )
            ),
            recordings = listOf(
                BackupRecording(
                    transcription = "recording text", durationSeconds = 120,
                    source = "PHONE", createdAt = 1700000000000L
                )
            )
        )

        val jsonStr = json.encodeToString(backup)
        val decoded = json.decodeFromString<Backup>(jsonStr)

        assertEquals(2, decoded.version)
        assertEquals(1700000000000L, decoded.exportedAt)
        assertEquals(1, decoded.entries.size)
        assertEquals(1, decoded.recordings.size)
        assertEquals("entry1", decoded.entries[0].text)
        assertEquals("recording text", decoded.recordings[0].transcription)
    }

    @Test
    fun `Backup defaults recordings to empty list`() {
        val backup = Backup(
            entries = listOf(
                BackupEntry(
                    text = "e", keyword = "k", category = "c",
                    confidence = 0.5f, source = "PHONE", duration = 1,
                    createdAt = 1700000000000L
                )
            )
        )

        assertTrue(backup.recordings.isEmpty())
    }

    @Test
    fun `Backup version defaults to 2`() {
        val backup = Backup(entries = emptyList())
        assertEquals(2, backup.version)
    }

    @Test
    fun `Backup round-trip preserves empty entries and recordings`() {
        val backup = Backup(entries = emptyList(), recordings = emptyList())
        val jsonStr = json.encodeToString(backup)
        val decoded = json.decodeFromString<Backup>(jsonStr)

        assertTrue(decoded.entries.isEmpty())
        assertTrue(decoded.recordings.isEmpty())
    }

    // ── getBackupFileName ──

    @Test
    fun `getBackupFileName returns string matching expected pattern`() {
        val fileName = BackupManager.getBackupFileName()
        assertTrue(fileName.startsWith("mydiary-backup-"))
        assertTrue(fileName.endsWith(".json"))
        // Pattern: mydiary-backup-YYYY-MM-DD.json
        assertTrue(fileName.matches(Regex("mydiary-backup-\\d{4}-\\d{2}-\\d{2}\\.json")))
    }
}
