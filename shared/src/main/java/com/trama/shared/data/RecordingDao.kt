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

    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<Recording>

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
              SET title = :title, summary = :summary, keyPoints = :keyPoints,
                  processingStatus = :status, processedLocally = :processedLocally,
                  processedBy = :processedBy
              WHERE id = :id""")
    suspend fun updateProcessingResult(
        id: Long, title: String, summary: String,
        keyPoints: String?, status: String,
        processedLocally: Boolean = false, processedBy: String? = null
    )

    @Query("SELECT COUNT(*) FROM recordings")
    fun count(): Flow<Int>

    @Query("SELECT * FROM recordings WHERE isSynced = 0")
    suspend fun getUnsynced(): List<Recording>

    @Query("UPDATE recordings SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("SELECT EXISTS(SELECT 1 FROM recordings WHERE createdAt = :createdAt)")
    suspend fun existsByCreatedAt(createdAt: Long): Boolean
}
