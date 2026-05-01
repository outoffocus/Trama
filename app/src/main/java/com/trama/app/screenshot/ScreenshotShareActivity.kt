package com.trama.app.screenshot

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File

class ScreenshotShareActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val imageUri = intent.sharedImageUri()
        if (imageUri == null) {
            Toast.makeText(this, "No he encontrado ninguna imagen para procesar", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val cachedUri = try {
            copyToPrivateCache(imageUri)
        } catch (_: Exception) {
            Toast.makeText(this, "No he podido preparar la captura", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val work = OneTimeWorkRequestBuilder<ScreenshotActionWorker>()
            .setInputData(
                Data.Builder()
                    .putString(ScreenshotActionWorker.KEY_IMAGE_URI, cachedUri.toString())
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueue(work)

        Toast.makeText(this, "Procesando captura en Trama", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun Intent.sharedImageUri(): Uri? {
        if (action != Intent.ACTION_SEND) return null
        return getParcelableExtra(Intent.EXTRA_STREAM)
    }

    private fun copyToPrivateCache(source: Uri): Uri {
        // Use filesDir, not cacheDir: WorkManager persists tasks across restarts and the
        // system can evict cacheDir at any time, causing FileNotFoundException on retry.
        val dir = File(filesDir, "screenshots").apply { mkdirs() }
        val dest = File(dir, "shared-${System.currentTimeMillis()}.jpg")
        contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Cannot open shared screenshot" }
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return Uri.fromFile(dest)
    }
}
