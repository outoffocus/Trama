package com.trama.app.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionTextNormalizerTest {
    @Test
    fun `removes conversation before and after a commitment`() {
        val text = "Llevábamos un rato hablando de las vacaciones y tenemos que llamar a Pedro mañana. Después seguimos hablando del hotel"

        assertEquals("Llamar a Pedro mañana", ActionTextNormalizer.focus(text))
    }

    @Test
    fun `removes unpunctuated trailing conversation`() {
        val text = "Estábamos comentando el presupuesto tengo que enviar el informe a Marta y después seguimos hablando del partido"

        assertEquals("Enviar el informe a Marta", ActionTextNormalizer.focus(text))
    }

    @Test
    fun `keeps context required to execute the action`() {
        val text = "Hoy vi a Elena y tengo que llamarla mañana para confirmar la reserva del restaurante"

        assertEquals(
            "Llamarla mañana para confirmar la reserva del restaurante",
            ActionTextNormalizer.focus(text)
        )
    }

    @Test
    fun `preserves dots inside names and abbreviations`() {
        assertEquals(
            "Llamar al Dr. Pérez mañana",
            ActionTextNormalizer.focus("Tenemos que llamar al Dr. Pérez mañana")
        )
    }

    @Test
    fun `caps pathological model copies without cutting a word`() {
        val focused = ActionTextNormalizer.focus("Llamar a Pedro para " + "explicarle el presupuesto ".repeat(30))

        assertTrue(focused.length <= 240)
        assertTrue(!focused.endsWith(" "))
    }
}
