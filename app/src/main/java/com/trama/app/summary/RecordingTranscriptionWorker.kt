package com.trama.app.summary

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.trama.app.audio.PcmRecordingStorage
import com.trama.app.audio.PcmRecordingTranscriber
import com.trama.app.audio.SherpaWhisperAsrEngine
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.RecordingStatus

class RecordingTranscriptionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "RecordingTranscription"
        private const val KEY_RECORDING_ID = "recording_id"

        fun enqueue(context: Context, recordingId: Long) {
            val request = OneTimeWorkRequestBuilder<RecordingTranscriptionWorker>()
                .setInputData(Data.Builder().putLong(KEY_RECORDING_ID, recordingId).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "recording-transcription-$recordingId",
                ExistingWorkPolicy.KEEP,
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

        val asrEngine = SherpaWhisperAsrEngine(applicationContext)
        if (!asrEngine.isAvailable) {
            return retryOrFail(recordingId, IllegalStateException("Offline ASR unavailable"))
        }
        return try {
            val result = PcmRecordingTranscriber(asrEngine).transcribe(audioFile, sampleRateHz)
            if (result.text.isBlank() || result.rejectReason != null) {
                repository.updateRecordingStatus(recordingId, RecordingStatus.FAILED)
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
                RecordingProcessorWorker.enqueue(applicationContext, recordingId)
                Log.i(TAG, "Recording $recordingId transcribed from durable PCM")
                Result.success()
            }
        } catch (error: Exception) {
            retryOrFail(recordingId, error)
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
