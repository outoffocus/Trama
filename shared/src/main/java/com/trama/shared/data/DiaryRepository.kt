package com.trama.shared.data

import androidx.room.withTransaction
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.DailyPage
import com.trama.shared.model.DwellDetectionState
import com.trama.shared.model.Place
import com.trama.shared.model.Recording
import com.trama.shared.model.TimelineEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

class DiaryRepository(
    private val dao: DiaryDao,
    private val recordingDao: RecordingDao? = null,
    private val timelineEventDao: TimelineEventDao? = null,
    private val placeDao: PlaceDao? = null,
    private val dwellDetectionStateDao: DwellDetectionStateDao? = null,
    private val dailyPageDao: DailyPageDao? = null,
    private val database: DiaryDatabase? = null
) {

    // ── DiaryEntry ──

    fun getById(id: Long): Flow<DiaryEntry?> = dao.getById(id).distinctUntilChanged()

    suspend fun getByIdOnce(id: Long): DiaryEntry? = dao.getByIdOnce(id)

    fun getAll(): Flow<List<DiaryEntry>> = dao.getAll().distinctUntilChanged()

    fun getPending(): Flow<List<DiaryEntry>> = dao.getPending().distinctUntilChanged()

    fun getSuggested(): Flow<List<DiaryEntry>> = dao.getSuggested().distinctUntilChanged()

    fun getCompleted(): Flow<List<DiaryEntry>> = dao.getCompleted().distinctUntilChanged()

    fun getOverdue(): Flow<List<DiaryEntry>> = dao.getOverdue().distinctUntilChanged()

    fun getPendingFromOtherDays(beforeDayStart: Long, dayEnd: Long): Flow<List<DiaryEntry>> =
        dao.getPendingFromOtherDays(beforeDayStart, dayEnd).distinctUntilChanged()

    fun byDateRange(startTime: Long, endTime: Long): Flow<List<DiaryEntry>> =
        dao.byDateRange(startTime, endTime).distinctUntilChanged()

    fun getCompletedByCompletedAt(startTime: Long, endTime: Long): Flow<List<DiaryEntry>> =
        dao.getCompletedByCompletedAt(startTime, endTime).distinctUntilChanged()

    fun getPendingAsOf(dayEnd: Long): Flow<List<DiaryEntry>> =
        dao.getPendingAsOf(dayEnd).distinctUntilChanged()

    fun getPendingForDay(dayStart: Long, dayEnd: Long): Flow<List<DiaryEntry>> =
        dao.getPendingForDay(dayStart, dayEnd).distinctUntilChanged()

    suspend fun getUnsynced(): List<DiaryEntry> = dao.getUnsynced()

    suspend fun getAllOnce(): List<DiaryEntry> = dao.getAllOnce()

    fun search(query: String): Flow<List<DiaryEntry>> = dao.search(query).distinctUntilChanged()

    suspend fun insert(entry: DiaryEntry): Long = dao.insert(entry)

    suspend fun delete(entry: DiaryEntry) = dao.delete(entry)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun updateText(id: Long, text: String) = dao.updateText(id, text)

    suspend fun updateDueDate(id: Long, dueDate: Long?) = dao.updateDueDate(id, dueDate)

    suspend fun markSynced(ids: List<Long>) = dao.markSynced(ids)

    suspend fun existsByCreatedAtAndText(createdAt: Long, text: String): Boolean =
        dao.existsByCreatedAtAndText(createdAt, text)

    suspend fun deleteByIds(ids: List<Long>) = dao.deleteByIds(ids)

    suspend fun updateLLMReview(id: Long, correctedText: String?, confidence: Float) =
        dao.updateLLMReview(id, correctedText, confidence)

    suspend fun updateProcessingBackend(id: Long, backend: String?) =
        dao.updateProcessingBackend(id, backend)

    suspend fun markCompleted(id: Long) = dao.markCompleted(id)

    suspend fun markDiscarded(id: Long) = dao.markDiscarded(id)

    suspend fun markSuggested(id: Long) = dao.markSuggested(id)

    suspend fun markPending(id: Long) = dao.markPending(id)

    suspend fun markCompletedByIds(ids: List<Long>) = dao.markCompletedByIds(ids)

    suspend fun updateAIProcessing(
        id: Long, cleanText: String, actionType: String,
        dueDate: Long?, priority: String, confidence: Float
    ) = dao.updateAIProcessing(id, cleanText, actionType, dueDate, priority, confidence)

    fun getLatest(): Flow<DiaryEntry?> = dao.getLatest().distinctUntilChanged()

    fun getLatestPending(): Flow<DiaryEntry?> = dao.getLatestPending().distinctUntilChanged()

    suspend fun getLatestPendingOnce(): DiaryEntry? = dao.getLatestPendingOnce()

    fun countAll(): Flow<Int> = dao.countAll().distinctUntilChanged()

    fun countPending(): Flow<Int> = dao.countPending().distinctUntilChanged()

    fun countCompletedToday(startOfDay: Long): Flow<Int> = dao.countCompletedToday(startOfDay).distinctUntilChanged()

    suspend fun markDuplicate(id: Long, originalId: Long) = dao.markDuplicate(id, originalId)

    suspend fun clearDuplicate(id: Long) = dao.clearDuplicate(id)

    fun getDuplicates(): Flow<List<DiaryEntry>> = dao.getDuplicates().distinctUntilChanged()

    suspend fun getRecentPendingForDedup(): List<DiaryEntry> = dao.getRecentPendingForDedup()

    suspend fun getRecentActiveForDedup(): List<DiaryEntry> = dao.getRecentActiveForDedup()

    suspend fun <T> withTransaction(block: suspend DiaryRepository.() -> T): T {
        val db = database
        return if (db != null) {
            db.withTransaction { block() }
        } else {
            block()
        }
    }

    suspend fun markCompletedByKey(createdAt: Long, text: String) = dao.markCompletedByKey(createdAt, text)

    suspend fun deleteByKey(createdAt: Long, text: String) = dao.deleteByKey(createdAt, text)

    fun getByRecordingId(recordingId: Long): Flow<List<DiaryEntry>> =
        dao.getByRecordingId(recordingId).distinctUntilChanged()

    suspend fun getByRecordingIdOnce(recordingId: Long): List<DiaryEntry> =
        dao.getByRecordingIdOnce(recordingId)

    suspend fun deleteByRecordingId(recordingId: Long) = dao.deleteByRecordingId(recordingId)

    // ── Recording ──

    fun getAllRecordings(): Flow<List<Recording>> =
        recordingDao?.getAll()?.distinctUntilChanged()
            ?: kotlinx.coroutines.flow.flowOf(emptyList())

    fun getRecordingById(id: Long): Flow<Recording?> =
        recordingDao?.getById(id)?.distinctUntilChanged()
            ?: kotlinx.coroutines.flow.flowOf(null)

    suspend fun getRecordingByIdOnce(id: Long): Recording? =
        recordingDao?.getByIdOnce(id)

    suspend fun insertRecording(recording: Recording): Long =
        recordingDao?.insert(recording) ?: -1

    suspend fun getAllRecordingsOnce(): List<Recording> =
        recordingDao?.getAllOnce() ?: emptyList()

    suspend fun deleteRecording(id: Long) = recordingDao?.delete(id)

    suspend fun deleteRecordingsByIds(ids: List<Long>) = recordingDao?.deleteByIds(ids)

    suspend fun updateRecordingStatus(id: Long, status: String) =
        recordingDao?.updateStatus(id, status)

    suspend fun updateRecordingResult(
        id: Long, title: String, summary: String, keyPoints: String?, status: String,
        processedLocally: Boolean = false, processedBy: String? = null
    ) = recordingDao?.updateProcessingResult(id, title, summary, keyPoints, status, processedLocally, processedBy)

    fun recordingCount(): Flow<Int> =
        recordingDao?.count()?.distinctUntilChanged()
            ?: kotlinx.coroutines.flow.flowOf(0)

    suspend fun getUnsyncedRecordings(): List<Recording> =
        recordingDao?.getUnsynced() ?: emptyList()

    suspend fun markRecordingsSynced(ids: List<Long>) =
        recordingDao?.markSynced(ids)

    suspend fun existsRecordingByCreatedAt(createdAt: Long): Boolean =
        recordingDao?.existsByCreatedAt(createdAt) ?: false

    // ── TimelineEvent ──

    fun getTimelineEvents(): Flow<List<TimelineEvent>> =
        timelineEventDao?.getAll()?.distinctUntilChanged()
            ?: kotlinx.coroutines.flow.flowOf(emptyList())

    fun getTimelineEventsByDateRange(startTime: Long, endTime: Long): Flow<List<TimelineEvent>> =
        timelineEventDao?.byDateRange(startTime, endTime)?.distinctUntilChanged()
            ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun getTimelineEventsByDateRangeOnce(startTime: Long, endTime: Long): List<TimelineEvent> =
        timelineEventDao?.byDateRangeOnce(startTime, endTime) ?: emptyList()

    suspend fun insertTimelineEvent(event: TimelineEvent): Long =
        timelineEventDao?.insert(event) ?: -1

    suspend fun insertTimelineEvents(events: List<TimelineEvent>) =
        timelineEventDao?.insertAll(events)

    suspend fun getTimelineEventByIdOnce(id: Long): TimelineEvent? =
        timelineEventDao?.getByIdOnce(id)

    suspend fun getTimelineEventByTypeSourceAndDataJson(
        type: String,
        source: String,
        dataJson: String
    ): TimelineEvent? = timelineEventDao?.getByTypeSourceAndDataJson(type, source, dataJson)

    fun getTimelineEventsByPlaceId(placeId: Long): Flow<List<TimelineEvent>> =
        timelineEventDao?.getByPlaceId(placeId)?.distinctUntilChanged()
            ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun updateTimelineEvent(event: TimelineEvent) =
        timelineEventDao?.update(event)

    suspend fun deleteTimelineEventById(id: Long) =
        timelineEventDao?.deleteById(id)

    suspend fun deleteTimelineEventsByIds(ids: List<Long>) =
        timelineEventDao?.deleteByIds(ids)

    suspend fun updateTimelineEventTitlesForPlace(placeId: Long, title: String) =
        timelineEventDao?.updateTitleForPlace(placeId, title)

    // ── Places ──

    fun getPlaces(): Flow<List<Place>> =
        placeDao?.getAll()?.distinctUntilChanged()
            ?: kotlinx.coroutines.flow.flowOf(emptyList())

    fun getPlaceById(id: Long): Flow<Place?> =
        placeDao?.getById(id)?.distinctUntilChanged()
            ?: kotlinx.coroutines.flow.flowOf(null)

    suspend fun getPlaceByIdOnce(id: Long): Place? =
        placeDao?.getByIdOnce(id)

    suspend fun getAllPlacesOnce(): List<Place> =
        placeDao?.getAllOnce() ?: emptyList()

    suspend fun insertPlace(place: Place): Long =
        placeDao?.insert(place) ?: -1

    suspend fun updatePlace(place: Place) =
        placeDao?.update(place)

    suspend fun renamePlace(id: Long, name: String) =
        placeDao?.rename(id, name)

    suspend fun incrementPlaceVisit(id: Long, visitedAt: Long) =
        placeDao?.incrementVisit(id, visitedAt)

    suspend fun updatePlaceOpinion(
        id: Long,
        rating: Int?,
        opinionText: String?,
        opinionSummary: String?,
        opinionUpdatedAt: Long?
    ) = placeDao?.updateOpinion(id, rating, opinionText, opinionSummary, opinionUpdatedAt)

    suspend fun updatePlaceOpinionSummary(
        id: Long,
        opinionSummary: String?,
        opinionUpdatedAt: Long?
    ) = placeDao?.updateOpinionSummary(id, opinionSummary, opinionUpdatedAt)

    suspend fun markHomePlace(id: Long) =
        placeDao?.markHome(id)

    suspend fun clearHomePlace(id: Long) =
        placeDao?.clearHome(id)

    suspend fun markWorkPlace(id: Long) =
        placeDao?.markWork(id)

    suspend fun clearWorkPlace(id: Long) =
        placeDao?.clearWork(id)

    suspend fun findPlacesInBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double
    ): List<Place> = placeDao?.findInBoundingBox(minLat, maxLat, minLon, maxLon) ?: emptyList()

    // ── Dwell detector state ──

    fun observeDwellDetectionState(): Flow<DwellDetectionState?> =
        dwellDetectionStateDao?.observe()?.distinctUntilChanged()
            ?: kotlinx.coroutines.flow.flowOf(null)

    suspend fun getDwellDetectionState(): DwellDetectionState? =
        dwellDetectionStateDao?.get()

    suspend fun saveDwellDetectionState(state: DwellDetectionState) =
        dwellDetectionStateDao?.save(state)

    suspend fun clearDwellDetectionState() =
        dwellDetectionStateDao?.clear()

    // ── DailyPage ──

    fun getDailyPage(dayStartMillis: Long): Flow<DailyPage?> =
        dailyPageDao?.getByDay(dayStartMillis)?.distinctUntilChanged()
            ?: kotlinx.coroutines.flow.flowOf(null)

    suspend fun getDailyPageOnce(dayStartMillis: Long): DailyPage? =
        dailyPageDao?.getByDayOnce(dayStartMillis)

    suspend fun upsertDailyPage(page: DailyPage) =
        dailyPageDao?.upsert(page)

    suspend fun markDailyPageReviewed(dayStartMillis: Long, reviewedAt: Long = System.currentTimeMillis()) =
        dailyPageDao?.markReviewed(dayStartMillis, reviewedAt)

    suspend fun getAllDailyPagesOnce(): List<DailyPage> =
        dailyPageDao?.getAllOnce() ?: emptyList()

    suspend fun getCompletedSince(since: Long): List<DiaryEntry> =
        dao.getCompletedSince(since)

    suspend fun getPendingOnce(): List<DiaryEntry> =
        dao.getPendingOnce()
}
