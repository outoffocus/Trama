package com.trama.app.capture

import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryContentKind
import com.trama.shared.model.EntryStatus
import com.trama.shared.model.Source
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveManualCaptureTest {
    @Test
    fun `trims and persists the original capture with its timestamp`() = runTest {
        val saved = mutableListOf<DiaryEntry>()
        val useCase = SaveManualCapture(
            insert = { entry -> saved += entry; 42L },
            now = { 1234L }
        )

        val id = useCase("  llamar al taller  ")

        assertEquals(42L, id)
        assertEquals("llamar al taller", saved.single().text)
        assertEquals("llamar al taller", saved.single().cleanText)
        assertEquals(1234L, saved.single().createdAt)
        assertEquals(Source.PHONE, saved.single().source)
        assertTrue(saved.single().isManual)
        assertEquals(EntryContentKind.MEMORY, saved.single().contentKind)
        assertEquals(EntryStatus.SAVED, saved.single().status)
        assertTrue(saved.single().sourceCaptureId?.isNotBlank() == true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects an empty capture`() = runTest {
        SaveManualCapture(insert = { 1L })("   ")
    }
}
