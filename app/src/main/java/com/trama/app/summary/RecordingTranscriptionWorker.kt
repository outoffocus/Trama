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
import com.trama.app.audio.PcmRecordingStorage
import com.trama.app.audio.PcmRecordingTranscriber
import com.trama.app.audio.RecordingTranscriptionCheckpointStore
import com.trama.app.audio.SherpaMeetingDiarizer
import com.trama.app.audio.SherpaWhisperAsrEngine
import com.trama.app.audio.SpeakerTurnAligner
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.RecordingStatus
import com.trama.shared.model.SpeakerTurns
import kotlinx.coroutines.CancellationException

class RecordingTranscriptionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "RecordingTranscription"
        private const val KEY_RECORDING_ID = "recording_id"
        private const val NOTIFICATION_ID_BASE = 3_000

        fun workName(recordingId: Long) = "recording-transcription-$recordingId"

        fun enqueue(context: Context, recordingId: Long) {
            enqueue(context, recordingId, ExistingWorkPolicy.KEEP)
        }

        /** Explicit user retry also resets WorkManager's accumulated backoff. */
        fun retry(context: Context, recordingId: Long) {
            enqueue(context, recordingId, ExistingWorkPolicy.REPLACE)
        }

        private fun enqueue(context: Context, recordingId: Long, policy: ExistingWorkPolicy) {
            val request = OneTimeWorkRequestBuilder<RecordingTranscriptionWorker>()
                .setInputData(Data.Builder().putLong(KEY_RECORDING_ID, recordingId).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(recordingId),
                policy,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val recordingId = inputData.getLong(KEY_RECORDING_ID, -1L)
        if (recordingId <= 0L) return Result.failure()
        val repository = DatabaseProvider.getRepository(applicationContext)
        val recording = repository.getRecordingByIdOnce(recordingId) ?: return Result.failure()
        val sourceFile = PcmRecordingStorage.resolveManagedFile(
            applicationContext,
            recording.audioFilePath
        ) ?: run {
            repository.updateRecordingStatus(recordingId, RecordingStatus.FAILED)
            return Result.failure()
        }
        val audioFile = runCatching { PcmRecordingStorage.finalizePending(sourceFile) }
            .getOrElse { error -> return retryOrFail(recordingId, error) }
        val sampleRateHz = recording.audioSampleRateHz.coerceIn(8_000, 48_000)
        val duration = PcmRecordingStorage.durationSeconds(audioFile, sampleRateHz)
        repository.updateCapturedRecordingAudio(
            recordingId,
            audioFile.absolutePath,
            duration,
            RecordingStatus.TRANSCRIBING,
            sampleRateHz
        )

        runCatching { setForeground(createForegroundInfo(recordingId)) }
            .onFailure { Log.w(TAG, "Could not promote transcription to foreground", it) }

        val asrEngine = SherpaWhisperAsrEngine(applicationContext)
        if (!asrEngine.isAvailable) {
            return retryOrFail(recordingId, IllegalStateException("Offline ASR unavailable"))
        }
        return try {
            val expectedChunks = PcmRecordingTranscriber.expectedChunkCount(audioFile, sampleRateHz)
            val checkpoint = RecordingTranscriptionCheckpointStore.load(
                audioFile,
                sampleRateHz,
                expectedChunks
            )
            if (checkpoint != null) {
                Log.i(
                    TAG,
                    "Resuming recording $recordingId at chunk ${checkpoint.nextChunkIndex}/$expectedChunks"
                )
            }
            val result = PcmRecordingTranscriber(asrEngine).transcribe(
                file = audioFile,
                sampleRateHz = sampleRateHz,
                resumeFrom = checkpoint,
                onCheckpoint = { progress ->
                    RecordingTranscriptionCheckpointStore.save(audioFile, progress)
                }
            )
            if (result.text.isBlank() || result.rejectReason != null) {
                repository.updateRecordingStatus(recordingId, RecordingStatus.FAILED)
                RecordingTranscriptionCheckpointStore.clear(audioFile)
                Log.w(TAG, "Recording $recordingId has no useful speech: ${result.rejectReason}")
                Result.failure()
            } else {
                repository.updateRecordingTranscription(
                    id = recordingId,
                    transcription = result.text,
                    durationSeconds = duration,
                    status = RecordingStatus.PENDING,
                    processedLocally = true,
                    processedBy = asrEngine.name
                )
                val diarizationJson = try {
                    val diarizer = SherpaMeetingDiarizer(applicationContext)
                    if (diarizer.isAvailable) {
                        val spans = diarizer.diarize(audioFile, sampleRateHz)
                        SpeakerTurns.encode(SpeakerTurnAligner.align(result.segments, spans))
                    } else {
                        Log.w(TAG, "Speaker diarization assets are unavailable")
                        null
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    // The transcript is still valuable if a device ABI does not expose
                    // Sherpa's optional diarization native symbols.
                    Log.w(TAG, "Diarization failed for recording $recordingId", error)
                    null
                }
                repository.updateRecordingDiarization(recordingId, diarizationJson)
                RecordingTranscriptionCheckpointStore.clear(audioFile)
                RecordingProcessorWorker.enqueue(applicationContext, recordingId)
                Log.i(TAG, "Recording $recordingId transcribed from durable PCM")
                Result.success()
            }
        } catch (cancelled: CancellationException) {
            Log.i(TAG, "Transcription paused by Android for recording $recordingId; progress preserved")
            throw cancelled
        } catch (error: Exception) {
            retryOrFail(recordingId, error)
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
            .setContentTitle("Transcribiendo reunión")
            .setContentText("Transcripción y separación de interlocutores en el dispositivo")
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

    private suspend fun retryOrFail(recordingId: Long, error: Throwable): Result {
        Log.e(TAG, "Transcription failed for recording $recordingId", error)
        return if (runAttemptCount < 3) {
            Result.retry()
        } else {
            DatabaseProvider.getRepository(applicationContext)
                .updateRecordingStatus(recordingId, RecordingStatus.FAILED)
            Result.failure()
        }
    }
}
