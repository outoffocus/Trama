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
    fun `detects tengo que as tareas`() {
        val result = detector.detect("tengo que estudiar mas")
        assertNotNull(result)
        assertEquals("tareas", result!!.pattern?.id)
    }

    @Test
    fun `detects llamar a as comunicacion`() {
        val result = detector.detect("llamar a mi madre luego")
        assertNotNull(result)
        assertEquals("comunicacion", result!!.pattern?.id)
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
        detector.setCustomKeywords(listOf("PROYECTO ZETA"))
        val result = detector.detect("hablamos del proyecto zeta ayer")
        assertNotNull(result)
        assertEquals("PROYECTO ZETA", result!!.customKeyword)
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
    fun `detects fuzzy typo for recordar`() {
        val result = detector.detect("recorda llamar a casa")
        assertNotNull(result)
        assertEquals("recordatorios", result!!.pattern?.id)
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

    @Test
    fun `rejects broad default triggers that caused ambient false positives`() {
        assertNull(detector.detect("vamos a poner una luz"))
        assertNull(detector.detect("voy a hablar como sale"))
        assertNull(detector.detect("quiero mirar esto un momento"))
    }

    @Test
    fun `detects explicit useful task triggers`() {
        assertEquals("tareas", detector.detect("tienes que llamar a Pedro")?.pattern?.id)
        assertEquals("tareas", detector.detect("hay que ir a Ourense mañana")?.pattern?.id)
        assertEquals("tareas", detector.detect("tengo que reservar la comida para el domingo")?.pattern?.id)
        assertEquals("recordatorios", detector.detect("tengo que acordarme de comprar leche")?.pattern?.id)
        assertEquals("recordatorios", detector.detect("recordar comprarle el regalo a papa")?.pattern?.id)
        assertNull(detector.detect("no queria verla ahi ahi no escucho"))
    }

    @Test
    fun `detects explicit commitment triggers`() {
        assertEquals("compromisos", detector.detect("pasado mañana tengo ITV")?.pattern?.id)
        assertEquals("compromisos", detector.detect("tenemos reunión por la tarde")?.pattern?.id)
        assertEquals("compromisos", detector.detect("quedé con Elena el viernes")?.pattern?.id)
        assertEquals("compromisos", detector.detect("Lara tiene médico mañana")?.pattern?.id)
        assertEquals("compromisos", detector.detect("tiene dentista el lunes")?.pattern?.id)
    }
}
