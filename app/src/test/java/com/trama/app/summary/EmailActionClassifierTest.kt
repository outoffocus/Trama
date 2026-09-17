package com.trama.app.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailActionClassifierTest {
    @Test
    fun `recognizes email wording and address`() {
        assertTrue(EmailActionClassifier.isEmail("Enviar correo al gestor"))
        assertTrue(EmailActionClassifier.isEmail("Escribir a ana@example.com"))
        assertEquals("ana@example.com", EmailActionClassifier.recipient("Para ana@example.com"))
    }

    @Test
    fun `generic messages are not forced into Gmail`() {
        assertFalse(EmailActionClassifier.isEmail("Enviar mensaje a Ana"))
        assertFalse(EmailActionClassifier.isEmail("Mandar el paquete"))
    }
}
