package com.trama.app.summary

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.trama.app.NotificationConfig
import com.trama.app.R
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.RecordingStatus
import kotlinx.coroutines.CancellationException

/**
 * WorkManager worker that processes a recording locally.
 * Survives service/activity destruction — guaranteed to complete.
 */
class RecordingProcessorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "RecordingProcessorWorker"
        private const val KEY_RECORDING_ID = "recording_id"
        private const val NOTIFICATION_ID_BASE = 4_000

        fun workName(recordingId: Long) = "recording-processing-$recordingId"

        fun enqueue(context: Context, recordingId: Long) {
            enqueue(context, recordingId, ExistingWorkPolicy.KEEP)
        }

        fun retry(context: Context, recordingId: Long) {
            enqueue(context, recordingId, ExistingWorkPolicy.REPLACE)
        }

        private fun enqueue(context: Context, recordingId: Long, policy: ExistingWorkPolicy) {
            val data = Data.Builder()
                .putLong(KEY_RECORDING_ID, recordingId)
                .build()

            val request = OneTimeWorkRequestBuilder<RecordingProcessorWorker>()
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(recordingId),
                policy,
                request
            )
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
            runCatching { setForeground(createForegroundInfo(recordingId)) }
                .onFailure { Log.w(TAG, "Could not promote meeting analysis to foreground", it) }
            RecordingProcessor(applicationContext).process(recordingId, repository)
            Log.i(TAG, "Recording $recordingId processed successfully")
            Result.success()
        } catch (cancelled: CancellationException) {
            Log.i(TAG, "Meeting analysis paused by Android for recording $recordingId")
            throw cancelled
        } catch (e: Exception) {
            Log.e(TAG, "Processing failed for recording $recordingId", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                val repository = DatabaseProvider.getRepository(applicationContext)
                if (repository.getRecordingByIdOnce(recordingId)?.transcription?.isNotBlank() == true) {
                    repository.updateRecordingStatus(recordingId, RecordingStatus.TRANSCRIPT_ONLY)
                }
                Result.failure()
            }
        }
    }

    private fun createForegroundInfo(recordingId: Long): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    NotificationConfig.CHANNEL_TRANSCRIPTION,
                    "Transcripción de reuniones",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Procesamiento local de reuniones largas" }
            )
        }
        val notification = NotificationCompat.Builder(
            applicationContext,
            NotificationConfig.CHANNEL_TRANSCRIPTION
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Analizando reunión")
            .setContentText("Extrayendo resumen y acciones en este dispositivo")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        val notificationId = NOTIFICATION_ID_BASE + (recordingId % 900).toInt()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }
}
