package com.trama.app.audio

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.io.FileOutputStream

/**
 * Copies bundled assets to app-private storage when a backend needs filesystem paths.
 */
class AssetFileCache(private val context: Context) {
    private val assetManager: AssetManager = context.assets

    fun listAssets(path: String): List<String> {
        val normalized = path.trim('/').trim()
        return assetManager.list(normalized)?.toList().orEmpty()
    }

    fun assetExists(path: String): Boolean {
        val normalized = path.trim('/').trim()
        if (normalized.isBlank()) return false

        val parent = normalized.substringBeforeLast('/', "")
        val filename = normalized.substringAfterLast('/')
        val entries = assetManager.list(parent) ?: return false
        return entries.contains(filename)
    }

    fun ensureCopied(path: String): String {
        val normalized = path.trim('/').trim()
        val outFile = File(context.filesDir, normalized)
        outFile.parentFile?.mkdirs()

        if (outFile.exists()) {
            val assetLength = assetManager.open(normalized).use { it.available() }
            if (outFile.length() == assetLength.toLong()) {
                return outFile.absolutePath
            }
        }

        assetManager.open(normalized).use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
        return outFile.absolutePath
    }

    fun ensureTextFile(path: String, contents: String): String {
        val normalized = path.trim('/').trim()
        val outFile = File(context.filesDir, normalized)
        outFile.parentFile?.mkdirs()
        outFile.writeText(contents)
        return outFile.absolutePath
    }
}
