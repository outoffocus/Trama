package com.mydiary.shared.data

import com.mydiary.shared.model.DiaryEntry
import com.mydiary.shared.model.Recording
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

class DiaryRepository(
    private val dao: DiaryDao,
    private val recordingDao: RecordingDao? = null
) {

    // ── DiaryEntry ──

    fun getById(id: Long): Flow<DiaryEntry?> = dao.getById(id).distinctUntilChanged()

    fun getAll(): Flow<List<DiaryEntry>> = dao.getAll().distinctUntilChanged()

    fun getPending(): Flow<List<DiaryEntry>> = dao.getPending().distinctUntilChanged()

    fun getCompleted(): Flow<List<DiaryEntry>> = dao.getCompleted().distinctUntilChanged()

    fun getOverdue(): Flow<List<DiaryEntry>> = dao.getOverdue().distinctUntilChanged()

    fun byDateRange(startTime: Long, endTime: Long): Flow<List<DiaryEntry>> =
        dao.byDateRange(startTime, endTime).distinctUntilChanged()

    suspend fun getUnsynced(): List<DiaryEntry> = dao.getUnsynced()

    suspend fun getAllOnce(): List<DiaryEntry> = dao.getAllOnce()

    fun search(query: String): Flow<List<DiaryEntry>> = dao.search(query).distinctUntilChanged()

    suspend fun insert(entry: DiaryEntry): Long = dao.insert(entry)

    suspend fun delete(entry: DiaryEntry) = dao.delete(entry)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun updateText(id: Long, text: String) = dao.updateText(id, text)

    suspend fun markSynced(ids: List<Long>) = dao.markSynced(ids)

    suspend fun existsByCreatedAtAndText(createdAt: Long, text: String): Boolean =
        dao.existsByCreatedAtAndText(createdAt, text)

    suspend fun deleteByIds(ids: List<Long>) = dao.deleteByIds(ids)

    suspend fun updateLLMReview(id: Long, correctedText: String?, confidence: Float) =
        dao.updateLLMReview(id, correctedText, confidence)

    suspend fun markCompleted(id: Long) = dao.markCompleted(id)

    suspend fun markDiscarded(id: Long) = dao.markDiscarded(id)

    suspend fun markPending(id: Long) = dao.markPending(id)

    suspend fun markCompletedByIds(ids: List<Long>) = dao.markCompletedByIds(ids)

    suspend fun updateAIProcessing(
        id: Long, cleanText: String, actionType: String,
        dueDate: Long?, priority: String, confidence: Float
    ) = dao.updateAIProcessing(id, cleanText, actionType, dueDate, priority, confidence)

    fun getLatest(): Flow<DiaryEntry?> = dao.getLatest().distinctUntilChanged()

    fun getLatestPending(): Flow<DiaryEntry?> = dao.getLatestPending().distinctUntilChanged()

    fun countAll(): Flow<Int> = dao.countAll().distinctUntilChanged()

    fun countPending(): Flow<Int> = dao.countPending().distinctUntilChanged()

    fun countCompletedToday(startOfDay: Long): Flow<Int> = dao.countCompletedToday(startOfDay).distinctUntilChanged()

    suspend fun markDuplicate(id: Long, originalId: Long) = dao.markDuplicate(id, originalId)

    suspend fun clearDuplicate(id: Long) = dao.clearDuplicate(id)

    fun getDuplicates(): Flow<List<DiaryEntry>> = dao.getDuplicates().distinctUntilChanged()

    suspend fun getRecentPendingForDedup(): List<DiaryEntry> = dao.getRecentPendingForDedup()

    suspend fun markCompletedByKey(createdAt: Long, text: String) = dao.markCompletedByKey(createdAt, text)

    suspend fun deleteByKey(createdAt: Long, text: String) = dao.deleteByKey(createdAt, text)

    fun getByRecordingId(recordingId: Long): Flow<List<DiaryEntry>> =
        dao.getByRecordingId(recordingId).distinctUntilChanged()

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
        processedLocally: Boolean = false
    ) = recordingDao?.updateProcessingResult(id, title, summary, keyPoints, status, processedLocally)

    fun recordingCount(): Flow<Int> =
        recordingDao?.count()?.distinctUntilChanged()
            ?: kotlinx.coroutines.flow.flowOf(0)

    suspend fun getUnsyncedRecordings(): List<Recording> =
        recordingDao?.getUnsynced() ?: emptyList()

    suspend fun markRecordingsSynced(ids: List<Long>) =
        recordingDao?.markSynced(ids)

    suspend fun existsRecordingByCreatedAt(createdAt: Long): Boolean =
        recordingDao?.existsByCreatedAt(createdAt) ?: false
}
