package com.mydiary.app.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mydiary.app.ui.DatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * WorkManager worker that auto-exports diary entries to a user-selected file
 * (Google Drive, Downloads, etc.). The user creates the file once via CreateDocument,
 * and the worker overwrites it on each backup.
 */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "AutoBackupWorker"
        private const val PREFS = "backup"
        private const val KEY_FILE_URI = "backup_file_uri"
        private const val KEY_FILE_NAME = "backup_file_name"

        /** Save the user-selected backup file URI */
        fun setBackupFile(context: Context, uri: Uri, displayName: String?) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_FILE_URI, uri.toString())
                .putString(KEY_FILE_NAME, displayName)
                .apply()
        }

        /** Get the saved backup file URI, or null if not configured */
        fun getBackupFileUri(context: Context): Uri? {
            val str = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_FILE_URI, null)
            return str?.let { Uri.parse(it) }
        }

        /** Get display name of the backup file location */
        fun getBackupFileName(context: Context): String? {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_FILE_NAME, null)
        }

        /** Get last backup time, or null */
        fun getLastBackupTime(context: Context): Long? {
            val t = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong("last_backup", 0L)
            return if (t > 0) t else null
        }

        /** Get last backup entry count */
        fun getLastBackupCount(context: Context): Int {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt("last_backup_count", 0)
        }

        /** Get last error message, or null */
        fun getLastError(context: Context): String? {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("last_error", null)
        }

        private fun saveLastError(context: Context, message: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("last_error", message)
                .putLong("last_error_time", System.currentTimeMillis())
                .apply()
        }

        private fun clearLastError(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove("last_error").remove("last_error_time").apply()
        }

        /** Trigger an immediate one-time backup */
        fun runNow(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<AutoBackupWorker>()
                .build()
            androidx.work.WorkManager.getInstance(context).enqueue(request)
        }
    }

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Auto-backup starting...")

                val fileUri = getBackupFileUri(applicationContext)
                if (fileUri == null) {
                    Log.w(TAG, "No backup file configured, skipping")
                    saveLastError(applicationContext, "No hay archivo de backup configurado")
                    return@withContext Result.success()
                }

                // Check URI permissions
                try {
                    applicationContext.contentResolver.openOutputStream(fileUri, "w")?.close()
                } catch (e: SecurityException) {
                    Log.e(TAG, "Lost write permission to backup file: $fileUri", e)
                    saveLastError(applicationContext, "Permiso de escritura perdido. Reconfigura la ubicación del backup.")
                    return@withContext Result.failure()
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot write to backup file: $fileUri", e)
                    saveLastError(applicationContext, "No se puede escribir al archivo: ${e.message?.take(80)}")
                    return@withContext Result.failure()
                }

                val repository = DatabaseProvider.getRepository(applicationContext)
                val entries = repository.getAll().first()

                if (entries.isEmpty()) {
                    Log.i(TAG, "No entries to backup")
                    return@withContext Result.success()
                }

                val backupEntries = entries.map { entry ->
                    BackupManager.BackupEntry(
                        text = entry.text,
                        keyword = entry.keyword,
                        category = entry.category,
                        confidence = entry.confidence,
                        source = entry.source.name,
                        duration = entry.duration,
                        createdAt = entry.createdAt,
                        correctedText = entry.correctedText,
                        wasReviewedByLLM = entry.wasReviewedByLLM,
                        llmConfidence = entry.llmConfidence,
                        status = entry.status,
                        actionType = entry.actionType,
                        cleanText = entry.cleanText,
                        dueDate = entry.dueDate,
                        completedAt = entry.completedAt,
                        priority = entry.priority,
                        isManual = entry.isManual
                    )
                }

                val recordings = try { repository.getAllRecordingsOnce() } catch (_: Exception) { emptyList() }
                val backupRecordings = recordings.map { rec ->
                    BackupManager.BackupRecording(
                        title = rec.title,
                        transcription = rec.transcription,
                        summary = rec.summary,
                        keyPoints = rec.keyPoints,
                        durationSeconds = rec.durationSeconds,
                        source = rec.source.name,
                        createdAt = rec.createdAt,
                        processingStatus = rec.processingStatus,
                        processedLocally = rec.processedLocally
                    )
                }

                val backup = BackupManager.Backup(entries = backupEntries, recordings = backupRecordings)
                val jsonStr = json.encodeToString(backup)

                saveToFile(fileUri, jsonStr)

                val totalCount = entries.size + recordings.size
                applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putLong("last_backup", System.currentTimeMillis())
                    .putInt("last_backup_count", totalCount)
                    .apply()

                clearLastError(applicationContext)
                Log.i(TAG, "Auto-backup complete: ${entries.size} entries + ${recordings.size} recordings")
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Auto-backup failed", e)
                saveLastError(applicationContext, "Error: ${e.message?.take(100)}")
                Result.retry()
            }
        }
    }

    /**
     * Overwrite the user-selected backup file with new data.
     */
    private fun saveToFile(fileUri: Uri, jsonStr: String) {
        val resolver = applicationContext.contentResolver
        resolver.openOutputStream(fileUri, "w")?.use { stream ->
            stream.write(jsonStr.toByteArray(Charsets.UTF_8))
        } ?: throw Exception("Cannot write to backup file")

        Log.i(TAG, "Backup saved to $fileUri")
    }
}
