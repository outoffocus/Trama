package com.trama.app.chat

import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelChatMessageTest {

    @Test
    fun `missing model requests installation`() {
        val message = LocalModelChatMessage.forState(downloaded = false, enabled = true)

        assertTrue(message.contains("instala el modelo local"))
    }

    @Test
    fun `installed disabled model requests activation instead of installation`() {
        val message = LocalModelChatMessage.forState(downloaded = true, enabled = false)

        assertTrue(message.contains("ya está instalado"))
        assertTrue(message.contains("desactivado"))
    }

    @Test
    fun `runtime failure does not claim the model is missing`() {
        val message = LocalModelChatMessage.forState(downloaded = true, enabled = true)

        assertTrue(message.contains("instalado y activado"))
        assertTrue(message.contains("no ha podido iniciarse"))
    }
}
