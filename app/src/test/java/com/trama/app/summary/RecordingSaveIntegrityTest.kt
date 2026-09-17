package com.trama.app.summary

import android.content.Context
import com.trama.shared.data.DiaryDao
import com.trama.shared.data.DiaryRepository
import com.trama.shared.data.RecordingDao
import com.trama.shared.model.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RecordingSaveIntegrityTest {
    @Test fun `noun ending in infinitive suffix is still a concrete destination`() {
        assertTrue(ActionQualityGate.isActionable("Llamar al taller", "CALL"))
        assertFalse(ActionQualityGate.isActionable("Tengo que hacer", "GENERIC"))
    }
    private val dao = mockk<DiaryDao>(relaxed = true)
    private val recordings = mockk<RecordingDao>(relaxed = true)
    private val repository = DiaryRepository(dao, recordings)
    private val processor = RecordingProcessor(mockk<Context>(relaxed = true))
    private val analysis = RecordingProcessor.RecordingAnalysis("Reunión", "Resumen",
        actionItems = listOf(RecordingProcessor.ActionItem("Llamar al taller", "CALL")))

    @Test fun `extraction creates a suggestion without human confirmation`() = runBlocking {
        val entry = slot<DiaryEntry>()
        coEvery { dao.insert(capture(entry)) } returns 7
        processor.saveResult(1, analysis, "", Source.PHONE, "LOCAL", 0.8f, repository)
        assertEquals(EntryStatus.SUGGESTED, entry.captured.status)
        assertNull(entry.captured.userConfirmedAt)
        assertEquals(1L, entry.captured.sourceRecordingId)
    }

    @Test fun `failed action insert cannot report recording completed`() = runBlocking {
        coEvery { dao.insert(any()) } throws IllegalStateException("disk full")
        assertTrue(runCatching {
            processor.saveResult(1, analysis, "", Source.PHONE, "LOCAL", 0.8f, repository)
        }.isFailure)
        coVerify(exactly = 0) { recordings.updateProcessingResult(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun `reprocessing preserves an existing reviewed action`() = runBlocking {
        val existing = DiaryEntry(id=7, text="Llamar al taller", keyword="", category="", confidence=1f,
            source=Source.PHONE, duration=0, sourceRecordingId=1, status=EntryStatus.COMPLETED,
            userConfirmedAt=10)
        coEvery { dao.getByRecordingIdOnce(1) } returns listOf(existing)
        processor.saveResult(1, analysis, "", Source.PHONE, "LOCAL", 0.8f, repository)
        coVerify(exactly = 0) { dao.insert(any()) }
        coVerify(exactly = 0) { dao.deleteByRecordingId(any()) }
    }

    @Test fun `meeting suggestion stores only the action from a long conversation`() = runBlocking {
        val entry = slot<DiaryEntry>()
        coEvery { dao.insert(capture(entry)) } returns 9
        val contextual = analysis.copy(
            actionItems = listOf(
                RecordingProcessor.ActionItem(
                    "Estuvimos hablando del viaje y tenemos que llamar a Pedro mañana. Después seguimos con el hotel",
                    "CALL"
                )
            )
        )

        processor.saveResult(2, contextual, "", Source.PHONE, "LOCAL", 0.8f, repository)

        assertEquals("Llamar a Pedro mañana", entry.captured.cleanText)
        assertEquals("Llamar a Pedro mañana", entry.captured.text)
        assertEquals(EntryStatus.SUGGESTED, entry.captured.status)
    }
}
