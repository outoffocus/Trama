package com.trama.app.summary

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Manages download and deletion of the local LLM model file.
 * URL, token, and filename are user-configurable via SharedPreferences.
 */
class GemmaModelManager(private val context: Context) {

    sealed class DownloadState {
        object NotDownloaded : DownloadState()
        data class Downloading(val progress: Int) : DownloadState()
        object Downloaded : DownloadState()
        data class Failed(val message: String) : DownloadState()
    }

    companion object {
        private const val TAG = "GemmaModelManager"
        private const val PREFS_NAME = "gemma_model"

        private const val KEY_MODEL_URL = "model_url"
        private const val KEY_HF_TOKEN = "hf_token"

        const val DEFAULT_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"

        fun getPrefs(context: Context) =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun getModelUrl(context: Context): String =
            getPrefs(context).getString(KEY_MODEL_URL, DEFAULT_URL) ?: DEFAULT_URL

        fun setModelUrl(context: Context, url: String) {
            getPrefs(context).edit().putString(KEY_MODEL_URL, url.trim()).apply()
        }

        fun getHfToken(context: Context): String =
            getPrefs(context).getString(KEY_HF_TOKEN, "") ?: ""

        fun setHfToken(context: Context, token: String) =
            getPrefs(context).edit().putString(KEY_HF_TOKEN, token.trim()).apply()

        /** Extracts filename from the URL's last path segment (e.g. "gemma-4-E2B-it.litertlm"). */
        fun filenameFromUrl(url: String): String {
            val lastSegment = url.trim().trimEnd('/').substringAfterLast('/').substringBefore('?')
            return if (lastSegment.isNotBlank() && lastSegment.contains('.')) lastSegment
                   else GemmaClient.DEFAULT_FILENAME
        }

        fun getModelFilename(context: Context): String =
            filenameFromUrl(getModelUrl(context))

        /** Destination associated with the URL currently configured by the user. */
        fun getConfiguredModelFile(context: Context): File =
            File(context.filesDir, getModelFilename(context))

        /**
         * Returns the configured model or recovers an already-installed compatible model.
         *
         * The fallback is important after changing the app default: older versions installed
         * `gemma3-1b-it-int4.task`, while current versions default to a `.litertlm` bundle. The
         * model must not become invisible merely because the default URL (and filename) changed.
         */
        fun findInstalledModelFile(context: Context): File? =
            LocalModelFileResolver.find(
                configuredFile = getConfiguredModelFile(context),
                filesInDirectory = context.filesDir.listFiles().orEmpty()
            )
    }

    private val _state = MutableStateFlow<DownloadState>(
        if (GemmaClient.isModelDownloaded(context)) DownloadState.Downloaded
        else DownloadState.NotDownloaded
    )
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private var downloadId: Long = -1
    private var progressJob: Job? = null

    fun refreshState() {
        _state.value = if (GemmaClient.isModelDownloaded(context))
            DownloadState.Downloaded else DownloadState.NotDownloaded
    }

    fun startDownload() {
        if (_state.value is DownloadState.Downloading) return

        val url = getModelUrl(context)
        val hfToken = getHfToken(context)
        val filename = getModelFilename(context)

        if (url.isBlank()) {
            _state.value = DownloadState.Failed("URL del modelo no configurada")
            return
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        // Download to cache first (DownloadManager can't write to internal storage)
        val tempFile = File(context.externalCacheDir ?: context.cacheDir, filename)
        if (tempFile.exists()) tempFile.delete()

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Descargando modelo local")
            .setDescription(filename)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(tempFile))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        // Add HuggingFace auth header if token is provided
        if (hfToken.isNotBlank()) {
            request.addRequestHeader("Authorization", "Bearer $hfToken")
            Log.i(TAG, "Using HuggingFace token for download")
        }

        downloadId = dm.enqueue(request)
        _state.value = DownloadState.Downloading(0)
        Log.i(TAG, "Download started: id=$downloadId url=$url")

        // Poll progress and handle completion directly (BroadcastReceiver is unreliable on Android 13+)
        progressJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                delay(500)
                val result = queryDownloadState(dm, tempFile)
                if (result != null) {
                    // Terminal state reached
                    _state.value = result
                    break
                }
            }
        }
    }

    fun cancelDownload() {
        if (downloadId != -1L) {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.remove(downloadId)
            downloadId = -1
        }
        progressJob?.cancel()
        _state.value = DownloadState.NotDownloaded
        Log.i(TAG, "Download cancelled")
    }

    fun deleteModel() {
        GemmaClient.release()
        val file = GemmaClient.getModelFile(context)
        if (file.exists()) {
            file.delete()
            Log.i(TAG, "Model deleted")
        }
        _state.value = DownloadState.NotDownloaded
    }

    fun getModelSizeMB(): Long {
        val file = GemmaClient.getModelFile(context)
        return if (file.exists()) file.length() / (1024 * 1024) else 0
    }

    /**
     * Polls DownloadManager for current state.
     * Returns null if still downloading (and updates progress), or a terminal DownloadState.
     */
    private fun queryDownloadState(dm: DownloadManager, tempFile: File): DownloadState? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = dm.query(query) ?: return DownloadState.Failed("Descarga no encontrada")
        return try {
            if (!cursor.moveToFirst()) return DownloadState.Failed("Descarga no encontrada")

            val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val status = cursor.getInt(statusCol)

            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    // Install: copy from cache to internal storage
                    try {
                        // Always install the download under the configured filename. The active
                        // model resolver may currently be exposing a compatible legacy file.
                        val dest = getConfiguredModelFile(context)
                        tempFile.copyTo(dest, overwrite = true)
                        tempFile.delete()
                        Log.i(TAG, "Model installed: ${dest.length() / (1024 * 1024)} MB")
                        DownloadState.Downloaded
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to install model", e)
                        DownloadState.Failed("Error instalando: ${e.message}")
                    }
                }
                DownloadManager.STATUS_FAILED -> {
                    val reasonCol = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                    val reason = cursor.getInt(reasonCol)
                    Log.e(TAG, "Download failed: reason=$reason")
                    tempFile.delete()
                    val msg = when (reason) {
                        DownloadManager.ERROR_HTTP_DATA_ERROR -> "Error de datos HTTP"
                        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Código HTTP no soportado (¿token incorrecto?)"
                        DownloadManager.ERROR_CANNOT_RESUME -> "No se puede reanudar"
                        404 -> "Archivo no encontrado (URL incorrecta)"
                        401, 403 -> "No autorizado (¿falta token HuggingFace?)"
                        else -> "Descarga fallida (código $reason)"
                    }
                    DownloadState.Failed(msg)
                }
                else -> {
                    // Still running or paused — update progress
                    val bytesCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val bytes = cursor.getLong(bytesCol)
                    val total = cursor.getLong(totalCol)
                    if (total > 0) {
                        _state.value = DownloadState.Downloading(((bytes * 100) / total).toInt())
                    }
                    null // not terminal
                }
            }
        } finally {
            cursor.close()
        }
    }
}

/** Pure file selection policy, kept independent from Android so migrations stay unit-testable. */
internal object LocalModelFileResolver {
    private val supportedExtensions = setOf("task", "litertlm")

    fun find(configuredFile: File, filesInDirectory: Array<out File>): File? {
        if (configuredFile.isUsableLocalModel()) return configuredFile

        return filesInDirectory
            .asSequence()
            .filter { it.isUsableLocalModel() }
            // Recovery is only for Gemma migrations. A generic `.task` file could belong
            // to another MediaPipe feature and must never be loaded as an LLM by accident.
            .filter { it.nameWithoutExtension.lowercase().contains("gemma") }
            .maxWithOrNull(compareBy<File> { it.lastModified() }.thenBy { it.name })
    }

    private fun File.isUsableLocalModel(): Boolean =
        isFile && length() > 0L && extension.lowercase() in supportedExtensions
}
