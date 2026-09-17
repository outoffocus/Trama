package com.trama.shared.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.trama.shared.model.Recording
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    fun getAll(): Flow<List<Recording>>

    @Query("SELECT * FROM recordings WHERE createdAt BETWEEN :start AND :end ORDER BY createdAt ASC")
    fun getByDateRange(start: Long, end: Long): Flow<List<Recording>>

    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<Recording>

    @Query(
        """
        SELECT * FROM recordings
        WHERE COALESCE(title, '') LIKE '%' || :query || '%' COLLATE NOCASE
           OR transcription LIKE '%' || :query || '%' COLLATE NOCASE
           OR COALESCE(summary, '') LIKE '%' || :query || '%' COLLATE NOCASE
           OR COALESCE(keyPoints, '') LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY createdAt DESC
        """
    )
    fun search(query: String): Flow<List<Recording>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    fun getById(id: Long): Flow<Recording?>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getByIdOnce(id: Long): Recording?

    @Insert
    suspend fun insert(recording: Recording): Long

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM recordings WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE recordings SET processingStatus = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("""UPDATE recordings
              SET audioFilePath = :audioFilePath, durationSeconds = :durationSeconds,
                  audioSampleRateHz = :audioSampleRateHz, processingStatus = :status
              WHERE id = :id""")
    suspend fun updateCapturedAudio(
        id: Long,
        audioFilePath: String,
        durationSeconds: Int,
        audioSampleRateHz: Int,
        status: String
    )

    @Query("""UPDATE recordings
              SET transcription = :transcription, durationSeconds = :durationSeconds,
                  processingStatus = :status, processedLocally = :processedLocally,
                  processedBy = :processedBy
              WHERE id = :id""")
    suspend fun updateTranscription(
        id: Long,
        transcription: String,
        durationSeconds: Int,
        status: String,
        processedLocally: Boolean,
        processedBy: String?
    )

    @Query("UPDATE recordings SET diarizationJson = :diarizationJson WHERE id = :id")
    suspend fun updateDiarization(id: Long, diarizationJson: String?)

    @Query("SELECT * FROM recordings WHERE processingStatus IN (:statuses) ORDER BY createdAt ASC")
    suspend fun getByStatuses(statuses: List<String>): List<Recording>

    @Query("""UPDATE recordings
              SET title = :title, summary = :summary, keyPoints = :keyPoints,
                  processingStatus = :status, processedLocally = :processedLocally,
                  processedBy = :processedBy
              WHERE id = :id""")
    suspend fun updateProcessingResult(
        id: Long, title: String, summary: String,
        keyPoints: String?, status: String,
        processedLocally: Boolean = false, processedBy: String? = null
    )

    @Query("""UPDATE recordings
              SET title = :title, summary = :summary, keyPoints = :keyPoints
              WHERE id = :id""")
    suspend fun updateNotes(id: Long, title: String?, summary: String?, keyPoints: String?)

    @Query("SELECT COUNT(*) FROM recordings")
    fun count(): Flow<Int>

    @Query("SELECT * FROM recordings WHERE isSynced = 0")
    suspend fun getUnsynced(): List<Recording>

    @Query("UPDATE recordings SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("SELECT EXISTS(SELECT 1 FROM recordings WHERE createdAt = :createdAt)")
    suspend fun existsByCreatedAt(createdAt: Long): Boolean

    @Query("SELECT * FROM recordings WHERE createdAt = :createdAt LIMIT 1")
    suspend fun getByCreatedAt(createdAt: Long): Recording?
}
