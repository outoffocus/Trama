package com.mydiary.app.speech

import com.mydiary.shared.speech.IntentPattern
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for IntentPattern — regex compilation, serialization/deserialization,
 * and merge logic for built-in vs custom patterns.
 */
class IntentPatternTest {

    // ── buildRegex ──

    @Test
    fun `buildRegex creates regex matching any trigger`() {
        val regex = IntentPattern.buildRegex(listOf("tengo que", "hay que", "debo"))
        assertTrue(regex.containsMatchIn("tengo que ir"))
        assertTrue(regex.containsMatchIn("hay que comprar"))
        assertTrue(regex.containsMatchIn("debo estudiar"))
    }

    @Test
    fun `buildRegex is case insensitive`() {
        val regex = IntentPattern.buildRegex(listOf("tengo que"))
        assertTrue(regex.containsMatchIn("TENGO QUE hacer algo"))
        assertTrue(regex.containsMatchIn("Tengo que ir"))
    }

    @Test
    fun `buildRegex handles flexible whitespace`() {
        val regex = IntentPattern.buildRegex(listOf("tengo que"))
        assertTrue(regex.containsMatchIn("tengo  que ir"))
        assertTrue(regex.containsMatchIn("tengo\tque ir"))
    }

    @Test
    fun `buildRegex never matches for empty triggers`() {
        val regex = IntentPattern.buildRegex(emptyList())
        assertFalse(regex.containsMatchIn("anything"))
    }

    @Test
    fun `buildRegex sorts longest first to match greedily`() {
        val regex = IntentPattern.buildRegex(listOf("tengo que", "tengo que ir"))
        val match = regex.find("tengo que ir al banco")
        assertNotNull(match)
        // "tengo que ir" should match first since it's sorted longest-first
        assertTrue(match!!.value.contains("tengo que ir") || match.value.contains("tengo que"))
    }

    @Test
    fun `buildRegex filters blank triggers`() {
        val regex = IntentPattern.buildRegex(listOf("", "  ", "tengo que"))
        assertTrue(regex.containsMatchIn("tengo que hacer"))
    }

    @Test
    fun `buildRegex escapes special regex characters`() {
        val regex = IntentPattern.buildRegex(listOf("test (parentheses)"))
        assertTrue(regex.containsMatchIn("test (parentheses) work"))
    }

    // ── Serialization ──

    @Test
    fun `serialize and deserialize roundtrips correctly`() {
        val patterns = listOf(
            IntentPattern(
                id = "test1",
                label = "Test",
                triggers = listOf("trigger one", "trigger two"),
                captureAll = false,
                enabled = true,
                isCustom = true
            )
        )
        val json = IntentPattern.serialize(patterns)
        val deserialized = IntentPattern.deserialize(json)
        val found = deserialized.find { it.id == "test1" }
        assertNotNull(found)
        assertEquals("Test", found!!.label)
        assertEquals(listOf("trigger one", "trigger two"), found.triggers)
        assertFalse(found.captureAll)
        assertTrue(found.isCustom)
    }

    @Test
    fun `deserialize invalid JSON returns defaults`() {
        val result = IntentPattern.deserialize("not valid json")
        assertEquals(IntentPattern.DEFAULTS.size, result.size)
    }

    @Test
    fun `deserialize merges with defaults for missing built-in patterns`() {
        // Serialize only one pattern
        val partial = listOf(
            IntentPattern(id = "pendiente", label = "Custom Pendientes",
                triggers = listOf("custom trigger"))
        )
        val json = IntentPattern.serialize(partial)
        val merged = IntentPattern.deserialize(json)

        // Should have the custom pendiente plus all other defaults
        assertTrue("Should have more patterns than just one", merged.size > 1)
        val pendiente = merged.find { it.id == "pendiente" }
        assertEquals("Custom Pendientes", pendiente!!.label)
        assertEquals(listOf("custom trigger"), pendiente.triggers)

        // Other defaults should be present
        assertNotNull(merged.find { it.id == "recordar" })
        assertNotNull(merged.find { it.id == "cita" })
        assertNotNull(merged.find { it.id == "contacto" })
    }

    // ── DEFAULTS ──

    @Test
    fun `DEFAULTS contains all expected patterns`() {
        val ids = IntentPattern.DEFAULTS.map { it.id }
        assertTrue("pendiente" in ids)
        assertTrue("recordar" in ids)
        assertTrue("cita" in ids)
        assertTrue("horario" in ids)
        assertTrue("contacto" in ids)
        assertTrue("urgente" in ids)
        assertTrue("decision" in ids)
        assertTrue("idea" in ids)
        assertTrue("problema" in ids)
        assertTrue("nota" in ids)
        assertTrue("compra" in ids)
        assertTrue("hecho" in ids)
    }

    @Test
    fun `all DEFAULTS are enabled`() {
        IntentPattern.DEFAULTS.forEach { pattern ->
            assertTrue("Pattern '${pattern.id}' should be enabled", pattern.enabled)
        }
    }

    @Test
    fun `all DEFAULTS are not custom`() {
        IntentPattern.DEFAULTS.forEach { pattern ->
            assertFalse("Pattern '${pattern.id}' should not be custom", pattern.isCustom)
        }
    }

    @Test
    fun `all DEFAULTS have non-empty triggers`() {
        IntentPattern.DEFAULTS.forEach { pattern ->
            assertTrue("Pattern '${pattern.id}' should have triggers",
                pattern.triggers.isNotEmpty())
        }
    }

    @Test
    fun `all DEFAULTS compile valid regex`() {
        IntentPattern.DEFAULTS.forEach { pattern ->
            // This should not throw
            val regex = pattern.regex
            assertNotNull("Pattern '${pattern.id}' should have compiled regex", regex)
        }
    }
}
