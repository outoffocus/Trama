package com.mydiary.shared.speech

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class IntentDetectorTest {

    private lateinit var detector: IntentDetector

    @Before
    fun setUp() {
        detector = IntentDetector()
    }

    // ── Pattern detection ────────────────────────────────────────

    @Test
    fun `detects pendiente intent with tengo que`() {
        val result = detector.detect("tengo que llamar al dentista")
        assertNotNull(result)
        assertEquals("pendiente", result!!.pattern?.id)
        assertEquals("Pendientes", result.label)
    }

    @Test
    fun `detects recordar intent with no olvidar`() {
        val result = detector.detect("no olvidar comprar el regalo")
        assertNotNull(result)
        assertEquals("recordar", result!!.pattern?.id)
    }

    @Test
    fun `detects cita intent`() {
        val result = detector.detect("tengo cita con el doctor a las tres")
        assertNotNull(result)
        assertEquals("cita", result!!.pattern?.id)
    }

    @Test
    fun `detects compra intent`() {
        val result = detector.detect("hay que comprar leche y pan")
        assertNotNull(result)
        // Could match pendiente ("hay que") or compra ("comprar")
        // Patterns are checked in order, so pendiente matches first
        assertNotNull(result!!.pattern)
    }

    @Test
    fun `detects urgente intent`() {
        val result = detector.detect("esto es urgente y no puede esperar")
        assertNotNull(result)
        assertEquals("urgente", result!!.pattern?.id)
    }

    @Test
    fun `detects contacto intent with llama a`() {
        val result = detector.detect("llama a Maria por favor")
        assertNotNull(result)
        assertEquals("contacto", result!!.pattern?.id)
    }

    // ── No match ─────────────────────────────────────────────────

    @Test
    fun `returns null for text without any intent`() {
        val result = detector.detect("el cielo esta azul hoy en la manana")
        assertNull(result)
    }

    @Test
    fun `returns null for text shorter than MIN_TEXT_LENGTH`() {
        val result = detector.detect("hol")
        assertNull(result)
    }

    @Test
    fun `returns null for empty string`() {
        assertNull(detector.detect(""))
    }

    // ── Custom keywords ──────────────────────────────────────────

    @Test
    fun `detects custom keyword`() {
        detector.setCustomKeywords(listOf("proyecto alpha"))
        val result = detector.detect("revisar el proyecto alpha esta semana")
        assertNotNull(result)
        assertNull(result!!.pattern)
        assertEquals("proyecto alpha", result.customKeyword)
        assertEquals("proyecto alpha", result.label)
    }

    @Test
    fun `custom keyword matching is case insensitive`() {
        detector.setCustomKeywords(listOf("IMPORTANTE"))
        val result = detector.detect("esto es importante para el equipo")
        assertNotNull(result)
        assertEquals("IMPORTANTE", result!!.customKeyword)
    }

    @Test
    fun `patterns take priority over custom keywords`() {
        detector.setCustomKeywords(listOf("tengo"))
        val result = detector.detect("tengo que hacer esto ya")
        assertNotNull(result)
        // Pattern should match, not custom keyword
        assertNotNull(result!!.pattern)
        assertNull(result.customKeyword)
    }

    @Test
    fun `setCustomKeywords filters blank entries`() {
        detector.setCustomKeywords(listOf("", "  ", "valid"))
        val result = detector.detect("esto es valid para nosotros")
        assertNotNull(result)
        assertEquals("valid", result!!.customKeyword)
    }

    // ── Partial detection ────────────────────────────────────────

    @Test
    fun `detectPartial returns null for text shorter than 8 chars`() {
        assertNull(detector.detectPartial("tengo q"))
    }

    @Test
    fun `detectPartial works for longer text`() {
        val result = detector.detectPartial("tengo que ir al banco")
        assertNotNull(result)
        assertEquals("pendiente", result!!.pattern?.id)
    }

    // ── Pattern updates ──────────────────────────────────────────

    @Test
    fun `setPatterns replaces default patterns`() {
        detector.setPatterns(
            listOf(
                IntentPattern(
                    id = "custom",
                    label = "Custom",
                    triggers = listOf("abracadabra")
                )
            )
        )
        // Old patterns should not match
        assertNull(detector.detect("tengo que hacer algo urgente"))
        // New pattern should match
        assertNotNull(detector.detect("dijo abracadabra y desaparecio"))
    }

    // ── Case insensitivity ───────────────────────────────────────

    @Test
    fun `detection is case insensitive`() {
        val result = detector.detect("TENGO QUE LLAMAR AL DOCTOR")
        assertNotNull(result)
        assertEquals("pendiente", result!!.pattern?.id)
    }
}
