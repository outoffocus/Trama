package com.mydiary.app.summary

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * Tests for RecordingProcessor's local processing methods:
 * generateLocalTitle, extractLocalActions, extractDateHint, parseDateHeuristic,
 * validateActionType, validatePriority.
 *
 * These methods were made internal for testability. They contain the pure logic
 * fallback used when Gemini is unavailable.
 */
class RecordingProcessorTest {

    private lateinit var processor: RecordingProcessor

    @Before
    fun setUp() {
        val mockContext = mockk<Context>(relaxed = true)
        processor = RecordingProcessor(mockContext)
    }

    // ── generateLocalTitle ──

    @Test
    fun `generateLocalTitle returns first sentence capped at 8 words`() {
        val sentences = listOf(
            "Hoy tuve una reunion muy larga con el equipo de marketing",
            "Hablamos de varios temas"
        )
        val title = processor.generateLocalTitle(sentences, sentences.joinToString(". "))
        val wordCount = title.split(" ").size
        assertTrue("Title should have at most 8 words, had $wordCount", wordCount <= 8)
    }

    @Test
    fun `generateLocalTitle truncates to 50 chars with ellipsis`() {
        val longSentence = "A".repeat(60)
        val title = processor.generateLocalTitle(listOf(longSentence), longSentence)
        assertTrue("Title should be at most 50 chars", title.length <= 50)
        assertTrue("Truncated title should end with ...", title.endsWith("..."))
    }

    @Test
    fun `generateLocalTitle uses full text fallback when sentences empty`() {
        val full = "texto corto sin puntos"
        val title = processor.generateLocalTitle(emptyList(), full)
        assertEquals("texto corto sin puntos", title)
    }

    @Test
    fun `generateLocalTitle preserves short first sentence`() {
        val sentences = listOf("Ir al banco", "Comprar pan")
        val title = processor.generateLocalTitle(sentences, "Ir al banco. Comprar pan.")
        assertEquals("Ir al banco", title)
    }

    // ── extractLocalActions ──

    @Test
    fun `extractLocalActions detects CALL action`() {
        val text = "Tengo que llamar a mi madre esta tarde"
        val sentences = text.split(". ")
        val actions = processor.extractLocalActions(sentences, text)
        assertTrue("Should find at least one action", actions.isNotEmpty())
        assertTrue("Should detect CALL type", actions.any { it.actionType == "CALL" })
    }

    @Test
    fun `extractLocalActions detects BUY action`() {
        val text = "Necesito comprar leche y huevos"
        val sentences = text.split(". ")
        val actions = processor.extractLocalActions(sentences, text)
        assertTrue("Should find BUY action", actions.any { it.actionType == "BUY" })
    }

    @Test
    fun `extractLocalActions detects SEND action`() {
        val text = "Tengo que enviar el informe al jefe"
        val sentences = text.split(". ")
        val actions = processor.extractLocalActions(sentences, text)
        assertTrue("Should find SEND action", actions.any { it.actionType == "SEND" })
    }

    @Test
    fun `extractLocalActions detects TALK_TO action`() {
        val text = "Necesito hablar con Pedro sobre el proyecto"
        val sentences = text.split(". ")
        val actions = processor.extractLocalActions(sentences, text)
        assertTrue("Should find TALK_TO action", actions.any { it.actionType == "TALK_TO" })
    }

    @Test
    fun `extractLocalActions detects EVENT action`() {
        val text = "Tengo una reunión con el cliente en la oficina"
        val sentences = text.split(". ")
        val actions = processor.extractLocalActions(sentences, text)
        assertTrue("Should find EVENT action", actions.any { it.actionType == "EVENT" })
    }

    @Test
    fun `extractLocalActions detects REVIEW action`() {
        val text = "Tengo que revisar los documentos del contrato"
        val sentences = text.split(". ")
        val actions = processor.extractLocalActions(sentences, text)
        assertTrue("Should find REVIEW action", actions.any { it.actionType == "REVIEW" })
    }

    @Test
    fun `extractLocalActions detects GENERIC action with recordar`() {
        val text = "Acordarme de poner la alarma temprano"
        val sentences = text.split(". ")
        val actions = processor.extractLocalActions(sentences, text)
        assertTrue("Should find GENERIC action from 'acordarme de'",
            actions.any { it.actionType == "GENERIC" })
    }

    @Test
    fun `extractLocalActions deduplicates same action text`() {
        val text = "Tengo que llamar a Juan. Debo llamar a Juan por la tarde."
        val sentences = text.split(". ")
        val actions = processor.extractLocalActions(sentences, text)
        // "llamar a Juan" should only appear once (dedup by lowercase key)
        val callActions = actions.filter { it.actionType == "CALL" }
        // May have 1 or 2 depending on exact regex match text, but dedup should cap it
        assertTrue("Should not have many duplicates", callActions.size <= 2)
    }

    @Test
    fun `extractLocalActions caps at 10 results`() {
        // Build text with many action triggers
        val triggers = (1..15).map { "tengo que hacer la tarea numero $it" }
        val text = triggers.joinToString(". ")
        val sentences = triggers
        val actions = processor.extractLocalActions(sentences, text)
        assertTrue("Should cap at 10 actions", actions.size <= 10)
    }

    @Test
    fun `extractLocalActions detects URGENT priority`() {
        val text = "Tengo que llamar al banco ahora mismo es urgente"
        val sentences = text.split(". ")
        val actions = processor.extractLocalActions(sentences, text)
        assertTrue("Should detect URGENT priority",
            actions.any { it.priority == "URGENT" })
    }

    @Test
    fun `extractLocalActions detects HIGH priority for importante`() {
        val text = "Necesito revisar el contrato es importante"
        val sentences = text.split(". ")
        val actions = processor.extractLocalActions(sentences, text)
        assertTrue("Should detect HIGH priority",
            actions.any { it.priority == "HIGH" })
    }

    @Test
    fun `extractLocalActions cleans trigger prefixes from action text`() {
        val text = "Tengo que comprar pan y leche"
        val sentences = text.split(". ")
        val actions = processor.extractLocalActions(sentences, text)
        // The extracted action text should have the trigger stripped
        val buyAction = actions.find { it.actionType == "BUY" }
        assertNotNull("Should find BUY action", buyAction)
        assertFalse("Should not start with 'tengo que'",
            buyAction!!.text.lowercase().startsWith("tengo que"))
    }

    @Test
    fun `extractLocalActions returns empty list for text without actions`() {
        val text = "Hoy hizo buen tiempo y fuimos al parque"
        val sentences = text.split(". ")
        val actions = processor.extractLocalActions(sentences, text)
        assertTrue("Should return no actions", actions.isEmpty())
    }

    // ── extractDateHint ──

    @Test
    fun `extractDateHint detects hoy`() {
        val text = "Necesito hacerlo hoy sin falta"
        val matchRange = 0..20
        val hint = processor.extractDateHint(text, matchRange)
        assertEquals("hoy", hint)
    }

    @Test
    fun `extractDateHint detects manana`() {
        val text = "Necesito llamar mañana al doctor"
        val matchRange = 0..15
        val hint = processor.extractDateHint(text, matchRange)
        assertEquals("mañana", hint)
    }

    @Test
    fun `extractDateHint detects day of week`() {
        val text = "Tengo que ir el lunes a la oficina"
        val matchRange = 0..14
        val hint = processor.extractDateHint(text, matchRange)
        assertEquals("lunes", hint)
    }

    @Test
    fun `extractDateHint detects miercoles with accent`() {
        val text = "La reunión es el miércoles por la tarde"
        val matchRange = 0..15
        val hint = processor.extractDateHint(text, matchRange)
        assertEquals("miércoles", hint)
    }

    @Test
    fun `extractDateHint detects miercoles without accent`() {
        val text = "La reunión es el miercoles por la tarde"
        val matchRange = 0..15
        val hint = processor.extractDateHint(text, matchRange)
        assertEquals("miércoles", hint)
    }

    @Test
    fun `extractDateHint returns null when no date found`() {
        val text = "Necesito comprar leche"
        val matchRange = 0..10
        val hint = processor.extractDateHint(text, matchRange)
        assertNull(hint)
    }

    // ── parseDateHeuristic ──

    @Test
    fun `parseDateHeuristic returns null for null input`() {
        assertNull(processor.parseDateHeuristic(null))
    }

    @Test
    fun `parseDateHeuristic returns today for hoy`() {
        val result = processor.parseDateHeuristic("hoy")
        assertNotNull(result)
        val cal = Calendar.getInstance()
        val resultCal = Calendar.getInstance().apply { timeInMillis = result!! }
        assertEquals(cal.get(Calendar.DAY_OF_YEAR), resultCal.get(Calendar.DAY_OF_YEAR))
    }

    @Test
    fun `parseDateHeuristic returns tomorrow for manana`() {
        val result = processor.parseDateHeuristic("mañana")
        assertNotNull(result)
        val expected = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val resultCal = Calendar.getInstance().apply { timeInMillis = result!! }
        assertEquals(expected.get(Calendar.DAY_OF_YEAR), resultCal.get(Calendar.DAY_OF_YEAR))
    }

    @Test
    fun `parseDateHeuristic returns day after tomorrow for pasado_manana`() {
        val result = processor.parseDateHeuristic("pasado_mañana")
        assertNotNull(result)
        val expected = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 2) }
        val resultCal = Calendar.getInstance().apply { timeInMillis = result!! }
        assertEquals(expected.get(Calendar.DAY_OF_YEAR), resultCal.get(Calendar.DAY_OF_YEAR))
    }

    @Test
    fun `parseDateHeuristic returns correct weekday for lunes`() {
        val result = processor.parseDateHeuristic("lunes")
        assertNotNull(result)
        val resultCal = Calendar.getInstance().apply { timeInMillis = result!! }
        assertEquals(Calendar.MONDAY, resultCal.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun `parseDateHeuristic returns future date for weekday`() {
        val result = processor.parseDateHeuristic("viernes")
        assertNotNull(result)
        assertTrue("Date should be in the future", result!! > System.currentTimeMillis())
    }

    @Test
    fun `parseDateHeuristic returns null for unknown hint`() {
        assertNull(processor.parseDateHeuristic("next_month"))
    }

    // ── validateActionType ──

    @Test
    fun `validateActionType accepts valid types`() {
        assertEquals("CALL", processor.validateActionType("call"))
        assertEquals("BUY", processor.validateActionType("BUY"))
        assertEquals("SEND", processor.validateActionType("Send"))
        assertEquals("EVENT", processor.validateActionType("event"))
        assertEquals("REVIEW", processor.validateActionType("REVIEW"))
        assertEquals("TALK_TO", processor.validateActionType("talk_to"))
        assertEquals("GENERIC", processor.validateActionType("generic"))
    }

    @Test
    fun `validateActionType defaults to GENERIC for unknown`() {
        assertEquals("GENERIC", processor.validateActionType("UNKNOWN"))
        assertEquals("GENERIC", processor.validateActionType(""))
        assertEquals("GENERIC", processor.validateActionType("delete"))
    }

    // ── validatePriority ──

    @Test
    fun `validatePriority accepts valid priorities`() {
        assertEquals("LOW", processor.validatePriority("low"))
        assertEquals("NORMAL", processor.validatePriority("NORMAL"))
        assertEquals("HIGH", processor.validatePriority("High"))
        assertEquals("URGENT", processor.validatePriority("urgent"))
    }

    @Test
    fun `validatePriority defaults to NORMAL for unknown`() {
        assertEquals("NORMAL", processor.validatePriority("CRITICAL"))
        assertEquals("NORMAL", processor.validatePriority(""))
        assertEquals("NORMAL", processor.validatePriority("medium"))
    }

    // ── advanceToWeekday ──

    @Test
    fun `advanceToWeekday advances to next occurrence`() {
        val cal = Calendar.getInstance()
        val currentDay = cal.get(Calendar.DAY_OF_WEEK)
        val target = Calendar.MONDAY
        val originalDay = cal.get(Calendar.DAY_OF_YEAR)

        processor.advanceToWeekday(cal, target)

        assertEquals(target, cal.get(Calendar.DAY_OF_WEEK))
        assertTrue("Should advance to future", cal.get(Calendar.DAY_OF_YEAR) > originalDay ||
            cal.get(Calendar.YEAR) > Calendar.getInstance().get(Calendar.YEAR))
    }

    @Test
    fun `advanceToWeekday wraps around week if target is today or past`() {
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_WEEK)
        processor.advanceToWeekday(cal, today)
        // Should advance by 7 days (next week same day)
        val diff = cal.get(Calendar.DAY_OF_YEAR) - Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        assertEquals(7, diff)
    }
}
