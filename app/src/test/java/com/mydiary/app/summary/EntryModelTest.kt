package com.mydiary.app.summary

import com.mydiary.shared.model.EntryActionType
import com.mydiary.shared.model.EntryPriority
import com.mydiary.shared.model.EntryStatus
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for shared model constants and helper methods:
 * EntryActionType, EntryPriority, EntryStatus.
 */
class EntryModelTest {

    // ── EntryActionType.emoji ──

    @Test
    fun `emoji returns correct emoji for CALL`() {
        val emoji = EntryActionType.emoji(EntryActionType.CALL)
        assertNotNull(emoji)
        assertTrue(emoji.isNotEmpty())
    }

    @Test
    fun `emoji returns correct emoji for BUY`() {
        val emoji = EntryActionType.emoji(EntryActionType.BUY)
        assertNotNull(emoji)
        assertTrue(emoji.isNotEmpty())
    }

    @Test
    fun `emoji returns fallback for unknown type`() {
        val emoji = EntryActionType.emoji("UNKNOWN")
        assertNotNull(emoji)
        assertTrue(emoji.isNotEmpty())
    }

    @Test
    fun `emoji returns different emojis for different types`() {
        val callEmoji = EntryActionType.emoji(EntryActionType.CALL)
        val buyEmoji = EntryActionType.emoji(EntryActionType.BUY)
        val sendEmoji = EntryActionType.emoji(EntryActionType.SEND)
        // Each should be different
        assertNotEquals(callEmoji, buyEmoji)
        assertNotEquals(buyEmoji, sendEmoji)
    }

    // ── EntryActionType.label ──

    @Test
    fun `label returns Llamar for CALL`() {
        assertEquals("Llamar", EntryActionType.label(EntryActionType.CALL))
    }

    @Test
    fun `label returns Comprar for BUY`() {
        assertEquals("Comprar", EntryActionType.label(EntryActionType.BUY))
    }

    @Test
    fun `label returns Enviar for SEND`() {
        assertEquals("Enviar", EntryActionType.label(EntryActionType.SEND))
    }

    @Test
    fun `label returns Evento for EVENT`() {
        assertEquals("Evento", EntryActionType.label(EntryActionType.EVENT))
    }

    @Test
    fun `label returns Revisar for REVIEW`() {
        assertEquals("Revisar", EntryActionType.label(EntryActionType.REVIEW))
    }

    @Test
    fun `label returns Hablar con for TALK_TO`() {
        assertEquals("Hablar con", EntryActionType.label(EntryActionType.TALK_TO))
    }

    @Test
    fun `label returns Tarea for GENERIC`() {
        assertEquals("Tarea", EntryActionType.label(EntryActionType.GENERIC))
    }

    @Test
    fun `label returns Tarea for unknown type`() {
        assertEquals("Tarea", EntryActionType.label("INVALID"))
    }

    // ── Constants ──

    @Test
    fun `EntryActionType constants have correct values`() {
        assertEquals("CALL", EntryActionType.CALL)
        assertEquals("BUY", EntryActionType.BUY)
        assertEquals("SEND", EntryActionType.SEND)
        assertEquals("EVENT", EntryActionType.EVENT)
        assertEquals("REVIEW", EntryActionType.REVIEW)
        assertEquals("TALK_TO", EntryActionType.TALK_TO)
        assertEquals("GENERIC", EntryActionType.GENERIC)
    }

    @Test
    fun `EntryPriority constants have correct values`() {
        assertEquals("LOW", EntryPriority.LOW)
        assertEquals("NORMAL", EntryPriority.NORMAL)
        assertEquals("HIGH", EntryPriority.HIGH)
        assertEquals("URGENT", EntryPriority.URGENT)
    }

    @Test
    fun `EntryStatus constants have correct values`() {
        assertEquals("PENDING", EntryStatus.PENDING)
        assertEquals("COMPLETED", EntryStatus.COMPLETED)
        assertEquals("DISCARDED", EntryStatus.DISCARDED)
    }
}
