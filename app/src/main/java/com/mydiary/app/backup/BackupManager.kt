package com.mydiary.app.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import com.mydiary.shared.data.DiaryRepository
import com.mydiary.shared.model.DiaryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Handles backup/restore of diary entries via JSON files.
 * Uses Storage Access Framework (SAF) so user can save to Google Drive,
 * Downloads, or any storage provider — zero configuration needed.
 */
object BackupManager {

    private const val TAG = "BackupManager"
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Serializable
    data class Backup(
        val version: Int = 1,
        val exportedAt: Long = System.currentTimeMillis(),
        val entries: List<BackupEntry>
    )

    @Serializable
    data class BackupEntry(
        val text: String,
        val keyword: String,
        val category: String,
        val confidence: Float,
        val source: String,
        val duration: Int,
        val createdAt: Long,
        val correctedText: String? = null,
        val wasReviewedByLLM: Boolean = false,
        val llmConfidence: Float? = null,
        val status: String? = null,
        val actionType: String? = null,
        val cleanText: String? = null,
        val dueDate: Long? = null,
        val completedAt: Long? = null,
        val priority: String? = null,
        val isManual: Boolean = false
    )

    /**
     * Export all entries to JSON and write to the given URI.
     * @return number of entries exported
     */
    suspend fun exportToUri(context: Context, uri: Uri, repository: DiaryRepository): Int {
        return withContext(Dispatchers.IO) {
            val entries = repository.getAll().first()
            val backupEntries = entries.map { it.toBackupEntry() }
            val backup = Backup(entries = backupEntries)
            val jsonStr = json.encodeToString(backup)

            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(jsonStr.toByteArray(Charsets.UTF_8))
            } ?: throw Exception("Cannot open output stream")

            Log.i(TAG, "Exported ${entries.size} entries to $uri")
            entries.size
        }
    }

    /**
     * Import entries from a JSON backup file.
     * Skips duplicates (same createdAt + text).
     * @return Pair(imported, skipped)
     */
    suspend fun importFromUri(context: Context, uri: Uri, repository: DiaryRepository): Pair<Int, Int> {
        return withContext(Dispatchers.IO) {
            val jsonStr = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: throw Exception("Cannot open input stream")

            val backup = json.decodeFromString<Backup>(jsonStr)
            var imported = 0
            var skipped = 0

            for (entry in backup.entries) {
                // Skip if duplicate
                if (repository.existsByCreatedAtAndText(entry.createdAt, entry.text)) {
                    skipped++
                    continue
                }

                repository.insert(entry.toDiaryEntry())
                imported++
            }

            Log.i(TAG, "Imported $imported entries, skipped $skipped duplicates")
            Pair(imported, skipped)
        }
    }

    /**
     * Get backup file name with date.
     */
    fun getBackupFileName(): String {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(System.currentTimeMillis())
        return "mydiary-backup-$date.json"
    }

    private fun DiaryEntry.toBackupEntry() = BackupEntry(
        text = text,
        keyword = keyword,
        category = category,
        confidence = confidence,
        source = source.name,
        duration = duration,
        createdAt = createdAt,
        correctedText = correctedText,
        wasReviewedByLLM = wasReviewedByLLM,
        llmConfidence = llmConfidence,
        status = status,
        actionType = actionType,
        cleanText = cleanText,
        dueDate = dueDate,
        completedAt = completedAt,
        priority = priority,
        isManual = isManual
    )

    private fun BackupEntry.toDiaryEntry() = DiaryEntry(
        text = text,
        keyword = keyword,
        category = category,
        confidence = confidence,
        source = try { com.mydiary.shared.model.Source.valueOf(source) }
                 catch (_: Exception) { com.mydiary.shared.model.Source.PHONE },
        duration = duration,
        createdAt = createdAt,
        correctedText = correctedText,
        wasReviewedByLLM = wasReviewedByLLM,
        llmConfidence = llmConfidence,
        status = status ?: com.mydiary.shared.model.EntryStatus.PENDING,
        actionType = actionType ?: com.mydiary.shared.model.EntryActionType.GENERIC,
        cleanText = cleanText,
        dueDate = dueDate,
        completedAt = completedAt,
        priority = priority ?: com.mydiary.shared.model.EntryPriority.NORMAL,
        isManual = isManual
    )
}
