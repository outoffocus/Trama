package com.trama.app.speech

import com.trama.shared.speech.IntentDetector
import com.trama.shared.speech.IntentPattern
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for IntentDetector — verifies the built-in reminders category and custom keywords.
 */
class IntentDetectorTest {

    private lateinit var detector: IntentDetector

    @Before
    fun setUp() {
        detector = IntentDetector()
    }

    @Test
    fun `rejects text shorter than 4 chars`() {
        assertNull(detector.detect("abc"))
    }

    @Test
    fun `rejects empty text`() {
        assertNull(detector.detect(""))
    }

    @Test
    fun `detects recordar as recordatorios`() {
        val result = detector.detect("recordar ir al banco")
        assertNotNull(result)
        assertEquals("recordatorios", result!!.pattern?.id)
    }

    @Test
    fun `detects acordarme de as recordatorios`() {
        val result = detector.detect("acordarme de llamar a casa")
        assertNotNull(result)
        assertEquals("recordatorios", result!!.pattern?.id)
    }

    @Test
    fun `detects acordarnos de as recordatorios`() {
        val result = detector.detect("acordarnos de enviar el correo")
        assertNotNull(result)
        assertEquals("recordatorios", result!!.pattern?.id)
    }

    @Test
    fun `detects me olvide as recordatorios`() {
        val result = detector.detect("me olvidé estudiar mas")
        assertNotNull(result)
        assertEquals("recordatorios", result!!.pattern?.id)
    }

    @Test
    fun `detects se me fue la olla as recordatorios`() {
        val result = detector.detect("se me fue la olla comprar pan ayer")
        assertNotNull(result)
        assertEquals("recordatorios", result!!.pattern?.id)
    }

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
        detector.setCustomKeywords(listOf("recordar"))
        val result = detector.detect("recordar llamar a casa")
        assertNotNull(result)
        assertNotNull(result!!.pattern)
        assertNull(result.customKeyword)
    }

    @Test
    fun `captureAll true captures full utterance`() {
        val fullText = "recordar las llaves del coche"
        val result = detector.detect(fullText)
        assertNotNull(result)
        assertEquals(fullText, result!!.capturedText)
    }

    @Test
    fun `returns null when no pattern matches`() {
        val result = detector.detect("el cielo esta azul hoy")
        assertNull(result)
    }

    @Test
    fun `detectPartial requires minimum 8 chars`() {
        assertNull(detector.detectPartial("recorda"))
    }

    @Test
    fun `detectPartial detects intent in longer text`() {
        val result = detector.detectPartial("recordar comprar")
        assertNotNull(result)
    }

    @Test
    fun `disabled patterns are skipped`() {
        val customPatterns = IntentPattern.DEFAULTS.map {
            if (it.id == "recordatorios") it.copy(enabled = false) else it
        }
        detector.setPatterns(customPatterns)
        val result = detector.detect("recordar ir al banco")
        if (result != null) {
            assertNotEquals("recordatorios", result.pattern?.id)
        }
    }
}
