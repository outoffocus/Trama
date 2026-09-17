package com.trama.app.summary

import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryActionType
import com.trama.shared.model.EntryStatus
import com.trama.shared.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EntryActionBridgeTest {

    @Test
    fun `every pending task receives its related external action`() {
        val expected = mapOf(
            EntryActionType.CALL to ActionType.REMINDER,
            EntryActionType.SEND to ActionType.MESSAGE,
            EntryActionType.TALK_TO to ActionType.NOTE,
            EntryActionType.EVENT to ActionType.CALENDAR_EVENT,
            EntryActionType.BUY to ActionType.NOTE,
            EntryActionType.REVIEW to ActionType.NOTE,
            EntryActionType.GENERIC to ActionType.NOTE
        )

        expected.forEach { (entryActionType, expectedActionType) ->
            val quickAction = EntryActionBridge.build(entry(actionType = entryActionType))

            assertNotNull("Missing quick action for $entryActionType", quickAction)
            assertEquals(expectedActionType, quickAction!!.action.type)
        }
    }

    @Test
    fun `undated call task opens Calendar scheduling`() {
        val quickAction = EntryActionBridge.build(entry(actionType = EntryActionType.CALL))

        assertEquals("Programar", quickAction?.label)
        assertEquals(ActionType.REMINDER, quickAction?.action?.type)
        assertEquals(null, quickAction?.action?.datetime)
    }

    @Test
    fun `dated call preserves its date for Calendar`() {
        val quickAction = EntryActionBridge.build(
            entry(actionType = EntryActionType.CALL).copy(dueDate = 1_800_000_000_000L)
        )

        assertEquals(ActionType.REMINDER, quickAction?.action?.type)
        assertNotNull(quickAction?.action?.datetime)
    }

    @Test
    fun `undated local tasks always offer Keep`() {
        assertEquals(ActionType.NOTE, EntryActionBridge.build(entry(actionType = EntryActionType.REVIEW))?.action?.type)
        assertEquals(ActionType.NOTE, EntryActionBridge.build(entry(actionType = EntryActionType.GENERIC))?.action?.type)
    }

    @Test
    fun `dated local task offers calendar handoff`() {
        val quickAction = EntryActionBridge.build(
            entry(actionType = EntryActionType.REVIEW).copy(dueDate = 1_800_000_000_000L)
        )

        assertEquals(ActionType.REMINDER, quickAction?.action?.type)
    }

    @Test
    fun `dated email keeps its direct Gmail action`() {
        val quickAction = EntryActionBridge.build(
            entry(actionType = EntryActionType.SEND).copy(
                text = "Enviar el correo a ana@example.com",
                cleanText = "Enviar correo a ana@example.com",
                dueDate = 1_800_000_000_000L
            )
        )

        assertEquals("Gmail", quickAction?.label)
        assertEquals(ActionType.MESSAGE, quickAction?.action?.type)
        assertEquals("ana@example.com", quickAction?.action?.contact)
    }

    @Test
    fun `non email send task keeps a generic send action`() {
        val quickAction = EntryActionBridge.build(entry(actionType = EntryActionType.SEND))

        assertEquals("Enviar", quickAction?.label)
        assertEquals(ActionType.MESSAGE, quickAction?.action?.type)
    }

    @Test
    fun `Keep note preserves cleaned task and original context`() {
        val quickAction = EntryActionBridge.build(
            entry(actionType = EntryActionType.GENERIC).copy(
                text = "Pues creo que tendría que enviarle un mensaje a Ana"
            )
        )

        assertEquals(
            "Enviar mensaje a Ana\n\nContexto original:\nPues creo que tendría que enviarle un mensaje a Ana",
            quickAction?.action?.description
        )
    }

    @Test
    fun `unconfirmed suggestion does not offer an external action`() {
        val suggested = entry(actionType = EntryActionType.BUY).copy(status = EntryStatus.SUGGESTED)

        assertNull(EntryActionBridge.build(suggested))
    }

    private fun entry(actionType: String) = DiaryEntry(
        text = "Enviar mensaje a Ana",
        keyword = "test",
        category = "nota",
        confidence = 0.9f,
        source = Source.PHONE,
        duration = 5,
        status = EntryStatus.PENDING,
        actionType = actionType,
        cleanText = "Enviar mensaje a Ana"
    )
}
