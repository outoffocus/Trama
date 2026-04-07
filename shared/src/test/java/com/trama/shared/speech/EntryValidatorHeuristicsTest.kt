package com.trama.shared.speech

import org.junit.Assert.*
import org.junit.Test

class EntryValidatorHeuristicsTest {

    // ── Short fragments ──────────────────────────────────────────

    @Test
    fun `rejects fragments with fewer than 3 words`() {
        val result = EntryValidatorHeuristics.check("hola mundo")
        assertNotNull(result)
        assertFalse(result!!.isValid)
        assertEquals(0.9f, result.confidence, 0.001f)
    }

    @Test
    fun `rejects single word`() {
        val result = EntryValidatorHeuristics.check("hola")
        assertNotNull(result)
        assertFalse(result!!.isValid)
    }

    // ── Noise detection ──────────────────────────────────────────

    @Test
    fun `rejects TV or radio noise`() {
        val result = EntryValidatorHeuristics.check("a continuación vamos con las noticias del día")
        assertNotNull(result)
        assertFalse(result!!.isValid)
        assertEquals(0.85f, result.confidence, 0.001f)
    }

    @Test
    fun `rejects weather broadcast`() {
        val result = EntryValidatorHeuristics.check("la temperatura sera de veinte grados celsius manana")
        assertNotNull(result)
        assertFalse(result!!.isValid)
    }

    @Test
    fun `rejects YouTube content`() {
        val result = EntryValidatorHeuristics.check("no olviden suscribirse al canal de youtube")
        assertNotNull(result)
        assertFalse(result!!.isValid)
    }

    @Test
    fun `rejects political news`() {
        val result = EntryValidatorHeuristics.check("el presidente anuncio nuevas medidas economicas")
        assertNotNull(result)
        assertFalse(result!!.isValid)
    }

    // ── Personal speech acceptance ───────────────────────────────

    @Test
    fun `accepts personal speech with tengo`() {
        val result = EntryValidatorHeuristics.check("tengo que ir al banco esta tarde")
        assertNotNull(result)
        assertTrue(result!!.isValid)
        assertEquals(0.9f, result.confidence, 0.001f)
    }

    @Test
    fun `accepts personal speech with necesito`() {
        val result = EntryValidatorHeuristics.check("necesito comprar leche y pan")
        assertNotNull(result)
        assertTrue(result!!.isValid)
    }

    @Test
    fun `accepts personal speech with hay que`() {
        val result = EntryValidatorHeuristics.check("hay que revisar el presupuesto del proyecto")
        assertNotNull(result)
        assertTrue(result!!.isValid)
    }

    @Test
    fun `accepts English personal speech`() {
        val result = EntryValidatorHeuristics.check("I need to call the dentist tomorrow morning")
        assertNotNull(result)
        assertTrue(result!!.isValid)
    }

    @Test
    fun `accepts speech with remind me`() {
        val result = EntryValidatorHeuristics.check("remind me to buy groceries after work")
        assertNotNull(result)
        assertTrue(result!!.isValid)
    }

    // ── Ambiguous cases ──────────────────────────────────────────

    @Test
    fun `returns null for ambiguous text`() {
        val result = EntryValidatorHeuristics.check("el coche rojo esta estacionado afuera")
        assertNull(result)
    }

    @Test
    fun `returns null for neutral statement`() {
        val result = EntryValidatorHeuristics.check("hoy llovio bastante durante la tarde")
        assertNull(result)
    }

    // ── Case insensitivity ───────────────────────────────────────

    @Test
    fun `detection is case insensitive`() {
        val result = EntryValidatorHeuristics.check("NECESITO LLAMAR AL DOCTOR")
        assertNotNull(result)
        assertTrue(result!!.isValid)
    }
}
