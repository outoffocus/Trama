package com.trama.shared.speech

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class IntentDetectorTest {

    private lateinit var detector: IntentDetector

    @Before
    fun setUp() {
        detector = IntentDetector()
    }

    @Test
    fun `detects reminders intent with recordar`() {
        val result = detector.detect("recordar llamar al dentista")
        assertNotNull(result)
        assertEquals("recordatorios", result!!.pattern?.id)
        assertEquals("Recordatorios", result.label)
    }

    @Test
    fun `detects reminders intent with me olvide`() {
        val result = detector.detect("me olvidé comprar el regalo")
        assertNotNull(result)
        assertEquals("recordatorios", result!!.pattern?.id)
    }

    @Test
    fun `detects reminders intent without accent`() {
        val result = detector.detect("me olvide comprar el regalo")
        assertNotNull(result)
        assertEquals("recordatorios", result!!.pattern?.id)
    }

    @Test
    fun `detects reminders intent with small asr typo`() {
        val result = detector.detect("recorda llamar al dentista")
        assertNotNull(result)
        assertEquals("recordatorios", result!!.pattern?.id)
    }

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
        detector.setCustomKeywords(listOf("recordar"))
        val result = detector.detect("recordar hacer esto ya")
        assertNotNull(result)
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

    @Test
    fun `detectPartial returns null for text shorter than 8 chars`() {
        assertNull(detector.detectPartial("recorda"))
    }

    @Test
    fun `detectPartial works for longer text`() {
        val result = detector.detectPartial("recordar ir al banco")
        assertNotNull(result)
        assertEquals("recordatorios", result!!.pattern?.id)
    }

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
        assertNull(detector.detect("recordar hacer algo urgente"))
        assertNotNull(detector.detect("dijo abracadabra y desaparecio"))
    }

    @Test
    fun `detection is case insensitive`() {
        val result = detector.detect("RECORDAR LLAMAR AL DOCTOR")
        assertNotNull(result)
        assertEquals("recordatorios", result!!.pattern?.id)
    }

    @Test
    fun `rejects broad conversational planning triggers`() {
        assertNull(detector.detect("vamos a cenar y ponemos musica"))
        assertNull(detector.detect("voy a hablar como sale"))
        assertNull(detector.detect("quiero ver que pasa"))
        assertNull(detector.detect("me gustaria que fuera distinto"))
    }

    @Test
    fun `detects explicit actionable hay que forms`() {
        assertEquals("tareas", detector.detect("hay que llamar a Elena mañana")?.pattern?.id)
        assertEquals("tareas", detector.detect("hay que ir a la oficina")?.pattern?.id)
        assertEquals("tareas", detector.detect("hay que comprar pan")?.pattern?.id)
    }

    @Test
    fun `detects explicit memory forms`() {
        assertEquals("recordatorios", detector.detect("tengo que acordarme de llevar las llaves")?.pattern?.id)
        assertEquals("recordatorios", detector.detect("recuérdame que llame a Marta")?.pattern?.id)
        assertEquals("recordatorios", detector.detect("acuérdate de pagar el recibo")?.pattern?.id)
    }

    @Test
    fun `detects commitment and appointment triggers`() {
        assertEquals("compromisos", detector.detect("mañana tengo la ITV")?.pattern?.id)
        assertEquals("compromisos", detector.detect("tengo cita con el medico")?.pattern?.id)
        assertEquals("compromisos", detector.detect("he quedado con Fabio esta noche")?.pattern?.id)
        assertEquals("compromisos", detector.detect("mañana tiene médico por la tarde")?.pattern?.id)
        assertEquals("compromisos", detector.detect("tiene cita en el dentista")?.pattern?.id)
    }
}
