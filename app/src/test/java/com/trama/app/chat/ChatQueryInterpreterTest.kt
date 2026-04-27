package com.trama.app.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ChatQueryInterpreterTest {

    private val madridZone = TimeZone.getTimeZone("Europe/Madrid")
    private val nowMillis = Calendar.getInstance(madridZone).apply {
        set(2026, Calendar.APRIL, 25, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val interpreter = ChatQueryInterpreter(nowProvider = { nowMillis })

    @Test
    fun `interprets this tuesday as day summary query`() {
        val query = interpreter.interpret("¿Qué hice este martes?")

        assertEquals(ChatIntent.DAY_SUMMARY, query.intent)
        assertTrue(query.dateRange != null)
        assertEquals("Martes 21 de abril 2026", query.dateRange?.label)
    }

    @Test
    fun `interprets place comparison query`() {
        val query = interpreter.interpret("¿Estuve en casa o en la oficina?")

        assertEquals(ChatIntent.PLACE_PRESENCE, query.intent)
        assertEquals(listOf("casa", "oficina"), query.placeTerms)
    }

    @Test
    fun `interprets place duration query`() {
        val query = interpreter.interpret("¿Cuánto tiempo estuve en la oficina?")

        assertEquals(ChatIntent.PLACE_DURATION, query.intent)
        assertEquals(listOf("oficina"), query.placeTerms.map { it.removePrefix("la ").trim() })
    }

    @Test
    fun `interprets weekly comparison query`() {
        val query = interpreter.interpret("¿Dónde estuve más tiempo esta semana, en casa o en la oficina?")

        assertEquals(ChatIntent.PLACE_DURATION, query.intent)
        assertEquals("esta semana", query.dateRange?.label)
        assertEquals(listOf("casa", "oficina"), query.placeTerms)
    }

    @Test
    fun `interprets order query between places`() {
        val query = interpreter.interpret("¿Fui antes a casa o a la oficina?")

        assertEquals(ChatIntent.PLACE_ORDER, query.intent)
        assertEquals(listOf("casa", "oficina"), query.placeTerms)
    }

    @Test
    fun `interprets first place query`() {
        val query = interpreter.interpret("¿Cuál fue el primer sitio en el que estuve este martes?")

        assertEquals(ChatIntent.FIRST_PLACE, query.intent)
        assertEquals("Martes 21 de abril 2026", query.dateRange?.label)
    }

    @Test
    fun `interprets last place query`() {
        val query = interpreter.interpret("¿Cuál fue el último lugar en el que estuve este martes?")

        assertEquals(ChatIntent.LAST_PLACE, query.intent)
        assertEquals("Martes 21 de abril 2026", query.dateRange?.label)
    }

    @Test
    fun `interprets after place query`() {
        val query = interpreter.interpret("¿Dónde estuve después de casa?")

        assertEquals(ChatIntent.PLACE_AFTER, query.intent)
        assertEquals(listOf("casa"), query.placeTerms)
    }

    @Test
    fun `interprets day places query`() {
        val query = interpreter.interpret("¿Dónde estuve este martes?")

        assertEquals(ChatIntent.DAY_PLACES, query.intent)
        assertEquals("Martes 21 de abril 2026", query.dateRange?.label)
    }

    @Test
    fun `interprets completed tasks for month`() {
        val query = interpreter.interpret("¿Qué tareas completé en marzo?")

        assertEquals(ChatIntent.COMPLETED_TASKS, query.intent)
        assertEquals("Marzo 2026", query.dateRange?.label)
    }

    @Test
    fun `interprets completed tasks for year`() {
        val query = interpreter.interpret("¿Qué tareas completé en 2025?")

        assertEquals(ChatIntent.COMPLETED_TASKS, query.intent)
        assertEquals("2025", query.dateRange?.label)
    }

    @Test
    fun `interprets explicit date with year`() {
        val query = interpreter.interpret("¿Qué hice el 3 de mayo de 2024?")

        assertEquals(ChatIntent.DAY_SUMMARY, query.intent)
        assertEquals("Viernes 3 de mayo 2024", query.dateRange?.label)
    }
}
