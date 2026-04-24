package com.trama.app.summary

import com.trama.shared.model.EntryActionType
import com.trama.shared.model.EntryPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ManualActionSuggestionExtractorTest {

    @Test
    fun `extract builds action suggestions from reminder text`() {
        val suggestions = ManualActionSuggestionExtractor.extract(
            "Recordar llamar al dentista y comprar leche mañana"
        )

        assertEquals(2, suggestions.size)
        assertEquals(EntryActionType.CALL, suggestions[0].actionType)
        assertEquals("Llamar al dentista", suggestions[0].text)
        assertEquals(EntryActionType.BUY, suggestions[1].actionType)
        assertEquals("Comprar leche mañana", suggestions[1].text)
        assertNotNull(suggestions[1].dueDate)
    }

    @Test
    fun `extract detects priority and weekday`() {
        val suggestions = ManualActionSuggestionExtractor.extract(
            "Tengo que enviar el informe importante el lunes"
        )

        assertEquals(1, suggestions.size)
        assertEquals(EntryPriority.HIGH, suggestions[0].priority)
        val cal = Calendar.getInstance().apply { timeInMillis = suggestions[0].dueDate!! }
        assertEquals(Calendar.MONDAY, cal.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun `extract splits comma separated actions automatically`() {
        val suggestions = ManualActionSuggestionExtractor.extract(
            "Recordar llamar al dentista, comprar leche y pagar el recibo"
        )

        assertEquals(3, suggestions.size)
        assertEquals("Llamar al dentista", suggestions[0].text)
        assertEquals("Comprar leche", suggestions[1].text)
        assertEquals("Pagar el recibo", suggestions[2].text)
    }

    @Test
    fun `extract splits actions joined by luego`() {
        val suggestions = ManualActionSuggestionExtractor.extract(
            "Tengo que revisar el presupuesto luego escribir a Marta"
        )

        assertEquals(2, suggestions.size)
        assertEquals("Tengo que revisar el presupuesto", suggestions[0].text)
        assertEquals("Tengo que escribir a Marta", suggestions[1].text)
    }

    @Test
    fun `extract accepts recoger after tengo que trigger`() {
        val suggestions = ManualActionSuggestionExtractor.extract(
            "Tengo que recoger el coche en el taller mañana"
        )

        assertEquals(1, suggestions.size)
        assertEquals("Tengo que recoger el coche en el taller mañana", suggestions[0].text)
        assertNotNull(suggestions[0].dueDate)
    }

    @Test
    fun `extract returns empty for blank text`() {
        assertTrue(ManualActionSuggestionExtractor.extract("   ").isEmpty())
    }
}
