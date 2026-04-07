package com.trama.app.summary

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for SuggestedAction, ActionType, EntryGroup, and DailySummary data classes.
 * Verifies serialization, defaults, and enum behavior.
 */
class SuggestedActionTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ── ActionType enum ──

    @Test
    fun `ActionType has all expected values`() {
        val types = ActionType.values()
        assertEquals(6, types.size)
        assertTrue(types.contains(ActionType.CALENDAR_EVENT))
        assertTrue(types.contains(ActionType.REMINDER))
        assertTrue(types.contains(ActionType.TODO))
        assertTrue(types.contains(ActionType.MESSAGE))
        assertTrue(types.contains(ActionType.CALL))
        assertTrue(types.contains(ActionType.NOTE))
    }

    @Test
    fun `ActionType valueOf works for all values`() {
        assertEquals(ActionType.CALENDAR_EVENT, ActionType.valueOf("CALENDAR_EVENT"))
        assertEquals(ActionType.TODO, ActionType.valueOf("TODO"))
        assertEquals(ActionType.CALL, ActionType.valueOf("CALL"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ActionType valueOf throws for invalid value`() {
        ActionType.valueOf("INVALID")
    }

    // ── SuggestedAction defaults ──

    @Test
    fun `SuggestedAction has correct defaults`() {
        val action = SuggestedAction(type = ActionType.TODO, title = "Test task")
        assertEquals("", action.description)
        assertNull(action.datetime)
        assertNull(action.contact)
        assertFalse(action.done)
        assertTrue(action.entryIds.isEmpty())
        assertNull(action.capturedAt)
    }

    @Test
    fun `SuggestedAction copy works`() {
        val original = SuggestedAction(type = ActionType.CALL, title = "Call mom")
        val withContact = original.copy(contact = "Mom", done = true)
        assertEquals("Mom", withContact.contact)
        assertTrue(withContact.done)
        assertEquals(ActionType.CALL, withContact.type)
    }

    // ── SuggestedAction serialization ──

    @Test
    fun `SuggestedAction serializes and deserializes`() {
        val action = SuggestedAction(
            type = ActionType.CALENDAR_EVENT,
            title = "Reunion de equipo",
            description = "Sala B",
            datetime = "2026-03-28T10:00",
            contact = "Carlos",
            done = false,
            entryIds = listOf(1L, 2L),
            capturedAt = 1711600000000L
        )
        val jsonStr = json.encodeToString(SuggestedAction.serializer(), action)
        val decoded = json.decodeFromString(SuggestedAction.serializer(), jsonStr)
        assertEquals(action, decoded)
    }

    @Test
    fun `SuggestedAction deserializes with missing optional fields`() {
        val jsonStr = """{"type":"TODO","title":"Comprar pan"}"""
        val action = json.decodeFromString(SuggestedAction.serializer(), jsonStr)
        assertEquals(ActionType.TODO, action.type)
        assertEquals("Comprar pan", action.title)
        assertEquals("", action.description)
        assertNull(action.datetime)
        assertFalse(action.done)
    }

    // ── EntryGroup ──

    @Test
    fun `EntryGroup holds data correctly`() {
        val group = EntryGroup(label = "Salud", emoji = "icon", items = listOf("Cita medico", "Farmacia"))
        assertEquals("Salud", group.label)
        assertEquals(2, group.items.size)
    }

    @Test
    fun `EntryGroup serializes and deserializes`() {
        val group = EntryGroup(label = "Pendientes", emoji = "icon", items = listOf("Comprar pan", "Llamar"))
        val jsonStr = json.encodeToString(EntryGroup.serializer(), group)
        val decoded = json.decodeFromString(EntryGroup.serializer(), jsonStr)
        assertEquals(group, decoded)
    }

    // ── DailySummary ──

    @Test
    fun `DailySummary holds data correctly`() {
        val summary = DailySummary(
            date = "2026-03-28",
            narrative = "Hoy capturaste 5 entradas.",
            groups = listOf(EntryGroup("Pendientes", "icon", listOf("Tarea 1"))),
            actions = listOf(SuggestedAction(type = ActionType.TODO, title = "Tarea 1")),
            entryCount = 5
        )
        assertEquals("2026-03-28", summary.date)
        assertEquals(5, summary.entryCount)
        assertEquals(1, summary.groups.size)
        assertEquals(1, summary.actions.size)
        assertTrue(summary.generatedAt > 0)
    }

    @Test
    fun `DailySummary defaults for empty day`() {
        val summary = DailySummary(
            date = "2026-03-28",
            narrative = "No hubo entradas hoy.",
            actions = emptyList(),
            entryCount = 0
        )
        assertTrue(summary.groups.isEmpty())
        assertTrue(summary.actions.isEmpty())
        assertEquals(0, summary.entryCount)
    }

    @Test
    fun `DailySummary serializes and deserializes`() {
        val summary = DailySummary(
            date = "2026-03-28",
            narrative = "Test narrative",
            groups = emptyList(),
            actions = listOf(SuggestedAction(type = ActionType.NOTE, title = "Nota")),
            entryCount = 1
        )
        val jsonStr = json.encodeToString(DailySummary.serializer(), summary)
        val decoded = json.decodeFromString(DailySummary.serializer(), jsonStr)
        assertEquals(summary.date, decoded.date)
        assertEquals(summary.narrative, decoded.narrative)
        assertEquals(summary.entryCount, decoded.entryCount)
        assertEquals(summary.actions.size, decoded.actions.size)
    }
}
