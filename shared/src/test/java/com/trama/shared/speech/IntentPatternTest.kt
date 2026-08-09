package com.trama.shared.speech

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
    fun `deserialize keeps current built-in categories and custom ones`() {
        val stored = listOf(
            IntentPattern(id = "recordatorios", label = "Custom Label", triggers = listOf("custom trigger")),
            IntentPattern(id = "trabajo", label = "Trabajo", triggers = listOf("proyecto"), isCustom = true)
        )
        val json = IntentPattern.serialize(stored)
        val restored = IntentPattern.deserialize(json)

        val reminders = restored.find { it.id == "recordatorios" }
        assertEquals("Custom Label", reminders?.label)
        assertTrue(restored.any { it.id == "trabajo" && it.isCustom })
        assertEquals(IntentPattern.DEFAULTS.size + 1, restored.size)
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
        assertEquals(listOf("recordatorios", "tareas", "compromisos"), ids)
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

    @Test
    fun `compact preset stays below twenty five explicit phrases`() {
        val explicitPhrases = IntentPattern.DEFAULTS.sumOf { it.normalizedTriggers.size }

        assertTrue("explicitPhrases=$explicitPhrases", explicitPhrases <= 25)
        assertEquals(
            explicitPhrases,
            IntentPattern.DEFAULTS.flatMap { it.normalizedTriggers }.distinct().size
        )
    }

    @Test
    fun `compiled trigger regex respects lexical boundaries`() {
        val regex = IntentPattern.buildRegex(listOf("cita", "app"))

        assertTrue(regex.containsMatchIn("tengo cita mañana"))
        assertFalse(regex.containsMatchIn("necesitar más tiempo"))
        assertFalse(regex.containsMatchIn("mensaje por whatsapp"))
    }

    @Test
    fun `current preset preserves removed built-in trigger`() {
        val reminders = IntentPattern.DEFAULTS.first { it.id == "recordatorios" }
            .copy(triggers = listOf("nota mental"))

        val restored = IntentPattern.deserialize(IntentPattern.serialize(listOf(reminders)))

        assertEquals(listOf("nota mental"), restored.first { it.id == "recordatorios" }.triggers)
    }

    @Test
    fun `legacy expanded preset is compacted while custom additions survive`() {
        val legacy = IntentPattern(
            id = "tareas",
            label = "Tareas",
            triggers = listOf("tengo que comprar", "tengo de compra", "mi frase privada")
        )

        val restored = IntentPattern.deserialize(IntentPattern.serialize(listOf(legacy)))
            .first { it.id == "tareas" }

        assertEquals(IntentPattern.CURRENT_PRESET_VERSION, restored.presetVersion)
        assertTrue(restored.triggers.contains("mi frase privada"))
        assertFalse(restored.triggers.contains("tengo de compra"))
        assertTrue(restored.triggers.size < 10)
    }
}
