package com.trama.app.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.trama.shared.data.DatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "backup-now",
                androidx.work.ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

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

                val repository = DatabaseProvider.getRepository(applicationContext)
                val backup = BackupManager.createBackup(repository)
                DurableBackupWriter.write(
                    applicationContext,
                    fileUri,
                    BackupManager.encode(backup)
                )

                val totalCount = BackupManager.entityCount(backup)
                applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putLong("last_backup", System.currentTimeMillis())
                    .putInt("last_backup_count", totalCount)
                    .apply()

                clearLastError(applicationContext)
                Log.i(TAG, "Auto-backup complete: $totalCount entities")
                Result.success()
            } catch (e: SecurityException) {
                Log.e(TAG, "Auto-backup permission lost", e)
                saveLastError(
                    applicationContext,
                    "Permiso de escritura perdido. Reconfigura la ubicación del backup."
                )
                Result.failure()
            } catch (e: Exception) {
                Log.e(TAG, "Auto-backup failed", e)
                saveLastError(applicationContext, "Error: ${e.message?.take(100)}")
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }
    }

}
