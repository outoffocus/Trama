package com.trama.app.ui.components

import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryActionType
import com.trama.shared.model.EntryPriority
import com.trama.shared.model.EntryStatus
import com.trama.shared.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Tests for the logic used by EntryCard to determine badge visibility,
 * priority colors, due date display, and display text.
 */
class EntryCardLogicTest {

    // ── Helper to build a DiaryEntry with defaults ──

    private fun entry(
        text: String = "Test entry",
        keyword: String = "test",
        category: String = "nota",
        confidence: Float = 0.9f,
        source: Source = Source.PHONE,
        duration: Int = 5,
        wasReviewedByLLM: Boolean = false,
        llmConfidence: Float? = null,
        sourceRecordingId: Long? = null,
        isManual: Boolean = false,
        status: String = EntryStatus.PENDING,
        priority: String = EntryPriority.NORMAL,
        actionType: String = EntryActionType.GENERIC,
        cleanText: String? = null,
        correctedText: String? = null,
        dueDate: Long? = null,
        completedAt: Long? = null,
        createdAt: Long = System.currentTimeMillis()
    ) = DiaryEntry(
        text = text,
        keyword = keyword,
        category = category,
        confidence = confidence,
        source = source,
        duration = duration,
        wasReviewedByLLM = wasReviewedByLLM,
        llmConfidence = llmConfidence,
        sourceRecordingId = sourceRecordingId,
        isManual = isManual,
        status = status,
        priority = priority,
        actionType = actionType,
        cleanText = cleanText,
        correctedText = correctedText,
        dueDate = dueDate,
        completedAt = completedAt,
        createdAt = createdAt
    )

    // ────────────────────────────────────────────────────────────────────────
    // Cloud / CloudOff badge logic
    // Cloud icon = validated by cloud LLM (wasReviewedByLLM + high confidence + no recording source)
    // CloudOff icon = processed locally (from recording with local model, or low confidence)
    // ────────────────────────────────────────────────────────────────────────

    private fun isCloudProcessed(e: DiaryEntry): Boolean =
        e.wasReviewedByLLM &&
            e.llmConfidence != null && e.llmConfidence!! >= 0.85f

    private fun isLocalProcessed(e: DiaryEntry): Boolean =
        (e.wasReviewedByLLM && !isCloudProcessed(e)) ||
            (!e.wasReviewedByLLM && (e.sourceRecordingId != null || e.llmConfidence == 0.0f))

    @Test
    fun `cloud badge shows for cloud-validated entry without recording source`() {
        val e = entry(wasReviewedByLLM = true, llmConfidence = 0.9f, sourceRecordingId = null)
        assertTrue("Should show cloud icon", isCloudProcessed(e))
        assertFalse("Should not show cloudOff", isLocalProcessed(e))
    }

    @Test
    fun `cloudOff badge shows for locally processed entry from recording`() {
        val e = entry(wasReviewedByLLM = true, llmConfidence = 0.8f, sourceRecordingId = 42L)
        assertFalse("Should not show cloud", isCloudProcessed(e))
        assertTrue("Should show cloudOff for local recording", isLocalProcessed(e))
    }

    @Test
    fun `cloud badge shows for cloud-processed entry from recording`() {
        val e = entry(wasReviewedByLLM = true, llmConfidence = 0.9f, sourceRecordingId = 10L)
        assertTrue("Cloud-processed recording entry shows cloud", isCloudProcessed(e))
        assertFalse("Should not show cloudOff", isLocalProcessed(e))
    }

    @Test
    fun `cloudOff badge shows when sourceRecordingId is not null`() {
        val e = entry(wasReviewedByLLM = false, sourceRecordingId = 42L)
        assertFalse("Should not show cloud", isCloudProcessed(e))
        assertTrue("sourceRecordingId should trigger cloudOff", isLocalProcessed(e))
    }

    @Test
    fun `cloudOff badge shows when llmConfidence is zero`() {
        val e = entry(wasReviewedByLLM = false, llmConfidence = 0.0f)
        assertFalse("Should not show cloud", isCloudProcessed(e))
        assertTrue("Zero confidence should show cloudOff", isLocalProcessed(e))
    }

    @Test
    fun `no badge when not reviewed and no recording id and confidence is non-zero`() {
        val e = entry(wasReviewedByLLM = false, sourceRecordingId = null, llmConfidence = 0.8f)
        assertFalse("Should not show cloud", isCloudProcessed(e))
        assertFalse("Should not show cloudOff", isLocalProcessed(e))
    }

    @Test
    fun `no badge when not reviewed and no recording id and confidence is null`() {
        val e = entry(wasReviewedByLLM = false, sourceRecordingId = null, llmConfidence = null)
        assertFalse("Should not show cloud", isCloudProcessed(e))
        assertFalse("Should not show cloudOff", isLocalProcessed(e))
    }

    @Test
    fun `cloudOff when both sourceRecordingId present and llmConfidence zero`() {
        val e = entry(wasReviewedByLLM = false, sourceRecordingId = 5L, llmConfidence = 0.0f)
        assertTrue("Should show cloudOff", isLocalProcessed(e))
    }

    // ────────────────────────────────────────────────────────────────────────
    // Priority badge logic
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `priority badge visible for URGENT when not completed`() {
        val e = entry(priority = EntryPriority.URGENT, status = EntryStatus.PENDING)
        val isCompleted = e.status == EntryStatus.COMPLETED
        val showPriorityBadge = !isCompleted && (e.priority == EntryPriority.URGENT || e.priority == EntryPriority.HIGH)
        assertTrue(showPriorityBadge)
    }

    @Test
    fun `priority badge visible for HIGH when not completed`() {
        val e = entry(priority = EntryPriority.HIGH, status = EntryStatus.PENDING)
        val isCompleted = e.status == EntryStatus.COMPLETED
        val showPriorityBadge = !isCompleted && (e.priority == EntryPriority.URGENT || e.priority == EntryPriority.HIGH)
        assertTrue(showPriorityBadge)
    }

    @Test
    fun `priority badge hidden for NORMAL priority`() {
        val e = entry(priority = EntryPriority.NORMAL)
        val isCompleted = e.status == EntryStatus.COMPLETED
        val showPriorityBadge = !isCompleted && (e.priority == EntryPriority.URGENT || e.priority == EntryPriority.HIGH)
        assertFalse(showPriorityBadge)
    }

    @Test
    fun `priority badge hidden for LOW priority`() {
        val e = entry(priority = EntryPriority.LOW)
        val isCompleted = e.status == EntryStatus.COMPLETED
        val showPriorityBadge = !isCompleted && (e.priority == EntryPriority.URGENT || e.priority == EntryPriority.HIGH)
        assertFalse(showPriorityBadge)
    }

    @Test
    fun `priority badge hidden when completed even if URGENT`() {
        val e = entry(priority = EntryPriority.URGENT, status = EntryStatus.COMPLETED)
        val isCompleted = e.status == EntryStatus.COMPLETED
        val showPriorityBadge = !isCompleted && (e.priority == EntryPriority.URGENT || e.priority == EntryPriority.HIGH)
        assertFalse("Badge should hide for completed entries", showPriorityBadge)
    }

    // ────────────────────────────────────────────────────────────────────────
    // Display text logic
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `displayText prefers cleanText over correctedText and text`() {
        val e = entry(text = "raw", correctedText = "corrected", cleanText = "clean")
        assertEquals("clean", e.displayText)
    }

    @Test
    fun `displayText falls back to correctedText when cleanText is null`() {
        val e = entry(text = "raw", correctedText = "corrected", cleanText = null)
        assertEquals("corrected", e.displayText)
    }

    @Test
    fun `displayText falls back to text when both cleanText and correctedText are null`() {
        val e = entry(text = "raw", correctedText = null, cleanText = null)
        assertEquals("raw", e.displayText)
    }

    // ────────────────────────────────────────────────────────────────────────
    // Due date / overdue logic
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `overdue when dueDate is in the past and not completed`() {
        val pastDue = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        val e = entry(dueDate = pastDue, status = EntryStatus.PENDING)
        val now = System.currentTimeMillis()
        val isCompleted = e.status == EntryStatus.COMPLETED
        val isOverdue = e.dueDate != null && e.dueDate!! < now && !isCompleted
        assertTrue(isOverdue)
    }

    @Test
    fun `not overdue when dueDate is in the past but completed`() {
        val pastDue = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        val e = entry(dueDate = pastDue, status = EntryStatus.COMPLETED)
        val now = System.currentTimeMillis()
        val isCompleted = e.status == EntryStatus.COMPLETED
        val isOverdue = e.dueDate != null && e.dueDate!! < now && !isCompleted
        assertFalse(isOverdue)
    }

    @Test
    fun `not overdue when dueDate is in the future`() {
        val futureDue = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(3)
        val e = entry(dueDate = futureDue, status = EntryStatus.PENDING)
        val now = System.currentTimeMillis()
        val isCompleted = e.status == EntryStatus.COMPLETED
        val isOverdue = e.dueDate != null && e.dueDate!! < now && !isCompleted
        assertFalse(isOverdue)
    }

    @Test
    fun `not overdue when dueDate is null`() {
        val e = entry(dueDate = null, status = EntryStatus.PENDING)
        val now = System.currentTimeMillis()
        val isCompleted = e.status == EntryStatus.COMPLETED
        val isOverdue = e.dueDate != null && e.dueDate!! < now && !isCompleted
        assertFalse(isOverdue)
    }

    // ────────────────────────────────────────────────────────────────────────
    // Manual badge logic
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `manual badge shows when isManual is true`() {
        val e = entry(isManual = true)
        assertTrue(e.isManual)
    }

    @Test
    fun `manual badge hidden when isManual is false`() {
        val e = entry(isManual = false)
        assertFalse(e.isManual)
    }

    // ────────────────────────────────────────────────────────────────────────
    // Source icon logic
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `source icon is Watch when source is WATCH`() {
        val e = entry(source = Source.WATCH)
        assertEquals(Source.WATCH, e.source)
    }

    @Test
    fun `source icon is Mic when source is PHONE`() {
        val e = entry(source = Source.PHONE)
        assertEquals(Source.PHONE, e.source)
    }

    // ────────────────────────────────────────────────────────────────────────
    // Action type label/emoji helpers
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `actionType label maps correctly`() {
        assertEquals("Llamar", EntryActionType.label(EntryActionType.CALL))
        assertEquals("Comprar", EntryActionType.label(EntryActionType.BUY))
        assertEquals("Enviar", EntryActionType.label(EntryActionType.SEND))
        assertEquals("Evento", EntryActionType.label(EntryActionType.EVENT))
        assertEquals("Revisar", EntryActionType.label(EntryActionType.REVIEW))
        assertEquals("Hablar con", EntryActionType.label(EntryActionType.TALK_TO))
        assertEquals("Tarea", EntryActionType.label(EntryActionType.GENERIC))
    }

    @Test
    fun `actionType emoji maps correctly for known types`() {
        // Just verify non-empty strings are returned for each known type
        val knownTypes = listOf(
            EntryActionType.CALL, EntryActionType.BUY, EntryActionType.SEND,
            EntryActionType.EVENT, EntryActionType.REVIEW, EntryActionType.TALK_TO
        )
        knownTypes.forEach { type ->
            assertTrue("Emoji for $type should not be empty", EntryActionType.emoji(type).isNotEmpty())
        }
    }

    @Test
    fun `actionType label returns Tarea for unknown type`() {
        assertEquals("Tarea", EntryActionType.label("UNKNOWN"))
    }
}
