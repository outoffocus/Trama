package com.mydiary.shared.data

import com.mydiary.shared.model.DiaryEntry
import kotlinx.coroutines.flow.Flow

class DiaryRepository(private val dao: DiaryDao) {

    fun getById(id: Long): Flow<DiaryEntry?> = dao.getById(id)

    fun getAll(): Flow<List<DiaryEntry>> = dao.getAll()

    fun byDateRange(startTime: Long, endTime: Long): Flow<List<DiaryEntry>> =
        dao.byDateRange(startTime, endTime)

    suspend fun getUnsynced(): List<DiaryEntry> = dao.getUnsynced()

    fun search(query: String): Flow<List<DiaryEntry>> = dao.search(query)

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
}
