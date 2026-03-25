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

    /** All pending items, ordered by priority then date (accumulative view) */
    @Query("""SELECT * FROM diary_entries WHERE status = 'PENDING'
              ORDER BY CASE priority
                WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1
                WHEN 'NORMAL' THEN 2 WHEN 'LOW' THEN 3 ELSE 4 END,
              createdAt DESC""")
    fun getPending(): Flow<List<DiaryEntry>>

    /** Completed items, most recent first */
    @Query("SELECT * FROM diary_entries WHERE status = 'COMPLETED' ORDER BY completedAt DESC")
    fun getCompleted(): Flow<List<DiaryEntry>>

    /** Pending items with a due date that has passed */
    @Query("SELECT * FROM diary_entries WHERE status = 'PENDING' AND dueDate IS NOT NULL AND dueDate < :now ORDER BY dueDate ASC")
    fun getOverdue(now: Long = System.currentTimeMillis()): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries WHERE createdAt BETWEEN :startTime AND :endTime ORDER BY createdAt DESC")
    fun byDateRange(startTime: Long, endTime: Long): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries WHERE isSynced = 0")
    suspend fun getUnsynced(): List<DiaryEntry>

    @Query("SELECT * FROM diary_entries WHERE text LIKE '%' || :query || '%' OR cleanText LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun search(query: String): Flow<List<DiaryEntry>>

    @Insert
    suspend fun insert(entry: DiaryEntry): Long

    @Delete
    suspend fun delete(entry: DiaryEntry): Int

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE diary_entries SET text = :text WHERE id = :id")
    suspend fun updateText(id: Long, text: String)

    @Query("UPDATE diary_entries SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>): Int

    /** Dedup check for sync: entry already exists with same timestamp and text */
    @Query("SELECT COUNT(*) > 0 FROM diary_entries WHERE createdAt = :createdAt AND text = :text")
    suspend fun existsByCreatedAtAndText(createdAt: Long, text: String): Boolean

    /** Batch delete by IDs */
    @Query("DELETE FROM diary_entries WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** Update LLM-corrected text and review status */
    @Query("UPDATE diary_entries SET correctedText = :correctedText, wasReviewedByLLM = 1, llmConfidence = :confidence WHERE id = :id")
    suspend fun updateLLMReview(id: Long, correctedText: String?, confidence: Float)

    /** Mark entry as completed */
    @Query("UPDATE diary_entries SET status = 'COMPLETED', completedAt = :completedAt WHERE id = :id")
    suspend fun markCompleted(id: Long, completedAt: Long = System.currentTimeMillis())

    /** Mark entry as discarded */
    @Query("UPDATE diary_entries SET status = 'DISCARDED', completedAt = :now WHERE id = :id")
    suspend fun markDiscarded(id: Long, now: Long = System.currentTimeMillis())

    /** Reopen a completed/discarded entry back to pending */
    @Query("UPDATE diary_entries SET status = 'PENDING', completedAt = NULL WHERE id = :id")
    suspend fun markPending(id: Long)

    /** Batch mark as completed */
    @Query("UPDATE diary_entries SET status = 'COMPLETED', completedAt = :completedAt WHERE id IN (:ids)")
    suspend fun markCompletedByIds(ids: List<Long>, completedAt: Long = System.currentTimeMillis())

    /** Update AI-processed fields after capture */
    @Query("UPDATE diary_entries SET cleanText = :cleanText, actionType = :actionType, dueDate = :dueDate, priority = :priority, wasReviewedByLLM = 1, llmConfidence = :confidence WHERE id = :id")
    suspend fun updateAIProcessing(id: Long, cleanText: String, actionType: String, dueDate: Long?, priority: String, confidence: Float)

    /** Get only the most recent entry (for watch home screen) */
    @Query("SELECT * FROM diary_entries ORDER BY createdAt DESC LIMIT 1")
    fun getLatest(): Flow<DiaryEntry?>

    /** Total entry count (lightweight) */
    @Query("SELECT COUNT(*) FROM diary_entries")
    fun countAll(): Flow<Int>

    /** Count pending items */
    @Query("SELECT COUNT(*) FROM diary_entries WHERE status = 'PENDING'")
    fun countPending(): Flow<Int>

    /** Count completed today */
    @Query("SELECT COUNT(*) FROM diary_entries WHERE status = 'COMPLETED' AND completedAt >= :startOfDay")
    fun countCompletedToday(startOfDay: Long): Flow<Int>
}
