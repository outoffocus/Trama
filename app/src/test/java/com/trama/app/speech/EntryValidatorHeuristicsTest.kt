package com.trama.app.speech

import com.trama.shared.speech.EntryValidatorHeuristics
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for EntryValidatorHeuristics — the pure heuristic filter that rejects
 * obvious noise and accepts clear personal speech without any network call.
 */
class EntryValidatorHeuristicsTest {

    // ── Too short fragments ──

    @Test
    fun `rejects single word`() {
        val result = EntryValidatorHeuristics.check("hola")
        assertNotNull(result)
        assertFalse(result!!.isValid)
    }

    @Test
    fun `rejects two words`() {
        val result = EntryValidatorHeuristics.check("si bueno")
        assertNotNull(result)
        assertFalse(result!!.isValid)
    }

    @Test
    fun `rejects empty string`() {
        val result = EntryValidatorHeuristics.check("")
        assertNotNull(result)
        assertFalse(result!!.isValid)
    }

    @Test
    fun `fragment rejection has high confidence`() {
        val result = EntryValidatorHeuristics.check("si")
        assertNotNull(result)
        assertTrue(result!!.confidence >= 0.8f)
    }

    // ── Noise detection ──

    @Test
    fun `rejects radio style text`() {
        val result = EntryValidatorHeuristics.check("a continuación el pronóstico del tiempo")
        assertNotNull(result)
        assertFalse(result!!.isValid)
    }

    @Test
    fun `rejects TV advertising`() {
        val result = EntryValidatorHeuristics.check("este producto está patrocinado por una marca famosa")
        assertNotNull(result)
        assertFalse(result!!.isValid)
    }

    @Test
    fun `rejects YouTube style text`() {
        val result = EntryValidatorHeuristics.check("no olvides suscríbete al canal de youtube")
        assertNotNull(result)
        assertFalse(result!!.isValid)
    }

    @Test
    fun `rejects news style text`() {
        val result = EntryValidatorHeuristics.check("según fuentes oficiales el gobierno ha decidido")
        assertNotNull(result)
        assertFalse(result!!.isValid)
    }

    @Test
    fun `rejects weather forecast`() {
        val result = EntryValidatorHeuristics.check("la temperatura será de treinta grados celsius")
        assertNotNull(result)
        assertFalse(result!!.isValid)
    }

    @Test
    fun `rejects sports commentary`() {
        val result = EntryValidatorHeuristics.check("el equipo ha ganado tres partidos esta semana en la liga")
        assertNotNull(result)
        assertFalse(result!!.isValid)
    }

    // ── Personal speech acceptance ──

    @Test
    fun `accepts text with tengo`() {
        val result = EntryValidatorHeuristics.check("tengo que ir al supermercado")
        assertNotNull(result)
        assertTrue(result!!.isValid)
    }

    @Test
    fun `accepts text with necesito`() {
        val result = EntryValidatorHeuristics.check("necesito llamar al doctor mañana")
        assertNotNull(result)
        assertTrue(result!!.isValid)
    }

    @Test
    fun `accepts text with hay que`() {
        val result = EntryValidatorHeuristics.check("hay que comprar pan y leche")
        assertNotNull(result)
        assertTrue(result!!.isValid)
    }

    @Test
    fun `accepts text with nosotros`() {
        val result = EntryValidatorHeuristics.check("nosotros vamos a ir de viaje la semana que viene")
        assertNotNull(result)
        assertTrue(result!!.isValid)
    }

    @Test
    fun `accepts text with deberia`() {
        val result = EntryValidatorHeuristics.check("debería revisar los documentos esta noche")
        assertNotNull(result)
        assertTrue(result!!.isValid)
    }

    @Test
    fun `accepts text with recordar`() {
        val result = EntryValidatorHeuristics.check("recordar enviar el correo al proveedor")
        assertNotNull(result)
        assertTrue(result!!.isValid)
    }

    @Test
    fun `accepts English personal text`() {
        val result = EntryValidatorHeuristics.check("I need to buy groceries after work today")
        assertNotNull(result)
        assertTrue(result!!.isValid)
    }

    @Test
    fun `accepts text with vamos a`() {
        val result = EntryValidatorHeuristics.check("vamos a organizar la fiesta del sabado")
        assertNotNull(result)
        assertTrue(result!!.isValid)
    }

    @Test
    fun `personal acceptance has high confidence`() {
        val result = EntryValidatorHeuristics.check("tengo que hacer la compra")
        assertNotNull(result)
        assertTrue(result!!.confidence >= 0.8f)
    }

    // ── Ambiguous cases ──

    @Test
    fun `returns null for ambiguous text`() {
        val result = EntryValidatorHeuristics.check("el perro estaba en el jardin toda la tarde")
        assertNull("Ambiguous text should return null for LLM escalation", result)
    }

    @Test
    fun `returns null for neutral statement`() {
        val result = EntryValidatorHeuristics.check("ayer fuimos al cine y vimos una pelicula")
        assertNull("Neutral statements should be ambiguous", result)
    }
}
