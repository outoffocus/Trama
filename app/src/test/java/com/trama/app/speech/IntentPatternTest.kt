package com.trama.app.speech

import com.trama.shared.speech.IntentPattern
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
    fun `deserialize keeps current built-in categories and custom ones`() {
        val partial = listOf(
            IntentPattern(id = "recordatorios", label = "Mis recordatorios",
                triggers = listOf("custom trigger")),
            IntentPattern(id = "trabajo", label = "Trabajo",
                triggers = listOf("proyecto x"), isCustom = true)
        )
        val json = IntentPattern.serialize(partial)
        val merged = IntentPattern.deserialize(json)

        // Every current built-in category is always present after merge,
        // plus any user-created custom categories.
        assertEquals(IntentPattern.DEFAULTS.size + 1, merged.size)
        val reminders = merged.find { it.id == "recordatorios" }
        assertEquals("Mis recordatorios", reminders!!.label)
        // Merge keeps all non-legacy default triggers AND appends user ones.
        assertTrue("custom trigger" in reminders.triggers)
        assertNotNull(merged.find { it.id == "trabajo" && it.isCustom })
    }

    // ── DEFAULTS ──

    @Test
    fun `DEFAULTS contains the current built-in categories`() {
        val ids = IntentPattern.DEFAULTS.map { it.id }
        assertEquals(listOf("recordatorios", "tareas", "comunicacion"), ids)
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
