package com.trama.app.summary

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.trama.app.audio.PcmRecordingStorage
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.RecordingStatus

/** Recovers PCM files left behind if the recording process was killed. */
class RecordingRecoveryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "RecordingRecovery"
        private const val UNIQUE_WORK = "recording-recovery"

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<RecordingRecoveryWorker>().build()
            )
        }
    }

    override suspend fun doWork(): Result {
        val repository = DatabaseProvider.getRepository(applicationContext)
        val interrupted = repository.getRecordingsByStatuses(
            listOf(RecordingStatus.CAPTURING, RecordingStatus.TRANSCRIBING)
        )
        interrupted.forEach { recording ->
            val file = PcmRecordingStorage.resolveManagedFile(
                applicationContext,
                recording.audioFilePath
            )
            if (file == null || file.length() <= 0L) {
                repository.updateRecordingStatus(recording.id, RecordingStatus.FAILED)
                Log.w(TAG, "Recording ${recording.id} has no recoverable PCM")
                return@forEach
            }
            val finalized = runCatching { PcmRecordingStorage.finalizePending(file) }
                .getOrElse { error ->
                    Log.e(TAG, "Could not finalize recording ${recording.id}", error)
                    return@forEach
                }
            repository.updateCapturedRecordingAudio(
                id = recording.id,
                audioFilePath = finalized.absolutePath,
                durationSeconds = PcmRecordingStorage.durationSeconds(
                    finalized,
                    recording.audioSampleRateHz
                ),
                status = RecordingStatus.TRANSCRIBING,
                audioSampleRateHz = recording.audioSampleRateHz
            )
            RecordingTranscriptionWorker.enqueue(applicationContext, recording.id)
        }
        return Result.success()
    }
}
