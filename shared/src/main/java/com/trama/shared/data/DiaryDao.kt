package com.trama.shared.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.trama.shared.model.DiaryEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {

    @Query("SELECT * FROM diary_entries WHERE id = :id")
    fun getById(id: Long): Flow<DiaryEntry?>

    @Query("SELECT * FROM diary_entries WHERE id = :id")
    suspend fun getByIdOnce(id: Long): DiaryEntry?

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

    /**
     * Pending tasks from other days visible on [dayEnd]:
     * created before [beforeDayStart] AND (no dueDate OR dueDate <= [dayEnd]).
     * Tasks explicitly postponed to the future (dueDate > dayEnd) are excluded.
     */
    @Query("""SELECT * FROM diary_entries
              WHERE status = 'PENDING'
              AND duplicateOfId IS NULL
              AND createdAt < :beforeDayStart
              AND (dueDate IS NULL OR dueDate <= :dayEnd)
              ORDER BY CASE priority
                WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1
                WHEN 'NORMAL' THEN 2 WHEN 'LOW' THEN 3 ELSE 4 END,
              CASE WHEN dueDate IS NOT NULL THEN dueDate ELSE createdAt END ASC""")
    fun getPendingFromOtherDays(beforeDayStart: Long, dayEnd: Long): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries WHERE createdAt BETWEEN :startTime AND :endTime ORDER BY createdAt DESC")
    fun byDateRange(startTime: Long, endTime: Long): Flow<List<DiaryEntry>>

    /** Entries completed within a time range (by completedAt), regardless of creation date */
    @Query("SELECT * FROM diary_entries WHERE status = 'COMPLETED' AND completedAt BETWEEN :startTime AND :endTime ORDER BY completedAt DESC")
    fun getCompletedByCompletedAt(startTime: Long, endTime: Long): Flow<List<DiaryEntry>>

    /** Tasks that were live/pending as of a given moment: created before that point, not yet completed by then */
    @Query("SELECT * FROM diary_entries WHERE status != 'DISCARDED' AND createdAt <= :dayEnd AND (completedAt IS NULL OR completedAt > :dayEnd) ORDER BY createdAt DESC")
    fun getPendingAsOf(dayEnd: Long): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries WHERE isSynced = 0")
    suspend fun getUnsynced(): List<DiaryEntry>

    /** All entries as a one-shot list (for full sync) */
    @Query("SELECT * FROM diary_entries ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<DiaryEntry>

    @Query("SELECT * FROM diary_entries WHERE text LIKE '%' || :query || '%' OR cleanText LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun search(query: String): Flow<List<DiaryEntry>>

    @Insert
    suspend fun insert(entry: DiaryEntry): Long

    @Delete
    suspend fun delete(entry: DiaryEntry): Int

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE diary_entries SET text = :text, cleanText = :text, correctedText = NULL WHERE id = :id")
    suspend fun updateText(id: Long, text: String)

    @Query("UPDATE diary_entries SET dueDate = :dueDate WHERE id = :id")
    suspend fun updateDueDate(id: Long, dueDate: Long?)

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

    /** Get only the most recent entry */
    @Query("SELECT * FROM diary_entries ORDER BY createdAt DESC LIMIT 1")
    fun getLatest(): Flow<DiaryEntry?>

    /** Get most recent pending entry (for watch home screen) */
    @Query("SELECT * FROM diary_entries WHERE status = 'PENDING' ORDER BY createdAt DESC LIMIT 1")
    fun getLatestPending(): Flow<DiaryEntry?>

    /** One-shot latest pending entry for transactional dedup checks */
    @Query("SELECT * FROM diary_entries WHERE status = 'PENDING' ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestPendingOnce(): DiaryEntry?

    /** Total entry count (lightweight) */
    @Query("SELECT COUNT(*) FROM diary_entries")
    fun countAll(): Flow<Int>

    /** Count pending items */
    @Query("SELECT COUNT(*) FROM diary_entries WHERE status = 'PENDING'")
    fun countPending(): Flow<Int>

    /** Count completed today */
    @Query("SELECT COUNT(*) FROM diary_entries WHERE status = 'COMPLETED' AND completedAt >= :startOfDay")
    fun countCompletedToday(startOfDay: Long): Flow<Int>

    /** Mark entry as duplicate of another */
    @Query("UPDATE diary_entries SET duplicateOfId = :originalId WHERE id = :id")
    suspend fun markDuplicate(id: Long, originalId: Long)

    /** Clear duplicate flag */
    @Query("UPDATE diary_entries SET duplicateOfId = NULL WHERE id = :id")
    suspend fun clearDuplicate(id: Long)

    /** Get all entries flagged as duplicates */
    @Query("SELECT * FROM diary_entries WHERE duplicateOfId IS NOT NULL AND status = 'PENDING' ORDER BY createdAt DESC")
    fun getDuplicates(): Flow<List<DiaryEntry>>

    /** Get recent pending entries for dedup comparison (last 50) */
    @Query("SELECT * FROM diary_entries WHERE status = 'PENDING' AND duplicateOfId IS NULL ORDER BY createdAt DESC LIMIT 50")
    suspend fun getRecentPendingForDedup(): List<DiaryEntry>

    /** Mark entry as completed by createdAt+text (for cross-device sync where IDs differ) */
    @Query("UPDATE diary_entries SET status = 'COMPLETED', completedAt = :completedAt WHERE createdAt = :createdAt AND text = :text")
    suspend fun markCompletedByKey(createdAt: Long, text: String, completedAt: Long = System.currentTimeMillis()): Int

    /** Delete entry by createdAt+text (for cross-device sync) */
    @Query("DELETE FROM diary_entries WHERE createdAt = :createdAt AND text = :text")
    suspend fun deleteByKey(createdAt: Long, text: String): Int

    /** Get action items extracted from a specific recording */
    @Query("SELECT * FROM diary_entries WHERE sourceRecordingId = :recordingId ORDER BY createdAt ASC")
    fun getByRecordingId(recordingId: Long): Flow<List<DiaryEntry>>

    /** Get action items extracted from a specific recording (one-shot, for dedup) */
    @Query("SELECT * FROM diary_entries WHERE sourceRecordingId = :recordingId ORDER BY createdAt ASC")
    suspend fun getByRecordingIdOnce(recordingId: Long): List<DiaryEntry>

    /** Completed entries since a given timestamp, newest first (for assistant context) */
    @Query("SELECT * FROM diary_entries WHERE status = 'COMPLETED' AND completedAt >= :since ORDER BY completedAt DESC")
    suspend fun getCompletedSince(since: Long): List<DiaryEntry>

    /** All pending entries, priority-sorted, one-shot (for assistant context) */
    @Query("""SELECT * FROM diary_entries WHERE status = 'PENDING' AND duplicateOfId IS NULL
              ORDER BY CASE priority
                WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1
                WHEN 'NORMAL' THEN 2 WHEN 'LOW' THEN 3 ELSE 4 END,
              createdAt DESC""")
    suspend fun getPendingOnce(): List<DiaryEntry>

    /** Delete all action items linked to a recording (for reprocessing) */
    @Query("DELETE FROM diary_entries WHERE sourceRecordingId = :recordingId")
    suspend fun deleteByRecordingId(recordingId: Long)
}
