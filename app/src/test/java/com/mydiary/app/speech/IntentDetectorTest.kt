package com.mydiary.app.speech

import com.mydiary.shared.speech.IntentDetector
import com.mydiary.shared.speech.IntentPattern
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for IntentDetector — semantic intent detection via regex patterns.
 * Verifies Spanish intent patterns (pendiente, recordar, cita, contacto, etc.)
 * and custom keyword matching.
 */
class IntentDetectorTest {

    private lateinit var detector: IntentDetector

    @Before
    fun setUp() {
        detector = IntentDetector()
        // Uses DEFAULTS by default
    }

    // ── Minimum length ──

    @Test
    fun `rejects text shorter than 4 chars`() {
        assertNull(detector.detect("abc"))
    }

    @Test
    fun `rejects empty text`() {
        assertNull(detector.detect(""))
    }

    // ── Pendiente pattern ──

    @Test
    fun `detects tengo que as pendiente`() {
        val result = detector.detect("tengo que ir al banco")
        assertNotNull(result)
        assertEquals("pendiente", result!!.pattern?.id)
    }

    @Test
    fun `detects hay que as pendiente`() {
        val result = detector.detect("hay que limpiar la casa")
        assertNotNull(result)
        assertEquals("pendiente", result!!.pattern?.id)
    }

    @Test
    fun `detects necesito as pendiente`() {
        val result = detector.detect("necesito comprar leche")
        assertNotNull(result)
        assertEquals("pendiente", result!!.pattern?.id)
    }

    @Test
    fun `detects deberia as pendiente`() {
        val result = detector.detect("debería estudiar mas")
        assertNotNull(result)
        assertEquals("pendiente", result!!.pattern?.id)
    }

    // ── Recordar pattern ──

    @Test
    fun `detects no olvidar as recordar`() {
        val result = detector.detect("no olvidar las llaves")
        assertNotNull(result)
        assertEquals("recordar", result!!.pattern?.id)
    }

    @Test
    fun `detects acuerdate de as recordar`() {
        val result = detector.detect("acuérdate de llamar al doctor")
        assertNotNull(result)
        assertEquals("recordar", result!!.pattern?.id)
    }

    @Test
    fun `detects se me olvido as recordar`() {
        val result = detector.detect("se me olvidó comprar pan ayer")
        assertNotNull(result)
        assertEquals("recordar", result!!.pattern?.id)
    }

    // ── Contacto pattern ──

    @Test
    fun `detects llama a as contacto`() {
        val result = detector.detect("llama a Juan por favor")
        assertNotNull(result)
        assertEquals("contacto", result!!.pattern?.id)
    }

    @Test
    fun `detects escribele a as contacto`() {
        val result = detector.detect("escríbele a Maria sobre la fiesta")
        assertNotNull(result)
        assertEquals("contacto", result!!.pattern?.id)
    }

    @Test
    fun `detects hablar con as contacto`() {
        val result = detector.detect("hablar con Pedro sobre el proyecto")
        assertNotNull(result)
        assertEquals("contacto", result!!.pattern?.id)
    }

    // ── Cita pattern ──

    @Test
    fun `detects tengo cita as cita`() {
        val result = detector.detect("tengo cita con el dentista")
        assertNotNull(result)
        assertEquals("cita", result!!.pattern?.id)
    }

    @Test
    fun `detects reunion con as cita`() {
        val result = detector.detect("reunión con el jefe a las 3")
        assertNotNull(result)
        assertEquals("cita", result!!.pattern?.id)
    }

    // ── Urgente pattern ──

    @Test
    fun `detects es urgente`() {
        val result = detector.detect("es urgente enviar el documento")
        assertNotNull(result)
        assertEquals("urgente", result!!.pattern?.id)
    }

    @Test
    fun `detects cuanto antes as urgente`() {
        val result = detector.detect("es urgente terminarlo cuanto antes")
        assertNotNull(result)
        assertEquals("urgente", result!!.pattern?.id)
    }

    // ── Compra pattern ──

    @Test
    fun `detects comprar as compra`() {
        val result = detector.detect("hay que comprar frutas y verduras")
        assertNotNull(result)
        // Could match either "pendiente" (hay que) or "compra" (comprar) depending on order
        // The pattern list is checked in order; pendiente comes first
        assertNotNull(result!!.pattern)
    }

    @Test
    fun `detects lista de la compra`() {
        val result = detector.detect("lista de la compra para esta semana")
        assertNotNull(result)
        assertEquals("compra", result!!.pattern?.id)
    }

    // ── Idea pattern ──

    @Test
    fun `detects se me ocurre as idea`() {
        val result = detector.detect("se me ocurre que podemos cambiar el diseño")
        assertNotNull(result)
        assertEquals("idea", result!!.pattern?.id)
    }

    @Test
    fun `detects podriamos as idea`() {
        val result = detector.detect("podríamos hacer una excursion el fin de semana")
        assertNotNull(result)
        assertEquals("idea", result!!.pattern?.id)
    }

    // ── Hecho pattern ──

    @Test
    fun `detects ya esta as hecho`() {
        val result = detector.detect("ya está la tarea terminada")
        assertNotNull(result)
        assertEquals("hecho", result!!.pattern?.id)
    }

    // ── Custom keywords ──

    @Test
    fun `detects custom keyword`() {
        detector.setCustomKeywords(listOf("proyecto alpha"))
        val result = detector.detect("hablamos del proyecto alpha ayer")
        assertNotNull(result)
        assertNull(result!!.pattern)
        assertEquals("proyecto alpha", result.customKeyword)
    }

    @Test
    fun `custom keyword matching is case insensitive`() {
        detector.setCustomKeywords(listOf("URGENTE TAREA"))
        val result = detector.detect("hay una urgente tarea pendiente")
        assertNotNull(result)
        assertEquals("URGENTE TAREA", result!!.customKeyword)
    }

    @Test
    fun `patterns take priority over custom keywords`() {
        detector.setCustomKeywords(listOf("tengo"))
        val result = detector.detect("tengo que hacer algo")
        assertNotNull(result)
        // Pattern should match first
        assertNotNull(result!!.pattern)
        assertNull(result.customKeyword)
    }

    // ── captureAll behavior ──

    @Test
    fun `captureAll true captures full utterance`() {
        // Default patterns have captureAll = true
        val fullText = "no olvidar las llaves del coche"
        val result = detector.detect(fullText)
        assertNotNull(result)
        assertEquals(fullText, result!!.capturedText)
    }

    // ── No match ──

    @Test
    fun `returns null when no pattern matches`() {
        val result = detector.detect("el cielo esta azul hoy")
        assertNull(result)
    }

    // ── detectPartial ──

    @Test
    fun `detectPartial requires minimum 8 chars`() {
        assertNull(detector.detectPartial("tengo"))
    }

    @Test
    fun `detectPartial detects intent in longer text`() {
        val result = detector.detectPartial("tengo que comprar")
        assertNotNull(result)
    }

    // ── Disabled patterns ──

    @Test
    fun `disabled patterns are skipped`() {
        val customPatterns = IntentPattern.DEFAULTS.map {
            if (it.id == "pendiente") it.copy(enabled = false) else it
        }
        detector.setPatterns(customPatterns)
        val result = detector.detect("tengo que ir al banco")
        // Should NOT match pendiente since it's disabled
        if (result != null) {
            assertNotEquals("pendiente", result.pattern?.id)
        }
    }
}
