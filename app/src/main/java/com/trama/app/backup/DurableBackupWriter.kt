package com.trama.app.backup

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Stages a complete, validated backup locally before replacing an SAF document. */
object DurableBackupWriter {
    private const val DIRECTORY = "backups"
    private const val PENDING_NAME = "latest-backup.json.part"
    private const val LAST_GOOD_NAME = "latest-backup.json"
    private val lock = Any()

    fun write(context: Context, uri: Uri, json: String) = synchronized(lock) {
        require(json.isNotBlank()) { "Backup payload is empty" }
        BackupManager.decode(json)

        val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        val pending = File(directory, PENDING_NAME)
        FileOutputStream(pending, false).use { output ->
            output.write(json.toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }

        val destination = runCatching {
            context.contentResolver.openOutputStream(uri, "rwt")
        }.getOrNull() ?: context.contentResolver.openOutputStream(uri, "w")
        destination?.use { output ->
            pending.inputStream().use { input -> input.copyTo(output) }
            output.flush()
        } ?: error("Cannot open backup output stream")

        val lastGood = File(directory, LAST_GOOD_NAME)
        runCatching {
            Files.move(
                pending.toPath(),
                lastGood.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        }.recoverCatching {
            Files.move(
                pending.toPath(),
                lastGood.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }.getOrThrow()
    }
}
