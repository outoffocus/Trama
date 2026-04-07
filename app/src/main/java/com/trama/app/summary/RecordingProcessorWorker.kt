package com.trama.app.summary

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.trama.shared.data.DatabaseProvider

/**
 * WorkManager worker that processes a recording with Gemini.
 * Survives service/activity destruction — guaranteed to complete.
 */
class RecordingProcessorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "RecordingProcessorWorker"
        private const val KEY_RECORDING_ID = "recording_id"

        fun enqueue(context: Context, recordingId: Long) {
            val data = Data.Builder()
                .putLong(KEY_RECORDING_ID, recordingId)
                .build()

            val request = OneTimeWorkRequestBuilder<RecordingProcessorWorker>()
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueue(request)
            Log.i(TAG, "Enqueued processing for recording $recordingId")
        }
    }

    override suspend fun doWork(): Result {
        val recordingId = inputData.getLong(KEY_RECORDING_ID, -1)
        if (recordingId == -1L) {
            Log.w(TAG, "No recording ID provided")
            return Result.failure()
        }

        return try {
            val repository = DatabaseProvider.getRepository(applicationContext)
            RecordingProcessor(applicationContext).process(recordingId, repository)
            Log.i(TAG, "Recording $recordingId processed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Processing failed for recording $recordingId", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
