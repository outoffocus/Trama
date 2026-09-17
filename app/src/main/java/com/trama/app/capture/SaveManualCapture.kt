package com.trama.app.capture

import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryContentKind
import com.trama.shared.model.EntryStatus
import com.trama.shared.model.Source
import java.util.UUID

/** Persists the original manual capture before any later classification. */
class SaveManualCapture(
    private val insert: suspend (DiaryEntry) -> Long,
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend operator fun invoke(text: String, capturedAt: Long? = null): Long {
        val normalized = text.trim()
        require(normalized.isNotEmpty()) { "Manual capture cannot be empty" }
        return insert(buildEntry(normalized, capturedAt ?: now()))
    }

    internal fun buildEntry(text: String, capturedAt: Long): DiaryEntry = DiaryEntry(
        text = text,
        keyword = "manual",
        category = "Nota",
        confidence = 1f,
        source = Source.PHONE,
        duration = 0,
        isManual = true,
        cleanText = text,
        createdAt = capturedAt,
        status = EntryStatus.SAVED,
        contentKind = EntryContentKind.MEMORY,
        sourceCaptureId = UUID.randomUUID().toString()
    )
}
