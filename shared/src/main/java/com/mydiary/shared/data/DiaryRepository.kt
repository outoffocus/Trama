package com.mydiary.shared.data

import com.mydiary.shared.model.Category
import com.mydiary.shared.model.DiaryEntry
import kotlinx.coroutines.flow.Flow

class DiaryRepository(private val dao: DiaryDao) {

    fun getAll(): Flow<List<DiaryEntry>> = dao.getAll()

    fun byCategory(category: Category): Flow<List<DiaryEntry>> = dao.byCategory(category)

    fun byDateRange(startTime: Long, endTime: Long): Flow<List<DiaryEntry>> =
        dao.byDateRange(startTime, endTime)

    suspend fun getUnsynced(): List<DiaryEntry> = dao.getUnsynced()

    fun search(query: String): Flow<List<DiaryEntry>> = dao.search(query)

    suspend fun insert(entry: DiaryEntry): Long = dao.insert(entry)

    suspend fun delete(entry: DiaryEntry) = dao.delete(entry)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun updateText(id: Long, text: String) = dao.updateText(id, text)

    suspend fun updateCategory(id: Long, category: Category) = dao.updateCategory(id, category)

    suspend fun markSynced(ids: List<Long>) = dao.markSynced(ids)
}
