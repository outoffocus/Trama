package com.mydiary.shared.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.mydiary.shared.model.DiaryEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {

    @Query("SELECT * FROM diary_entries WHERE id = :id")
    fun getById(id: Long): Flow<DiaryEntry?>

    @Query("SELECT * FROM diary_entries ORDER BY createdAt DESC")
    fun getAll(): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries WHERE category = :category ORDER BY createdAt DESC")
    fun byCategory(category: String): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries WHERE createdAt BETWEEN :startTime AND :endTime ORDER BY createdAt DESC")
    fun byDateRange(startTime: Long, endTime: Long): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries WHERE isSynced = 0")
    suspend fun getUnsynced(): List<DiaryEntry>

    @Query("SELECT * FROM diary_entries WHERE text LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun search(query: String): Flow<List<DiaryEntry>>

    @Insert
    suspend fun insert(entry: DiaryEntry): Long

    @Delete
    suspend fun delete(entry: DiaryEntry): Int

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE diary_entries SET text = :text WHERE id = :id")
    suspend fun updateText(id: Long, text: String)

    @Query("UPDATE diary_entries SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: Long, category: String)

    @Query("UPDATE diary_entries SET category = :newCategory WHERE category = :oldCategory")
    suspend fun reassignCategory(oldCategory: String, newCategory: String)

    @Query("UPDATE diary_entries SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>): Int

    /** Dedup check for sync: entry already exists with same timestamp and text */
    @Query("SELECT COUNT(*) > 0 FROM diary_entries WHERE createdAt = :createdAt AND text = :text")
    suspend fun existsByCreatedAtAndText(createdAt: Long, text: String): Boolean
}
