package com.trama.app.backup

import com.trama.app.backup.BackupManager.toBackupEntry
import com.trama.app.backup.BackupManager.toDiaryEntry
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.Source
import org.junit.Assert.*
import org.junit.Test

class BackupConfirmationTest {
    @Test fun `confirmation and original timestamp survive actual backup mappings`() {
        val original = DiaryEntry(id = 17, text = "Llamar al taller", keyword = "", category = "nota",
            confidence = 1f, source = Source.WATCH, duration = 3, createdAt = 1700000000000,
            userConfirmedAt = 1700000009000, verificationSource = "DETAIL")
        val encoded = BackupManager.encode(BackupManager.Backup(entries = listOf(original.toBackupEntry())))
        val restored = BackupManager.decode(encoded).entries.single().toDiaryEntry(null, null)
        assertEquals(original.userConfirmedAt, restored.userConfirmedAt)
        assertEquals(original.verificationSource, restored.verificationSource)
        assertEquals(original.createdAt, restored.createdAt)
        assertEquals(original.source, restored.source)
    }

    @Test fun `edited manual capture restores original display text and provenance`() {
        val original = DiaryEntry(id = 17, text = "Texto original", keyword = "manual", category = "Nota",
            confidence = 1f, source = Source.PHONE, duration = 0, isManual = true,
            cleanText = "Texto editado", correctedText = "Texto editado", createdAt = 1700000000000)
        val encoded = BackupManager.encode(BackupManager.Backup(entries = listOf(original.toBackupEntry())))
        val restored = BackupManager.decode(encoded).entries.single().toDiaryEntry(null, null)
        assertEquals(original.text, restored.text)
        assertEquals(original.displayText, restored.displayText)
        assertEquals(original.createdAt, restored.createdAt)
        assertTrue(restored.isManual)
        assertEquals(original.source, restored.source)
    }

    @Test fun `old backup does not invent confirmation`() {
        val old = """{"entries":[{"text":"Nota","keyword":"","category":"nota","confidence":1.0,"source":"WATCH","duration":0,"createdAt":1}]}"""
        val restored = BackupManager.decode(old).entries.single().toDiaryEntry(null, null)
        assertNull(restored.userConfirmedAt)
        assertNull(restored.verificationSource)
    }
}
