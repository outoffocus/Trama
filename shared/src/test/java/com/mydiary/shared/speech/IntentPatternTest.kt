package com.mydiary.shared.speech

import org.junit.Assert.*
import org.junit.Test

class IntentPatternTest {

    // ── buildRegex ───────────────────────────────────────────────

    @Test
    fun `buildRegex matches single trigger`() {
        val regex = IntentPattern.buildRegex(listOf("tengo que"))
        assertTrue(regex.containsMatchIn("yo tengo que ir"))
    }

    @Test
    fun `buildRegex matches with flexible whitespace`() {
        val regex = IntentPattern.buildRegex(listOf("tengo que"))
        assertTrue(regex.containsMatchIn("tengo  que ir"))
        assertTrue(regex.containsMatchIn("tengo   que ir"))
    }

    @Test
    fun `buildRegex is case insensitive`() {
        val regex = IntentPattern.buildRegex(listOf("tengo que"))
        assertTrue(regex.containsMatchIn("TENGO QUE ir"))
    }

    @Test
    fun `buildRegex with empty list never matches`() {
        val regex = IntentPattern.buildRegex(emptyList())
        assertFalse(regex.containsMatchIn("anything"))
    }

    @Test
    fun `buildRegex with blank entries filters them out`() {
        val regex = IntentPattern.buildRegex(listOf("", "  ", "hola"))
        assertTrue(regex.containsMatchIn("dijo hola"))
    }

    @Test
    fun `buildRegex sorts longest first`() {
        val regex = IntentPattern.buildRegex(listOf("tengo", "tengo que ir"))
        // "tengo que ir" should match before "tengo"
        val match = regex.find("tengo que ir al banco")
        assertNotNull(match)
        assertTrue(match!!.value.contains("tengo que ir"))
    }

    // ── Serialization ────────────────────────────────────────────

    @Test
    fun `serialize and deserialize patterns round-trip`() {
        val patterns = listOf(
            IntentPattern(
                id = "test",
                label = "Test",
                triggers = listOf("alpha", "beta"),
                captureAll = false,
                enabled = true,
                isCustom = true
            )
        )
        val json = IntentPattern.serialize(patterns)
        val restored = IntentPattern.deserialize(json)

        // Restored includes defaults merged in
        val testPattern = restored.find { it.id == "test" }
        assertNotNull(testPattern)
        assertEquals("Test", testPattern!!.label)
        assertEquals(listOf("alpha", "beta"), testPattern.triggers)
        assertFalse(testPattern.captureAll)
        assertTrue(testPattern.isCustom)
    }

    @Test
    fun `deserialize merges with defaults for missing patterns`() {
        val stored = listOf(
            IntentPattern(id = "pendiente", label = "Custom Label", triggers = listOf("custom trigger"))
        )
        val json = IntentPattern.serialize(stored)
        val restored = IntentPattern.deserialize(json)

        // Should keep user's custom "pendiente" label
        val pendiente = restored.find { it.id == "pendiente" }
        assertEquals("Custom Label", pendiente?.label)

        // Should add missing defaults like "recordar", "cita", etc.
        val defaultIds = IntentPattern.DEFAULTS.map { it.id }.toSet()
        for (id in defaultIds) {
            assertTrue("Missing default pattern: $id", restored.any { it.id == id })
        }
    }

    @Test
    fun `deserialize returns defaults for invalid JSON`() {
        val result = IntentPattern.deserialize("invalid json")
        assertEquals(IntentPattern.DEFAULTS.size, result.size)
    }

    // ── DEFAULTS ─────────────────────────────────────────────────

    @Test
    fun `DEFAULTS contains expected pattern IDs`() {
        val ids = IntentPattern.DEFAULTS.map { it.id }
        assertTrue("pendiente" in ids)
        assertTrue("recordar" in ids)
        assertTrue("cita" in ids)
        assertTrue("urgente" in ids)
        assertTrue("contacto" in ids)
        assertTrue("compra" in ids)
        assertTrue("idea" in ids)
    }

    @Test
    fun `all default patterns are enabled and not custom`() {
        for (pattern in IntentPattern.DEFAULTS) {
            assertTrue("${pattern.id} should be enabled", pattern.enabled)
            assertFalse("${pattern.id} should not be custom", pattern.isCustom)
        }
    }

    @Test
    fun `all default patterns have non-empty triggers`() {
        for (pattern in IntentPattern.DEFAULTS) {
            assertTrue("${pattern.id} should have triggers", pattern.triggers.isNotEmpty())
        }
    }

    @Test
    fun `all default patterns have captureAll true`() {
        for (pattern in IntentPattern.DEFAULTS) {
            assertTrue("${pattern.id} should capture all", pattern.captureAll)
        }
    }
}
