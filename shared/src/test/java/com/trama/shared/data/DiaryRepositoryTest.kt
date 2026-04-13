package com.trama.shared.data

import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.DwellDetectionState
import com.trama.shared.model.Place
import com.trama.shared.model.Recording
import com.trama.shared.model.RecordingStatus
import com.trama.shared.model.Source
import com.trama.shared.model.TimelineEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for DiaryRepository logic, using a fake DAO to verify delegation
 * and behavior when the optional RecordingDao is null vs present.
 */
class DiaryRepositoryTest {

    private lateinit var fakeDao: FakeDiaryDao
    private lateinit var fakeRecordingDao: FakeRecordingDao

    private fun makeEntry(
        id: Long = 0,
        text: String = "test entry",
        keyword: String = "pendiente",
        category: String = "Pendientes",
        createdAt: Long = System.currentTimeMillis(),
        source: Source = Source.PHONE
    ) = DiaryEntry(
        id = id, text = text, keyword = keyword, category = category,
        confidence = 0.9f, createdAt = createdAt, source = source, duration = 5
    )

    private fun makeRecording(
        id: Long = 0,
        transcription: String = "test recording",
        durationSeconds: Int = 60,
        source: Source = Source.PHONE,
        createdAt: Long = System.currentTimeMillis()
    ) = Recording(
        id = id, transcription = transcription, durationSeconds = durationSeconds,
        source = source, createdAt = createdAt
    )

    @Before
    fun setUp() {
        fakeDao = FakeDiaryDao()
        fakeRecordingDao = FakeRecordingDao()
    }

    // ── Recording fallback when RecordingDao is null ──

    @Test
    fun `getAllRecordings returns empty flow when recordingDao is null`() = runBlocking {
        val repo = DiaryRepository(fakeDao, recordingDao = null)
        val result = repo.getAllRecordings().first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getRecordingById returns null flow when recordingDao is null`() = runBlocking {
        val repo = DiaryRepository(fakeDao, recordingDao = null)
        val result = repo.getRecordingById(1L).first()
        assertNull(result)
    }

    @Test
    fun `getRecordingByIdOnce returns null when recordingDao is null`() = runBlocking {
        val repo = DiaryRepository(fakeDao, recordingDao = null)
        val result = repo.getRecordingByIdOnce(1L)
        assertNull(result)
    }

    @Test
    fun `insertRecording returns -1 when recordingDao is null`() = runBlocking {
        val repo = DiaryRepository(fakeDao, recordingDao = null)
        val result = repo.insertRecording(makeRecording())
        assertEquals(-1L, result)
    }

    @Test
    fun `getAllRecordingsOnce returns empty list when recordingDao is null`() = runBlocking {
        val repo = DiaryRepository(fakeDao, recordingDao = null)
        val result = repo.getAllRecordingsOnce()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getUnsyncedRecordings returns empty list when recordingDao is null`() = runBlocking {
        val repo = DiaryRepository(fakeDao, recordingDao = null)
        val result = repo.getUnsyncedRecordings()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `existsRecordingByCreatedAt returns false when recordingDao is null`() = runBlocking {
        val repo = DiaryRepository(fakeDao, recordingDao = null)
        val result = repo.existsRecordingByCreatedAt(1000L)
        assertFalse(result)
    }

    @Test
    fun `recordingCount returns 0 flow when recordingDao is null`() = runBlocking {
        val repo = DiaryRepository(fakeDao, recordingDao = null)
        val result = repo.recordingCount().first()
        assertEquals(0, result)
    }

    // ── Delegation to DAO when RecordingDao is present ──

    @Test
    fun `insertRecording delegates to recordingDao when present`() = runBlocking {
        val repo = DiaryRepository(fakeDao, fakeRecordingDao)
        val recording = makeRecording()
        val id = repo.insertRecording(recording)
        assertEquals(1L, id) // FakeRecordingDao returns 1
        assertEquals(1, fakeRecordingDao.inserted.size)
    }

    @Test
    fun `getAllRecordingsOnce delegates to recordingDao when present`() = runBlocking {
        val repo = DiaryRepository(fakeDao, fakeRecordingDao)
        fakeRecordingDao.recordings.add(makeRecording(id = 5))
        val result = repo.getAllRecordingsOnce()
        assertEquals(1, result.size)
        assertEquals(5L, result[0].id)
    }

    // ── DiaryEntry delegation ──

    @Test
    fun `insert delegates to diaryDao`() = runBlocking {
        val repo = DiaryRepository(fakeDao)
        val entry = makeEntry()
        val id = repo.insert(entry)
        assertEquals(1L, id)
        assertEquals(1, fakeDao.inserted.size)
    }

    @Test
    fun `getUnsynced delegates to diaryDao`() = runBlocking {
        val repo = DiaryRepository(fakeDao)
        val unsynced = makeEntry(id = 3, text = "unsynced")
        fakeDao.unsyncedEntries.add(unsynced)
        val result = repo.getUnsynced()
        assertEquals(1, result.size)
        assertEquals("unsynced", result[0].text)
    }

    @Test
    fun `existsByCreatedAtAndText delegates correctly`() = runBlocking {
        val repo = DiaryRepository(fakeDao)
        fakeDao.existsResult = true
        val result = repo.existsByCreatedAtAndText(1000L, "test")
        assertTrue(result)
    }

    @Test
    fun `deleteById delegates to diaryDao`() = runBlocking {
        val repo = DiaryRepository(fakeDao)
        repo.deleteById(5L)
        assertEquals(5L, fakeDao.lastDeletedId)
    }

    @Test
    fun `markSynced delegates to diaryDao`() = runBlocking {
        val repo = DiaryRepository(fakeDao)
        repo.markSynced(listOf(1L, 2L, 3L))
        assertEquals(listOf(1L, 2L, 3L), fakeDao.lastSyncedIds)
    }
}

// ── Fake implementations ──

private class FakeDiaryDao : DiaryDao {
    val inserted = mutableListOf<DiaryEntry>()
    val unsyncedEntries = mutableListOf<DiaryEntry>()
    var existsResult = false
    var lastDeletedId: Long? = null
    var lastSyncedIds: List<Long>? = null

    override fun getById(id: Long): Flow<DiaryEntry?> = flowOf(null)
    override suspend fun getByIdOnce(id: Long): DiaryEntry? = inserted.find { it.id == id }
    override fun getAll(): Flow<List<DiaryEntry>> = flowOf(emptyList())
    override fun getPending(): Flow<List<DiaryEntry>> = flowOf(emptyList())
    override fun getCompleted(): Flow<List<DiaryEntry>> = flowOf(emptyList())
    override fun getOverdue(now: Long): Flow<List<DiaryEntry>> = flowOf(emptyList())
    override fun byDateRange(startTime: Long, endTime: Long): Flow<List<DiaryEntry>> = flowOf(emptyList())
    override suspend fun getUnsynced(): List<DiaryEntry> = unsyncedEntries
    override suspend fun getAllOnce(): List<DiaryEntry> = emptyList()
    override fun search(query: String): Flow<List<DiaryEntry>> = flowOf(emptyList())
    override suspend fun insert(entry: DiaryEntry): Long { inserted.add(entry); return inserted.size.toLong() }
    override suspend fun delete(entry: DiaryEntry): Int = 1
    override suspend fun deleteById(id: Long) { lastDeletedId = id }
    override suspend fun updateText(id: Long, text: String) {}
    override suspend fun markSynced(ids: List<Long>): Int { lastSyncedIds = ids; return ids.size }
    override suspend fun existsByCreatedAtAndText(createdAt: Long, text: String): Boolean = existsResult
    override suspend fun deleteByIds(ids: List<Long>) {}
    override suspend fun updateLLMReview(id: Long, correctedText: String?, confidence: Float) {}
    override suspend fun markCompleted(id: Long, completedAt: Long) {}
    override suspend fun markDiscarded(id: Long, now: Long) {}
    override suspend fun markPending(id: Long) {}
    override suspend fun markCompletedByIds(ids: List<Long>, completedAt: Long) {}
    override suspend fun updateAIProcessing(id: Long, cleanText: String, actionType: String, dueDate: Long?, priority: String, confidence: Float) {}
    override fun getLatest(): Flow<DiaryEntry?> = flowOf(null)
    override fun getLatestPending(): Flow<DiaryEntry?> = flowOf(null)
    override suspend fun getLatestPendingOnce(): DiaryEntry? = null
    override fun countAll(): Flow<Int> = flowOf(0)
    override fun countPending(): Flow<Int> = flowOf(0)
    override fun countCompletedToday(startOfDay: Long): Flow<Int> = flowOf(0)
    override suspend fun markDuplicate(id: Long, originalId: Long) {}
    override suspend fun clearDuplicate(id: Long) {}
    override fun getDuplicates(): Flow<List<DiaryEntry>> = flowOf(emptyList())
    override suspend fun getRecentPendingForDedup(): List<DiaryEntry> = emptyList()
    override suspend fun markCompletedByKey(createdAt: Long, text: String, completedAt: Long): Int = 0
    override suspend fun deleteByKey(createdAt: Long, text: String): Int = 0
    override fun getByRecordingId(recordingId: Long): Flow<List<DiaryEntry>> = flowOf(emptyList())
    override suspend fun getByRecordingIdOnce(recordingId: Long): List<DiaryEntry> = emptyList()
    override suspend fun deleteByRecordingId(recordingId: Long) {}
    override suspend fun updateDueDate(id: Long, dueDate: Long?) {}
}

private class FakeRecordingDao : RecordingDao {
    val recordings = mutableListOf<Recording>()
    val inserted = mutableListOf<Recording>()

    override fun getAll(): Flow<List<Recording>> = flowOf(recordings.toList())
    override suspend fun getAllOnce(): List<Recording> = recordings.toList()
    override fun getById(id: Long): Flow<Recording?> = flowOf(recordings.find { it.id == id })
    override suspend fun getByIdOnce(id: Long): Recording? = recordings.find { it.id == id }
    override suspend fun insert(recording: Recording): Long { inserted.add(recording); return inserted.size.toLong() }
    override suspend fun delete(id: Long) {}
    override suspend fun deleteByIds(ids: List<Long>) {}
    override suspend fun updateStatus(id: Long, status: String) {}
    override suspend fun updateProcessingResult(id: Long, title: String, summary: String, keyPoints: String?, status: String, processedLocally: Boolean, processedBy: String?) {}
    override fun count(): Flow<Int> = flowOf(recordings.size)
    override suspend fun getUnsynced(): List<Recording> = emptyList()
    override suspend fun markSynced(ids: List<Long>) {}
    override suspend fun existsByCreatedAt(createdAt: Long): Boolean = recordings.any { it.createdAt == createdAt }
}

private class FakeTimelineEventDao : TimelineEventDao {
    override fun getAll(): Flow<List<TimelineEvent>> = flowOf(emptyList())
    override fun byDateRange(startTime: Long, endTime: Long): Flow<List<TimelineEvent>> = flowOf(emptyList())
    override suspend fun byDateRangeOnce(startTime: Long, endTime: Long): List<TimelineEvent> = emptyList()
    override suspend fun getByIdOnce(id: Long): TimelineEvent? = null
    override fun getByPlaceId(placeId: Long): Flow<List<TimelineEvent>> = flowOf(emptyList())
    override suspend fun insert(event: TimelineEvent): Long = 1L
    override suspend fun insertAll(events: List<TimelineEvent>) {}
    override suspend fun update(event: TimelineEvent) {}
    override suspend fun updateTitleForPlace(placeId: Long, title: String) {}
    override suspend fun deleteById(id: Long) {}
    override suspend fun deleteByIds(ids: List<Long>) {}
}

private class FakePlaceDao : PlaceDao {
    override fun getAll(): Flow<List<Place>> = flowOf(emptyList())
    override suspend fun getAllOnce(): List<Place> = emptyList()
    override fun getById(id: Long): Flow<Place?> = flowOf(null)
    override suspend fun getByIdOnce(id: Long): Place? = null
    override suspend fun insert(place: Place): Long = 1L
    override suspend fun update(place: Place) {}
    override suspend fun rename(id: Long, name: String, updatedAt: Long) {}
    override suspend fun incrementVisit(id: Long, visitedAt: Long, updatedAt: Long) {}
    override suspend fun markHome(placeId: Long) {}
    override suspend fun clearHome(placeId: Long) {}
    override suspend fun markWork(placeId: Long) {}
    override suspend fun clearWork(placeId: Long) {}
    override suspend fun findInBoundingBox(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<Place> = emptyList()
    override suspend fun updateOpinion(id: Long, rating: Int?, opinionText: String?, opinionSummary: String?, opinionUpdatedAt: Long?, updatedAt: Long) {}
    override suspend fun updateOpinionSummary(id: Long, opinionSummary: String?, opinionUpdatedAt: Long?, updatedAt: Long) {}
}

private class FakeDwellDetectionStateDao : DwellDetectionStateDao {
    override fun observe(): Flow<DwellDetectionState?> = flowOf(null)
    override suspend fun get(): DwellDetectionState? = null
    override suspend fun save(state: DwellDetectionState) {}
    override suspend fun clear() {}
}
