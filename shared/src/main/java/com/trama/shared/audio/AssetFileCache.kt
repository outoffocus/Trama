package com.trama.shared.audio

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

        assetManager.open(normalized).use { input ->
            val assetLength = input.available().toLong()
            if (outFile.exists() && outFile.length() == assetLength) {
                return outFile.absolutePath
            }
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

    fun ensureDirectoryCopied(path: String): String {
        val normalized = path.trim('/').trim()
        require(normalized.isNotBlank()) { "Directory path cannot be blank" }
        copyDirectoryRecursively(normalized)
        return File(context.filesDir, normalized).absolutePath
    }

    private fun copyDirectoryRecursively(path: String) {
        val children = assetManager.list(path).orEmpty()
        if (children.isEmpty()) {
            // It's a file or an empty dir
            try {
                ensureCopied(path)
            } catch (_: Exception) {
                // Not a file, probably just an empty dir
                File(context.filesDir, path).mkdirs()
            }
            return
        }

        File(context.filesDir, path).mkdirs()
        children.forEach { child ->
            val childPath = if (path.isBlank()) child else "$path/$child"
            copyDirectoryRecursively(childPath)
        }
    }
}
