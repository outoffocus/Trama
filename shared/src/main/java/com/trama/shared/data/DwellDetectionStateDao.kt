package com.trama.shared.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.trama.shared.model.DwellDetectionState
import kotlinx.coroutines.flow.Flow

@Dao
interface DwellDetectionStateDao {

    @Query("SELECT * FROM dwell_detection_state WHERE id = 1")
    fun observe(): Flow<DwellDetectionState?>

    @Query("SELECT * FROM dwell_detection_state WHERE id = 1")
    suspend fun get(): DwellDetectionState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(state: DwellDetectionState)

    @Query("DELETE FROM dwell_detection_state WHERE id = 1")
    suspend fun clear()
}
