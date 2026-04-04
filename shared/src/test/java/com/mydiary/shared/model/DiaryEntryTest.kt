package com.mydiary.shared.model

import org.junit.Assert.*
import org.junit.Test

class DiaryEntryTest {

    private fun makeEntry(
        text: String = "tengo que llamar al dentista",
        keyword: String = "pendiente",
        category: String = "Pendientes",
        confidence: Float = 0.95f,
        source: Source = Source.PHONE,
        duration: Int = 5,
        correctedText: String? = null,
        cleanText: String? = null
    ) = DiaryEntry(
        text = text,
        keyword = keyword,
        category = category,
        confidence = confidence,
        source = source,
        duration = duration,
        correctedText = correctedText,
        cleanText = cleanText
    )

    // ── displayText ──────────────────────────────────────────────

    @Test
    fun `displayText returns text when no corrections exist`() {
        val entry = makeEntry(text = "original text")
        assertEquals("original text", entry.displayText)
    }

    @Test
    fun `displayText prefers correctedText over text`() {
        val entry = makeEntry(
            text = "orginal txt",
            correctedText = "original text"
        )
        assertEquals("original text", entry.displayText)
    }

    @Test
    fun `displayText prefers cleanText over correctedText and text`() {
        val entry = makeEntry(
            text = "raw",
            correctedText = "corrected",
            cleanText = "clean"
        )
        assertEquals("clean", entry.displayText)
    }

    @Test
    fun `displayText uses correctedText when cleanText is null`() {
        val entry = makeEntry(
            text = "raw",
            correctedText = "corrected",
            cleanText = null
        )
        assertEquals("corrected", entry.displayText)
    }

    // ── Default values ───────────────────────────────────────────

    @Test
    fun `default id is zero`() {
        val entry = makeEntry()
        assertEquals(0L, entry.id)
    }

    @Test
    fun `default isSynced is false`() {
        val entry = makeEntry()
        assertFalse(entry.isSynced)
    }

    @Test
    fun `default status is PENDING`() {
        val entry = makeEntry()
        assertEquals(EntryStatus.PENDING, entry.status)
    }

    @Test
    fun `default actionType is GENERIC`() {
        val entry = makeEntry()
        assertEquals(EntryActionType.GENERIC, entry.actionType)
    }

    @Test
    fun `default priority is NORMAL`() {
        val entry = makeEntry()
        assertEquals(EntryPriority.NORMAL, entry.priority)
    }

    @Test
    fun `default wasReviewedByLLM is false`() {
        val entry = makeEntry()
        assertFalse(entry.wasReviewedByLLM)
    }

    @Test
    fun `default isManual is false`() {
        val entry = makeEntry()
        assertFalse(entry.isManual)
    }

    @Test
    fun `nullable fields default to null`() {
        val entry = makeEntry()
        assertNull(entry.correctedText)
        assertNull(entry.llmConfidence)
        assertNull(entry.cleanText)
        assertNull(entry.dueDate)
        assertNull(entry.completedAt)
        assertNull(entry.duplicateOfId)
        assertNull(entry.sourceRecordingId)
    }
}

class EntryStatusTest {

    @Test
    fun `status constants have correct values`() {
        assertEquals("PENDING", EntryStatus.PENDING)
        assertEquals("COMPLETED", EntryStatus.COMPLETED)
        assertEquals("DISCARDED", EntryStatus.DISCARDED)
    }
}

class EntryActionTypeTest {

    @Test
    fun `action type constants have correct values`() {
        assertEquals("CALL", EntryActionType.CALL)
        assertEquals("BUY", EntryActionType.BUY)
        assertEquals("SEND", EntryActionType.SEND)
        assertEquals("EVENT", EntryActionType.EVENT)
        assertEquals("REVIEW", EntryActionType.REVIEW)
        assertEquals("TALK_TO", EntryActionType.TALK_TO)
        assertEquals("GENERIC", EntryActionType.GENERIC)
    }

    @Test
    fun `label returns Spanish labels for known types`() {
        assertEquals("Llamar", EntryActionType.label(EntryActionType.CALL))
        assertEquals("Comprar", EntryActionType.label(EntryActionType.BUY))
        assertEquals("Enviar", EntryActionType.label(EntryActionType.SEND))
        assertEquals("Evento", EntryActionType.label(EntryActionType.EVENT))
        assertEquals("Revisar", EntryActionType.label(EntryActionType.REVIEW))
        assertEquals("Hablar con", EntryActionType.label(EntryActionType.TALK_TO))
    }

    @Test
    fun `label returns Tarea for unknown type`() {
        assertEquals("Tarea", EntryActionType.label("UNKNOWN"))
        assertEquals("Tarea", EntryActionType.label(EntryActionType.GENERIC))
    }

    @Test
    fun `emoji returns fallback for unknown type`() {
        val fallback = EntryActionType.emoji("UNKNOWN")
        assertEquals("\u2610", fallback)
    }
}

class EntryPriorityTest {

    @Test
    fun `priority constants have correct values`() {
        assertEquals("LOW", EntryPriority.LOW)
        assertEquals("NORMAL", EntryPriority.NORMAL)
        assertEquals("HIGH", EntryPriority.HIGH)
        assertEquals("URGENT", EntryPriority.URGENT)
    }
}
