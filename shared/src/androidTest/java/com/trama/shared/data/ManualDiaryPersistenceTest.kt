package com.trama.shared.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryContentKind
import com.trama.shared.model.EntryHumanDecision
import com.trama.shared.model.EntryStatus
import com.trama.shared.model.Recording
import com.trama.shared.model.Source
import com.trama.shared.model.TimelineEvent
import com.trama.shared.model.TimelineEventType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ManualDiaryPersistenceTest {
    @Test
    fun automaticProcessingCannotOverwriteAHumanDecision() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, DiaryDatabase::class.java).build()
        try {
            val dao = db.diaryDao()
            val id = dao.insert(
                DiaryEntry(
                    text = "Llamar a Ana",
                    keyword = "recordar",
                    category = "Acción",
                    confidence = 0.9f,
                    source = Source.PHONE,
                    duration = 1,
                    contentKind = EntryContentKind.ACTION,
                    sourceCaptureId = "test-capture"
                )
            )

            assertEquals(1, dao.markDiscarded(id, now = 2_000))
            assertEquals(0, dao.autoDiscard(id, expectedRevision = 0, now = 3_000))
            assertEquals(0, dao.markSuggested(id))
            assertEquals(
                0,
                dao.updateAIProcessing(id, "Texto tardío", "CALL", null, "NORMAL", 1f)
            )

            val decided = requireNotNull(dao.getByIdOnce(id))
            assertEquals(EntryStatus.DISCARDED, decided.status)
            assertEquals(EntryHumanDecision.DISCARDED, decided.humanDecision)
            assertEquals(1L, decided.revision)
            assertEquals("Llamar a Ana", decided.displayText)

            assertEquals(1, dao.restoreDiscardedSuggestion(id))
            val restored = requireNotNull(dao.getByIdOnce(id))
            assertEquals(EntryStatus.SUGGESTED, restored.status)
            assertNull(restored.completedAt)
            assertNull(restored.humanDecision)
            assertEquals(2L, restored.revision)

            assertEquals(1, dao.markPending(id))
            val reopened = requireNotNull(dao.getByIdOnce(id))
            assertEquals(EntryStatus.PENDING, reopened.status)
            assertNull(reopened.humanDecision)
            assertEquals(3L, reopened.revision)
            assertEquals(
                1,
                dao.updateAIProcessing(id, "Llamar a Ana mañana", "CALL", 9_000, "HIGH", 0.95f)
            )
        } finally {
            db.close()
        }
    }

    @Test fun saveReopenEditAndSearchPreserveOriginalAndMetadata() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "phase2-test-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, DiaryDatabase::class.java, name).build()
        var db = open()
        try {
            val original = DiaryEntry(text = "Restaurante del viaje", keyword = "manual", category = "Nota",
                confidence = 1f, source = Source.PHONE, duration = 0, isManual = true,
                createdAt = 1000, cleanText = "Restaurante del viaje", userConfirmedAt = 1100,
                verificationSource = "DETAIL", dueDate = 9000)
            val id = db.diaryDao().insert(original)
            db.close()
            db = open()
            assertEquals(original.copy(id = id), db.diaryDao().getByIdOnce(id))
            db.diaryDao().updateText(id, "Excelente arroz en Portonovo")
            db.close()
            db = open()
            val restored = requireNotNull(db.diaryDao().getByIdOnce(id))
            assertEquals(original.text, restored.text)
            assertEquals("Excelente arroz en Portonovo", restored.displayText)
            assertEquals(original.createdAt, restored.createdAt)
            assertEquals(original.dueDate, restored.dueDate)
            assertEquals(original.userConfirmedAt, restored.userConfirmedAt)
            assertEquals(original.verificationSource, restored.verificationSource)
            assertEquals(listOf(id), db.diaryDao().search("Portonovo").first().map { it.id })
            assertEquals(listOf(id), db.diaryDao().byDateRange(1000, 1000).first().map { it.id })
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun discardedEntriesStayOutOfUserVisibleDiarySearchAndRecordingActions() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, DiaryDatabase::class.java).build()
        try {
            val dao = db.diaryDao()
            val discardedId = dao.insert(
                DiaryEntry(
                    text = "Comprar camisa",
                    keyword = "recordar",
                    category = "Acción",
                    confidence = 0.7f,
                    createdAt = 1_000,
                    source = Source.PHONE,
                    duration = 2,
                    status = EntryStatus.SUGGESTED,
                    sourceRecordingId = 7
                )
            )

            dao.markDiscarded(discardedId, now = 1_100)

            assertTrue(dao.byDateRange(0, 2_000).first().isEmpty())
            assertTrue(dao.search("camisa").first().isEmpty())
            assertTrue(dao.getByRecordingId(7).first().isEmpty())
            assertEquals(EntryStatus.DISCARDED, dao.getByIdOnce(discardedId)?.status)
            assertEquals(listOf(discardedId), dao.getByRecordingIdOnce(7).map { it.id })
        } finally {
            db.close()
        }
    }

    @Test
    fun derivedActionReplacesSourceBeforeAndAfterCompletion() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, DiaryDatabase::class.java).build()
        try {
            val dao = db.diaryDao()
            val sourceId = dao.insert(
                DiaryEntry(
                    text = "Recordar ver la serie Adults",
                    keyword = "recordar",
                    category = "Memoria",
                    confidence = 0.9f,
                    createdAt = 1_000,
                    source = Source.PHONE,
                    duration = 2,
                    status = EntryStatus.SAVED,
                    contentKind = EntryContentKind.MEMORY
                )
            )
            val actionId = dao.insert(
                DiaryEntry(
                    text = "Ver la serie Adults",
                    keyword = "recordar",
                    category = "Acción",
                    confidence = 0.9f,
                    createdAt = 1_000,
                    source = Source.PHONE,
                    duration = 2,
                    status = EntryStatus.PENDING,
                    contentKind = EntryContentKind.ACTION,
                    parentEntryId = sourceId
                )
            )

            assertEquals(listOf(actionId), dao.byDateRange(0, 2_000).first().map { it.id })

            dao.markCompleted(actionId, completedAt = 1_500)

            assertEquals(listOf(actionId), dao.byDateRange(0, 2_000).first().map { it.id })
        } finally {
            db.close()
        }
    }

    @Test
    fun recordingDayQueryIncludesBoundariesAndExcludesOtherDays() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, DiaryDatabase::class.java).build()
        try {
            val dao = db.recordingDao()
            for (time in listOf(99L, 100L, 200L, 201L)) {
                dao.insert(Recording(transcription = "test", durationSeconds = 1, source = Source.PHONE, createdAt = time))
            }
            assertEquals(listOf(100L, 200L), dao.getByDateRange(100, 200).first().map { it.createdAt })
        } finally { db.close() }
    }

    @Test
    fun calendarQueryIncludesEventsThatStartedBeforeRangeAndRemainInProgress() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, DiaryDatabase::class.java).build()
        try {
            val dao = db.timelineEventDao()
            dao.insert(
                TimelineEvent(
                    type = TimelineEventType.CALENDAR,
                    timestamp = 90,
                    endTimestamp = 110,
                    title = "En curso"
                )
            )
            dao.insert(
                TimelineEvent(
                    type = TimelineEventType.CALENDAR,
                    timestamp = 80,
                    endTimestamp = 99,
                    title = "Finalizado"
                )
            )
            dao.insert(
                TimelineEvent(
                    type = TimelineEventType.DWELL,
                    timestamp = 100,
                    endTimestamp = 110,
                    title = "Lugar"
                )
            )

            assertEquals(
                listOf("En curso"),
                dao.calendarOverlapping(startTime = 100, endTime = 200).first().map { it.title }
            )
        } finally {
            db.close()
        }
    }
}
