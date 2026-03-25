package com.mydiary.shared.data

import com.mydiary.shared.model.DiaryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

class DiaryRepository(private val dao: DiaryDao) {

    fun getById(id: Long): Flow<DiaryEntry?> = dao.getById(id).distinctUntilChanged()

    fun getAll(): Flow<List<DiaryEntry>> = dao.getAll().distinctUntilChanged()

    fun getPending(): Flow<List<DiaryEntry>> = dao.getPending().distinctUntilChanged()

    fun getCompleted(): Flow<List<DiaryEntry>> = dao.getCompleted().distinctUntilChanged()

    fun getOverdue(): Flow<List<DiaryEntry>> = dao.getOverdue().distinctUntilChanged()

    fun byDateRange(startTime: Long, endTime: Long): Flow<List<DiaryEntry>> =
        dao.byDateRange(startTime, endTime).distinctUntilChanged()

    suspend fun getUnsynced(): List<DiaryEntry> = dao.getUnsynced()

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

    fun countAll(): Flow<Int> = dao.countAll().distinctUntilChanged()

    fun countPending(): Flow<Int> = dao.countPending().distinctUntilChanged()

    fun countCompletedToday(startOfDay: Long): Flow<Int> = dao.countCompletedToday(startOfDay).distinctUntilChanged()
}
