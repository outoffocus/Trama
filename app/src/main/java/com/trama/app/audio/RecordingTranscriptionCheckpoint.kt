package com.trama.app.audio

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
data class RecordingTranscriptionCheckpoint(
    val sourceLength: Long,
    val sampleRateHz: Int,
    val nextChunkIndex: Int,
    val acceptedText: List<String>,
    val totalDecodeMs: Long,
    val acceptedChunks: Int,
    val rejectedChunks: Int,
    val rejectReasons: List<String>,
    val filterVersion: Int = 1
) {
    companion object {
        const val CURRENT_FILTER_VERSION = 2
    }
}

/** Durable progress for recordings whose offline transcription takes many minutes. */
object RecordingTranscriptionCheckpointStore {
    private val json = Json { ignoreUnknownKeys = true }
    private const val SUFFIX = ".transcription-progress.json"

    fun load(audioFile: File, sampleRateHz: Int, expectedChunks: Int): RecordingTranscriptionCheckpoint? {
        val checkpointFile = fileFor(audioFile)
        if (!checkpointFile.isFile) return null
        return runCatching {
            json.decodeFromString<RecordingTranscriptionCheckpoint>(checkpointFile.readText())
        }.getOrNull()?.takeIf {
            it.sourceLength == audioFile.length() &&
                it.sampleRateHz == sampleRateHz &&
                it.nextChunkIndex in 0..expectedChunks &&
                it.filterVersion == RecordingTranscriptionCheckpoint.CURRENT_FILTER_VERSION
        }
    }

    fun save(audioFile: File, checkpoint: RecordingTranscriptionCheckpoint) {
        val target = fileFor(audioFile)
        val pending = File(target.parentFile, "${target.name}.part")
        pending.writeText(json.encodeToString(checkpoint))
        runCatching {
            Files.move(
                pending.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        }.recoverCatching {
            Files.move(pending.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.getOrThrow()
    }

    fun clear(audioFile: File) {
        fileFor(audioFile).delete()
        File(fileFor(audioFile).parentFile, "${fileFor(audioFile).name}.part").delete()
    }

    internal fun fileFor(audioFile: File): File =
        File(audioFile.parentFile, audioFile.name + SUFFIX)
}
