package com.trama.wear.audio

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object WatchRecordingFileStore {
    private const val DIRECTORY = "watch-recordings"
    private const val PENDING_SUFFIX = ".part"

    data class StoredCapture(val file: File, val kind: String, val createdAt: Long)

    fun createPending(context: Context, kind: String, createdAt: Long): File {
        val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        return File(directory, "$kind--$createdAt.pcm$PENDING_SUFFIX")
    }

    fun finalize(file: File): File {
        if (!file.name.endsWith(PENDING_SUFFIX)) return file
        val target = File(file.parentFile, file.name.removeSuffix(PENDING_SUFFIX))
        runCatching {
            Files.move(
                file.toPath(), target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        }.recoverCatching {
            Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.getOrThrow()
        return target
    }

    fun recoverable(context: Context): List<StoredCapture> {
        val directory = File(context.filesDir, DIRECTORY)
        return directory.listFiles().orEmpty().mapNotNull { file ->
            val base = file.name.removeSuffix(PENDING_SUFFIX).removeSuffix(".pcm")
            val separator = base.lastIndexOf("--")
            if (separator <= 0) return@mapNotNull null
            val kind = base.substring(0, separator)
            val createdAt = base.substring(separator + 2).toLongOrNull() ?: return@mapNotNull null
            StoredCapture(file, kind, createdAt)
        }.sortedBy { it.createdAt }
    }
}
