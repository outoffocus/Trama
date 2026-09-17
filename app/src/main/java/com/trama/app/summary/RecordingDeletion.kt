package com.trama.app.summary

import android.content.Context
import androidx.work.WorkManager
import com.trama.app.audio.PcmRecordingStorage
import com.trama.app.audio.RecordingTranscriptionCheckpointStore
import com.trama.shared.data.DiaryRepository
import java.io.File

/** One durable deletion path for every recording surface. */
object RecordingDeletion {
    suspend fun delete(
        context: Context,
        repository: DiaryRepository,
        recordingIds: Collection<Long>,
        additionalRoomChanges: suspend DiaryRepository.() -> Unit = {}
    ) {
        val ids = recordingIds.distinct().filter { it > 0 }
        if (ids.isEmpty()) return

        val workManager = WorkManager.getInstance(context)
        ids.forEach { id ->
            workManager.cancelUniqueWork(RecordingTranscriptionWorker.workName(id))
            workManager.cancelUniqueWork(RecordingProcessorWorker.workName(id))
        }

        // Rename first so a failed Room commit can restore the audio. Deleting
        // after the commit makes an interrupted cleanup harmless and retryable.
        val staged = mutableListOf<Pair<File, File>>()
        try {
            ids.mapNotNull { repository.getRecordingByIdOnce(it) }.forEach { recording ->
                val source = PcmRecordingStorage.resolveManagedFile(context, recording.audioFilePath)
                if (source != null && source.exists()) {
                    val trash = File(source.parentFile, "${source.name}.deleting-${recording.id}")
                    check(source.renameTo(trash)) { "No se pudo preparar el borrado del audio" }
                    staged += source to trash
                }
            }
            repository.withTransaction {
                additionalRoomChanges()
                deleteRecordingsByIds(ids)
            }
        } catch (error: Throwable) {
            staged.asReversed().forEach { (source, trash) ->
                if (trash.exists()) trash.renameTo(source)
            }
            throw error
        }
        staged.forEach { (source, trash) ->
            trash.delete()
            RecordingTranscriptionCheckpointStore.clear(source)
        }
    }
}
