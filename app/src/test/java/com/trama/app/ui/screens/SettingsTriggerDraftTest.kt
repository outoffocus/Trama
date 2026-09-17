package com.trama.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTriggerDraftTest {

    @Test
    fun `saving commits text still present in new phrase field`() {
        assertEquals(
            listOf("recuérdame", "recordar"),
            mergeTriggerDraft(listOf("recuérdame"), "  RECORDAR  ")
        )
    }

    @Test
    fun `saving does not duplicate equivalent accented phrase`() {
        assertEquals(
            listOf("recuérdame"),
            mergeTriggerDraft(listOf("recuérdame"), "recuerdame")
        )
    }
}
