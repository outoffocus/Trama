package com.mydiary.app.summary

import com.mydiary.shared.model.EntryActionType
import com.mydiary.shared.model.EntryPriority
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * Tests for ActionItemProcessor.processWithRules() — the rule-based fallback
 * that extracts action type, clean text, priority, and due date from Spanish text.
 */
class ActionItemProcessorTest {

    private lateinit var processor: ActionItemProcessor

    @Before
    fun setUp() {
        // processWithRules is public and does not use Context
        // We use mockk to satisfy the constructor, but the method under test is pure logic
        processor = ActionItemProcessor(mockk(relaxed = true))
    }

    // ── Action Type Detection ──

    @Test
    fun `detecta tipo CALL para llamar`() {
        val result = processor.processWithRules("tengo que llamar al dentista")
        assertEquals(EntryActionType.CALL, result.actionType)
    }

    @Test
    fun `detecta tipo CALL para telefonear`() {
        val result = processor.processWithRules("tengo que telefonear a Maria")
        assertEquals(EntryActionType.CALL, result.actionType)
    }

    @Test
    fun `detecta tipo BUY para comprar`() {
        val result = processor.processWithRules("necesito comprar leche y pan")
        assertEquals(EntryActionType.BUY, result.actionType)
    }

    @Test
    fun `detecta tipo SEND para enviar`() {
        val result = processor.processWithRules("tengo que enviar el correo a Juan")
        assertEquals(EntryActionType.SEND, result.actionType)
    }

    @Test
    fun `detecta tipo SEND para mensaje`() {
        val result = processor.processWithRules("necesito mandar un mensaje a Pedro")
        assertEquals(EntryActionType.SEND, result.actionType)
    }

    @Test
    fun `detecta tipo EVENT para cita`() {
        val result = processor.processWithRules("tengo cita con el doctor")
        assertEquals(EntryActionType.EVENT, result.actionType)
    }

    @Test
    fun `detecta tipo EVENT para reunion`() {
        val result = processor.processWithRules("hay una reunión de equipo a las 10")
        assertEquals(EntryActionType.EVENT, result.actionType)
    }

    @Test
    fun `detecta tipo EVENT para reserva`() {
        val result = processor.processWithRules("hay que reservar mesa en el restaurante")
        assertEquals(EntryActionType.EVENT, result.actionType)
    }

    @Test
    fun `detecta tipo REVIEW para revisar`() {
        val result = processor.processWithRules("tengo que revisar el informe")
        assertEquals(EntryActionType.REVIEW, result.actionType)
    }

    @Test
    fun `detecta tipo REVIEW para mirar`() {
        val result = processor.processWithRules("necesito mirar las facturas")
        assertEquals(EntryActionType.REVIEW, result.actionType)
    }

    @Test
    fun `detecta tipo REVIEW para buscar`() {
        val result = processor.processWithRules("hay que buscar un hotel para el viaje")
        assertEquals(EntryActionType.REVIEW, result.actionType)
    }

    @Test
    fun `detecta tipo TALK_TO para hablar con`() {
        val result = processor.processWithRules("necesito hablar con mi jefe")
        assertEquals(EntryActionType.TALK_TO, result.actionType)
    }

    @Test
    fun `detecta tipo TALK_TO para decirle`() {
        val result = processor.processWithRules("tengo que decirle a Luis lo del proyecto")
        assertEquals(EntryActionType.TALK_TO, result.actionType)
    }

    @Test
    fun `detecta tipo GENERIC para texto sin accion especifica`() {
        val result = processor.processWithRules("hacer la maleta para el viaje")
        assertEquals(EntryActionType.GENERIC, result.actionType)
    }

    // ── Clean Text (trigger removal) ──

    @Test
    fun `elimina trigger tengo que`() {
        val result = processor.processWithRules("tengo que llamar al dentista")
        assertEquals("Llamar al dentista", result.cleanText)
    }

    @Test
    fun `elimina trigger hay que`() {
        val result = processor.processWithRules("hay que comprar pan")
        assertEquals("Comprar pan", result.cleanText)
    }

    @Test
    fun `elimina trigger deberia`() {
        val result = processor.processWithRules("debería revisar el informe")
        assertEquals("Revisar el informe", result.cleanText)
    }

    @Test
    fun `elimina trigger necesito`() {
        val result = processor.processWithRules("necesito hablar con mi jefe")
        assertEquals("Hablar con mi jefe", result.cleanText)
    }

    @Test
    fun `elimina trigger recordar`() {
        val result = processor.processWithRules("recordar ir al banco")
        assertEquals("Ir al banco", result.cleanText)
    }

    @Test
    fun `elimina trigger no olvidar`() {
        val result = processor.processWithRules("no olvidar comprar flores")
        assertEquals("Comprar flores", result.cleanText)
    }

    @Test
    fun `capitaliza primera letra`() {
        val result = processor.processWithRules("tengo que enviar el paquete")
        assertTrue(result.cleanText.first().isUpperCase())
    }

    @Test
    fun `no elimina trigger si no esta al inicio`() {
        val result = processor.processWithRules("Comprar lo que necesito para la cena")
        // "necesito" is not at the start, so text stays mostly as-is
        assertEquals("Comprar lo que necesito para la cena", result.cleanText)
    }

    // ── Priority Detection ──

    @Test
    fun `detecta prioridad URGENT para urgente`() {
        val result = processor.processWithRules("tengo que llamar al banco es urgente")
        assertEquals(EntryPriority.URGENT, result.priority)
    }

    @Test
    fun `detecta prioridad URGENT para ahora`() {
        val result = processor.processWithRules("necesito hacerlo ahora mismo")
        assertEquals(EntryPriority.URGENT, result.priority)
    }

    @Test
    fun `detecta prioridad URGENT para cuanto antes`() {
        val result = processor.processWithRules("hay que terminarlo cuanto antes")
        assertEquals(EntryPriority.URGENT, result.priority)
    }

    @Test
    fun `detecta prioridad HIGH para importante`() {
        val result = processor.processWithRules("es importante revisar el contrato")
        assertEquals(EntryPriority.HIGH, result.priority)
    }

    @Test
    fun `detecta prioridad HIGH para no olvidar`() {
        val result = processor.processWithRules("no olvidar la cita del martes")
        assertEquals(EntryPriority.HIGH, result.priority)
    }

    @Test
    fun `detecta prioridad LOW para cuando pueda`() {
        val result = processor.processWithRules("cuando pueda ordenar el garaje")
        assertEquals(EntryPriority.LOW, result.priority)
    }

    @Test
    fun `detecta prioridad NORMAL por defecto`() {
        val result = processor.processWithRules("tengo que comprar leche")
        assertEquals(EntryPriority.NORMAL, result.priority)
    }

    // ── Due Date Detection ──

    @Test
    fun `detecta fecha hoy`() {
        val result = processor.processWithRules("tengo que hacerlo hoy")
        assertNotNull(result.dueDate)
        val cal = Calendar.getInstance()
        val dueCal = Calendar.getInstance().apply { timeInMillis = result.dueDate!! }
        assertEquals(cal.get(Calendar.DAY_OF_YEAR), dueCal.get(Calendar.DAY_OF_YEAR))
    }

    @Test
    fun `detecta fecha manana`() {
        val result = processor.processWithRules("tengo cita con el doctor mañana")
        assertNotNull(result.dueDate)
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val dueCal = Calendar.getInstance().apply { timeInMillis = result.dueDate!! }
        assertEquals(cal.get(Calendar.DAY_OF_YEAR), dueCal.get(Calendar.DAY_OF_YEAR))
    }

    @Test
    fun `detecta dia de la semana lunes`() {
        val result = processor.processWithRules("hay que hacerlo el lunes")
        assertNotNull(result.dueDate)
        val dueCal = Calendar.getInstance().apply { timeInMillis = result.dueDate!! }
        assertEquals(Calendar.MONDAY, dueCal.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun `detecta dia de la semana miercoles con acento`() {
        val result = processor.processWithRules("tengo reunión el miércoles")
        assertNotNull(result.dueDate)
        val dueCal = Calendar.getInstance().apply { timeInMillis = result.dueDate!! }
        assertEquals(Calendar.WEDNESDAY, dueCal.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun `detecta dia de la semana miercoles sin acento`() {
        val result = processor.processWithRules("tengo reunión el miercoles")
        assertNotNull(result.dueDate)
        val dueCal = Calendar.getInstance().apply { timeInMillis = result.dueDate!! }
        assertEquals(Calendar.WEDNESDAY, dueCal.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun `no detecta fecha cuando no hay ninguna`() {
        val result = processor.processWithRules("tengo que comprar leche")
        assertNull(result.dueDate)
    }

    // ── Confidence ──

    @Test
    fun `confianza es 0_6 para procesamiento local`() {
        val result = processor.processWithRules("tengo que llamar al dentista")
        assertEquals(0.6f, result.confidence, 0.001f)
    }
}
