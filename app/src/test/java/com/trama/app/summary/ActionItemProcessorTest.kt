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
    fun `compound reminder is split into independent local suggestions`() {
        val suggestions = ManualActionSuggestionExtractor.extract(
            "debería hacer la compra mañana y además tengo que llamar a mi hermana para decirle que comemos el domingo con mis padres"
        )

        assertEquals(2, suggestions.size)
        assertEquals("Debería hacer la compra mañana", suggestions[0].text)
        assertEquals("BUY", suggestions[0].actionType)
        assertEquals("Llamar a mi hermana para decirle que comemos el domingo con mis padres", suggestions[1].text)
        assertEquals("CALL", suggestions[1].actionType)
    }

    @Test
    fun `heuristic supplemental actions recover extra action omitted by llm`() {
        val primary = callPrivate<ActionItemProcessor.ProcessingResult>(
            "buildProcessingResult",
            "Hacer la compra",
            "BUY",
            "2026-04-29",
            "NORMAL",
            0.9f,
            true,
            null
        )

        val extras = callPrivate<List<ActionItemProcessor.ProcessingResult>>(
            "buildHeuristicSupplementalActions",
            "debería hacer la compra mañana y además tengo que llamar a mi hermana para decirle que comemos el domingo con mis padres",
            primary,
            emptyList<ActionItemProcessor.ProcessingResult>()
        )

        assertEquals(1, extras.size)
        assertEquals("CALL", extras.first().actionType)
        assertTrue(extras.first().cleanText.contains("mi hermana"))
        assertTrue(extras.first().cleanText.contains("domingo"))
    }

    @Test
    fun `pending compound reminder splits shared pending trigger`() {
        val suggestions = ManualActionSuggestionExtractor.extract(
            "me quedó pendiente enviar un email a cecile y mover el tema de prevención"
        )

        assertEquals(2, suggestions.size)
        assertEquals("Enviar un email a cecile", suggestions[0].text)
        assertEquals("SEND", suggestions[0].actionType)
        assertEquals("Mover el tema de prevención", suggestions[1].text)
        assertEquals("GENERIC", suggestions[1].actionType)
    }

    @Test
    fun `heuristic supplemental actions recover moved topic omitted by llm`() {
        val primary = callPrivate<ActionItemProcessor.ProcessingResult>(
            "buildProcessingResult",
            "Enviar un email a Cecile",
            "SEND",
            null,
            "NORMAL",
            0.9f,
            true,
            null
        )

        val extras = callPrivate<List<ActionItemProcessor.ProcessingResult>>(
            "buildHeuristicSupplementalActions",
            "me quedó pendiente enviar un email a cecile y mover el tema de prevención",
            primary,
            emptyList<ActionItemProcessor.ProcessingResult>()
        )

        assertEquals(1, extras.size)
        assertEquals("GENERIC", extras.first().actionType)
        assertTrue(extras.first().cleanText.contains("Mover el tema"))
    }

    @Test
    fun `parseResult prefers actions array as canonical task list`() {
        val json = """
            {
              "kind":"TASK",
              "usefulnessScore":0.9,
              "actionabilityScore":0.9,
              "discardReason":null,
              "isActionable":true,
              "cleanText":"",
              "actionType":"GENERIC",
              "dueDate":null,
              "priority":"NORMAL",
              "confidence":0.8,
              "actions":[
                {"cleanText":"Enviar un email a Cecile","actionType":"SEND","dueDate":null,"priority":"NORMAL","confidence":0.86},
                {"cleanText":"Mover el tema de prevención","actionType":"GENERIC","dueDate":null,"priority":"NORMAL","confidence":0.84}
              ],
              "extraActions":[]
            }
        """.trimIndent()

        val outcome = callPrivate<ActionItemProcessor.LLMOutcome>(
            "parseResult",
            json,
            1.0f,
            "me quedó pendiente enviar un email a cecile y mover el tema de prevención"
        )

        assertEquals("Enviar un email a Cecile", outcome.primary.cleanText)
        assertEquals("SEND", outcome.primary.actionType)
        assertEquals(1, outcome.extras.size)
        assertEquals("Mover el tema de prevención", outcome.extras.first().cleanText)
    }

    @Test
    fun `parseResult keeps legacy cleanText plus extraActions response`() {
        val json = """
            {
              "kind":"TASK",
              "usefulnessScore":0.9,
              "actionabilityScore":0.9,
              "discardReason":null,
              "isActionable":true,
              "cleanText":"Enviar un email a Cecile",
              "actionType":"SEND",
              "dueDate":null,
              "priority":"NORMAL",
              "confidence":0.86,
              "extraActions":[
                {"cleanText":"Mover el tema de prevención","actionType":"GENERIC","dueDate":null,"priority":"NORMAL"}
              ]
            }
        """.trimIndent()

        val outcome = callPrivate<ActionItemProcessor.LLMOutcome>(
            "parseResult",
            json,
            1.0f,
            "me quedó pendiente enviar un email a cecile y mover el tema de prevención"
        )

        assertEquals("Enviar un email a Cecile", outcome.primary.cleanText)
        assertEquals(1, outcome.extras.size)
        assertEquals("Mover el tema de prevención", outcome.extras.first().cleanText)
    }

    @Test
    fun `parseResult trims conversational prefix from actionable clause`() {
        val json = """
            {
              "kind":"TASK",
              "usefulnessScore":0.9,
              "actionabilityScore":0.9,
              "discardReason":null,
              "isActionable":true,
              "cleanText":"Hoy estoy hablando con elena y tenemso que comprar una cafetera nueva",
              "actionType":"BUY",
              "dueDate":null,
              "priority":"NORMAL",
              "confidence":0.86,
              "actions":[],
              "extraActions":[]
            }
        """.trimIndent()

        val outcome = callPrivate<ActionItemProcessor.LLMOutcome>(
            "parseResult",
            json,
            1.0f,
            "hoy estuve hablando con elena y tenemos que comprar una cafetera nueva"
        )

        assertEquals("Comprar una cafetera nueva", outcome.primary.cleanText)
        assertTrue(outcome.primary.isActionable)
    }

    @Test
    fun `parseResult drops overlapping llm extras with typo variants`() {
        val json = """
            {
              "kind":"TASK",
              "usefulnessScore":0.9,
              "actionabilityScore":0.9,
              "discardReason":null,
              "isActionable":true,
              "cleanText":"Hoy estoy hablando con elena y tenemso que comprar una cafetera nueva",
              "actionType":"BUY",
              "dueDate":null,
              "priority":"NORMAL",
              "confidence":0.86,
              "actions":[
                {"cleanText":"Hoy estoy hablando con elena y tenemso que comprar una cafetera nueva","actionType":"BUY","dueDate":null,"priority":"NORMAL","confidence":0.86},
                {"cleanText":"tenés que comprar una cafetera nueva","actionType":"BUY","dueDate":null,"priority":"NORMAL","confidence":0.84}
              ],
              "extraActions":[]
            }
        """.trimIndent()

        val outcome = callPrivate<ActionItemProcessor.LLMOutcome>(
            "parseResult",
            json,
            1.0f,
            "hoy estuve hablando con elena y tenemos que comprar una cafetera nueva"
        )

        assertEquals("Comprar una cafetera nueva", outcome.primary.cleanText)
        assertEquals(0, outcome.extras.size)
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
        assertTrue(prompt.contains("\"actions\""))
        assertTrue(prompt.contains("\"cleanText\""))
        assertTrue(prompt.contains("\"actionType\""))
        assertTrue(prompt.contains("\"priority\""))
    }

    @Test
    fun `buildPrompt instructs model to resolve pronouns and shared context`() {
        val prompt = callPrivate<String>(
            "buildPrompt",
            "hoy hablé con Sadoth y mañana tengo que contestarle el mensaje. esta semana me tocó a mi pagar la piscina, la próxima semana le toca a Luis.",
            "hoy hablé con Sadoth y mañana tengo que contestarle el mensaje. esta semana me tocó a mi pagar la piscina, la próxima semana le toca a Luis.",
            ""
        )

        assertTrue(prompt.contains("resuelve referencias"))
        assertTrue(prompt.contains("Contestarle el mensaje a Sadoth"))
        assertTrue(prompt.contains("A Luis le toca pagar la piscina"))
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
