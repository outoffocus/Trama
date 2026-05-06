package com.trama.app.summary

import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryActionType
import com.trama.shared.model.EntryStatus
import com.trama.shared.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EntryActionBridgeTest {

    @Test
    fun `build returns quick actions for every supported card action type`() {
        val expected = mapOf(
            EntryActionType.CALL to ActionType.CALL,
            EntryActionType.SEND to ActionType.MESSAGE,
            EntryActionType.TALK_TO to ActionType.MESSAGE,
            EntryActionType.EVENT to ActionType.CALENDAR_EVENT,
            EntryActionType.BUY to ActionType.TODO,
            EntryActionType.REVIEW to ActionType.TODO,
            EntryActionType.GENERIC to ActionType.TODO
        )

        expected.forEach { (entryActionType, expectedActionType) ->
            val quickAction = EntryActionBridge.build(entry(actionType = entryActionType))

            assertNotNull("Missing quick action for $entryActionType", quickAction)
            assertEquals(expectedActionType, quickAction!!.action.type)
        }
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
