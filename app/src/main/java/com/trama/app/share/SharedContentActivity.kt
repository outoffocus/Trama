package com.trama.app.share

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File

class SharedContentActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val audioUris = intent.sharedAudioUris()

        if (sharedText == null && audioUris.isEmpty()) {
            Toast.makeText(this, "No he encontrado contenido compatible", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        var enqueued = 0
        if (sharedText != null) {
            enqueueText(sharedText)
            enqueued++
        }

        audioUris.forEach { uri ->
            runCatching {
                val cached = copyToPrivateFiles(uri)
                enqueueAudio(cached)
                enqueued++
            }
        }

        Toast.makeText(
            this,
            if (enqueued == 1) "Guardando en Trama" else "Guardando $enqueued elementos en Trama",
            Toast.LENGTH_SHORT
        ).show()
        finish()
    }

    private fun enqueueText(text: String) {
        val work = OneTimeWorkRequestBuilder<SharedContentWorker>()
            .setInputData(
                Data.Builder()
                    .putString(SharedContentWorker.KEY_TEXT, text)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueue(work)
    }

    private fun enqueueAudio(file: File) {
        val work = OneTimeWorkRequestBuilder<SharedContentWorker>()
            .setInputData(
                Data.Builder()
                    .putString(SharedContentWorker.KEY_AUDIO_PATH, file.absolutePath)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueue(work)
    }

    private fun Intent.sharedAudioUris(): List<Uri> {
        return when (action) {
            Intent.ACTION_SEND -> listOfNotNull(streamUri()).filter { looksLikeAudio(it, type) }
            Intent.ACTION_SEND_MULTIPLE -> streamUris().filter { looksLikeAudio(it, type) }
            else -> emptyList()
        }
    }

    private fun Intent.streamUri(): Uri? =
        if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM)
        }

    private fun Intent.streamUris(): List<Uri> =
        if (Build.VERSION.SDK_INT >= 33) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            @Suppress("DEPRECATION")
            getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }

    private fun looksLikeAudio(uri: Uri, mimeType: String?): Boolean {
        if (mimeType?.startsWith("audio/") == true) return true
        val name = displayName(uri).lowercase()
        return listOf(".mp3", ".m4a", ".aac", ".wav", ".ogg", ".opus", ".amr", ".flac", ".3gp")
            .any(name::endsWith)
    }

    private fun copyToPrivateFiles(source: Uri): File {
        val dir = File(filesDir, "shared-audio").apply { mkdirs() }
        val safeName = displayName(source)
            .ifBlank { "audio-${System.currentTimeMillis()}" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val dest = File(dir, "${System.currentTimeMillis()}-$safeName")
        contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Cannot open shared audio" }
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return dest
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index).orEmpty()
            }
        }
        return uri.lastPathSegment.orEmpty()
    }
}
