package com.mydiary.app.speech

import android.content.Context
import android.util.Log
import org.vosk.Model
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

object VoskModelManager {

    private const val TAG = "VoskModelManager"
    private const val MODEL_DIR = "model-es"

    @Volatile
    private var model: Model? = null

    fun getModel(): Model? = model

    fun isLoaded(): Boolean = model != null

    fun load(context: Context, onReady: (Model) -> Unit, onError: (Exception) -> Unit) {
        model?.let {
            onReady(it)
            return
        }

        val targetDir = File(context.filesDir, MODEL_DIR)

        Executors.newSingleThreadExecutor().execute {
            try {
                if (!targetDir.exists() || targetDir.listFiles()?.isEmpty() != false) {
                    extractAssets(context, MODEL_DIR, targetDir)
                }
                val m = Model(targetDir.absolutePath)
                model = m
                Log.i(TAG, "Model loaded successfully")
                android.os.Handler(context.mainLooper).post { onReady(m) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model", e)
                targetDir.deleteRecursively()
                android.os.Handler(context.mainLooper).post { onError(e) }
            }
        }
    }

    private fun extractAssets(context: Context, assetDir: String, targetDir: File) {
        val assets = context.assets
        val files = assets.list(assetDir) ?: return

        targetDir.mkdirs()

        for (file in files) {
            val assetPath = "$assetDir/$file"
            val targetFile = File(targetDir, file)

            // Check if it's a directory by trying to list its contents
            val subFiles = assets.list(assetPath)
            if (subFiles != null && subFiles.isNotEmpty()) {
                extractAssets(context, assetPath, targetFile)
            } else {
                assets.open(assetPath).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
        Log.d(TAG, "Extracted: $assetDir -> ${targetDir.absolutePath}")
    }

    fun release() {
        model?.close()
        model = null
        Log.i(TAG, "Model released")
    }
}
