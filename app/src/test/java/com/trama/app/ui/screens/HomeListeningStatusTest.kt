package com.trama.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeListeningStatusTest {
    @Test
    fun `active phone listening is never labelled disabled`() {
        assertEquals(
            "Escuchando",
            homeListeningLabel(
                showDetails = false,
                watchActive = false,
                serviceRunning = true,
                triggerRecognized = false,
                asrStatus = "sin datos",
                watchStatus = ""
            )
        )
    }

    @Test
    fun `inactive listening is labelled disabled`() {
        assertEquals(
            "Escucha desactivada",
            homeListeningLabel(false, false, false, false, "sin datos", "")
        )
    }

    @Test
    fun `active watch has an explicit fallback label`() {
        assertEquals(
            "Escucha en reloj",
            homeListeningLabel(false, true, false, false, "", "")
        )
    }

    @Test
    fun `detail setting exposes a meaningful runtime status`() {
        assertEquals(
            "Esperando palabra clave",
            homeListeningLabel(true, false, true, false, "esperando palabra clave", "")
        )
    }

    @Test
    fun `recognized trigger overrides hidden diagnostic details while capture is active`() {
        assertEquals(
            "Palabra clave reconocida",
            homeListeningLabel(false, false, true, true, "esperando palabra clave", "")
        )
    }
}
