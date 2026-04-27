package com.trama.app.summary

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class ActionItemProcessorTest {

    private lateinit var processor: ActionItemProcessor

    @Before
    fun setUp() {
        val ctx = mockk<Context>(relaxed = true)
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { ctx.getSharedPreferences(any(), any()) } returns prefs
        // Make SharedPreferences.getString return the default argument so that
        // PromptTemplateStore falls back to the built-in template.
        every { prefs.getString(any(), any()) } answers { secondArg() }
        processor = ActionItemProcessor(ctx)
    }

    @Test
    fun `inferActionType detects common action kinds`() {
        assertEquals("CALL", callPrivate<String>("inferActionType", "tengo que llamar al dentista"))
        assertEquals("BUY", callPrivate<String>("inferActionType", "necesito comprar leche"))
        assertEquals("SEND", callPrivate<String>("inferActionType", "hay que enviar el correo"))
        assertEquals("EVENT", callPrivate<String>("inferActionType", "tengo cita con el medico"))
        assertEquals("REVIEW", callPrivate<String>("inferActionType", "debo revisar el contrato"))
        assertEquals("TALK_TO", callPrivate<String>("inferActionType", "hablar con Pedro"))
        assertEquals("GENERIC", callPrivate<String>("inferActionType", "ordenar el escritorio"))
    }

    @Test
    fun `validateActionType normalizes invalid values to generic`() {
        assertEquals("CALL", callPrivate<String>("validateActionType", "call"))
        assertEquals("GENERIC", callPrivate<String>("validateActionType", "delete"))
        assertEquals("GENERIC", callPrivate<String>("validateActionType", ""))
    }

    @Test
    fun `validatePriority normalizes invalid values to normal`() {
        assertEquals("URGENT", callPrivate<String>("validatePriority", "urgent"))
        assertEquals("HIGH", callPrivate<String>("validatePriority", "HIGH"))
        assertEquals("NORMAL", callPrivate<String>("validatePriority", "critical"))
        assertEquals("NORMAL", callPrivate<String>("validatePriority", ""))
    }

    @Test
    fun `parseDateString parses yyyy mm dd and rejects invalid`() {
        val millis = callPrivate<Long?>("parseDateString", "2026-04-08")
        assertNotNull(millis)
        val formatted = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(millis!!)
        assertEquals("2026-04-08", formatted)

        assertNull(callPrivate<Long?>("parseDateString", null))
        assertNull(callPrivate<Long?>("parseDateString", "not-a-date"))
    }

    @Test
    fun `event entries can be accepted without an explicit action verb`() {
        val result = callPrivate<ActionItemProcessor.ProcessingResult>(
            "buildProcessingResult",
            "Cita con el medico mañana",
            "EVENT",
            null,
            "NORMAL",
            0.82f,
            true,
            null
        )

        assertTrue(result.isActionable)
        assertEquals("EVENT", result.actionType)
        assertEquals(0.82f, result.confidence, 0.001f)
    }

    @Test
    fun `recoger tasks are accepted as actionable`() {
        val result = callPrivate<ActionItemProcessor.ProcessingResult>(
            "buildProcessingResult",
            "Recoger el coche en el taller mañana",
            "GENERIC",
            null,
            "NORMAL",
            0.82f,
            true,
            null
        )

        assertTrue(result.isActionable)
        assertEquals(0.82f, result.confidence, 0.001f)
    }

    @Test
    fun `ir tasks are accepted as actionable`() {
        val result = callPrivate<ActionItemProcessor.ProcessingResult>(
            "buildProcessingResult",
            "Mañana tengo que ir a casa de los padres de Lena",
            "GENERIC",
            "2026-04-26",
            "NORMAL",
            0.82f,
            true,
            null
        )

        assertTrue(result.isActionable)
        assertEquals(0.82f, result.confidence, 0.001f)
        assertNotNull(result.dueDate)
    }

    @Test
    fun `display trigger is added to cleaned task text`() {
        val result = callPrivate<ActionItemProcessor.ProcessingResult>(
            "buildProcessingResult",
            "Recoger el coche en el taller mañana",
            "GENERIC",
            null,
            "NORMAL",
            0.82f,
            true,
            "Tengo que"
        )

        assertTrue(result.isActionable)
        assertEquals("Tengo que recoger el coche en el taller mañana", result.cleanText)
    }

    @Test
    fun `generic verbless fragments are still rejected`() {
        val result = callPrivate<ActionItemProcessor.ProcessingResult>(
            "buildProcessingResult",
            "Contrato de Marta mañana",
            "GENERIC",
            null,
            "NORMAL",
            0.82f,
            true,
            null
        )

        assertTrue(!result.isActionable)
        assertEquals(0.29f, result.confidence, 0.001f)
    }

    @Test
    fun `conversation noise examples are rejected even if model marks actionable`() {
        val phrases = listOf(
            "No quería verla ahí. Ahí no escucho",
            "Voy a hablar como sale",
            "¿Te asiste esto?",
            "¿No es barato?"
        )

        for (phrase in phrases) {
            val result = callPrivate<ActionItemProcessor.ProcessingResult>(
                "buildProcessingResult",
                phrase,
                "GENERIC",
                null,
                "NORMAL",
                0.9f,
                true,
                null
            )

            assertTrue("Expected '$phrase' to be rejected", !result.isActionable)
            assertEquals(0.29f, result.confidence, 0.001f)
        }
    }

    @Test
    fun `buildPrompt embeds the original note and output contract`() {
        val prompt = callPrivate<String>(
            "buildPrompt",
            "recordar llamar a Juan",  // originalText
            "recordar llamar a Juan",  // normalizedInput
            ""                          // recentContext
        )
        assertTrue(prompt.contains("recordar llamar a Juan"))
        assertTrue(prompt.contains("\"cleanText\""))
        assertTrue(prompt.contains("\"actionType\""))
        assertTrue(prompt.contains("\"priority\""))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> callPrivate(name: String, vararg args: Any?): T {
        val method = ActionItemProcessor::class.java.declaredMethods.first {
            it.name == name && it.parameterCount == args.size
        }
        method.isAccessible = true
        return method.invoke(processor, *args) as T
    }
}
