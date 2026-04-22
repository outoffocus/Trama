package com.trama.app.service

import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.Source
import com.trama.shared.speech.IntentDetector.DetectionResult
import com.trama.shared.speech.IntentPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeduplicationManagerTest {

    private var now = 1_000_000L
    private val clock: () -> Long = { now }
    private fun detection(text: String, pattern: IntentPattern? = null, keyword: String? = null) =
        DetectionResult(pattern = pattern, customKeyword = keyword, capturedText = text, label = "test")

    private fun entry(
        text: String,
        cleanText: String? = null,
        createdAt: Long = now
    ) = DiaryEntry(
        text = text,
        keyword = "test",
        category = "test",
        confidence = 0.9f,
        source = Source.PHONE,
        duration = 0,
        correctedText = text,
        wasReviewedByLLM = false,
        llmConfidence = 0.9f,
        cleanText = cleanText,
        createdAt = createdAt
    )

    @Test
    fun tryReserve_firstCaptureIsReserved() {
        val mgr = DeduplicationManager(clock)
        val r = mgr.tryReserve(detection("comprar leche"), "comprar leche")
        assertTrue(r is DeduplicationManager.Reservation.Reserved)
    }

    @Test
    fun tryReserve_sameKeyWithinWindow_isDuplicate() {
        val mgr = DeduplicationManager(clock)
        mgr.tryReserve(detection("comprar leche"), "comprar leche")
        now += 1_000
        val r = mgr.tryReserve(detection("comprar leche"), "comprar leche")
        assertTrue(r is DeduplicationManager.Reservation.Duplicate)
    }

    @Test
    fun tryReserve_sameKeyAfterWindow_isReserved() {
        val mgr = DeduplicationManager(clock)
        mgr.tryReserve(detection("comprar leche"), "comprar leche")
        now += DeduplicationManager.IN_MEMORY_WINDOW_MS + 1
        val r = mgr.tryReserve(detection("comprar leche"), "comprar leche")
        assertTrue(r is DeduplicationManager.Reservation.Reserved)
    }

    @Test
    fun tryReserve_differentText_isReserved() {
        val mgr = DeduplicationManager(clock)
        mgr.tryReserve(detection("comprar leche"), "comprar leche")
        val r = mgr.tryReserve(detection("llamar al médico"), "llamar al médico")
        assertTrue(r is DeduplicationManager.Reservation.Reserved)
    }

    @Test
    fun isDuplicateOfLatestPending_exactTextMatch() {
        val mgr = DeduplicationManager(clock)
        val latest = entry("comprar leche")
        assertTrue(mgr.isDuplicateOfLatestPending(latest, "comprar leche", "comprar leche"))
    }

    @Test
    fun isDuplicateOfLatestPending_diacriticsNormalized() {
        val mgr = DeduplicationManager(clock)
        val latest = entry("llamar al medico")
        assertTrue(mgr.isDuplicateOfLatestPending(latest, "llamar al médico", "llamar al médico"))
    }

    @Test
    fun isDuplicateOfLatestPending_similarityOverSeventyPercent() {
        val mgr = DeduplicationManager(clock)
        val latest = entry("comprar leche y pan")
        // 3/4 words overlap → 75% (smaller set size=4) → duplicate
        assertTrue(mgr.isDuplicateOfLatestPending(latest, "comprar leche pan", "comprar leche pan"))
    }

    @Test
    fun isDuplicateOfLatestPending_outsidePersistedWindow_notDuplicate() {
        val mgr = DeduplicationManager(clock)
        val latest = entry("comprar leche", createdAt = now - DeduplicationManager.PERSISTED_WINDOW_MS - 1)
        assertFalse(mgr.isDuplicateOfLatestPending(latest, "comprar leche", "comprar leche"))
    }

    @Test
    fun isDuplicateOfLatestPending_nullLatest_notDuplicate() {
        val mgr = DeduplicationManager(clock)
        assertFalse(mgr.isDuplicateOfLatestPending(null, "anything", "anything"))
    }

    @Test
    fun buildDedupKey_stripsTriggerPrefix() {
        val mgr = DeduplicationManager(clock)
        val pattern = IntentPattern(
            id = "recordatorios",
            label = "Recordar",
            triggers = listOf("recordar")
        )
        val k1 = mgr.buildDedupKey(detection("recordar comprar leche", pattern = pattern), "recordar comprar leche")
        val k2 = mgr.buildDedupKey(detection("comprar leche"), "comprar leche")
        // Both should produce the same meaningful key
        assertEquals(k1, k2)
    }

    @Test
    fun buildDedupKey_filtersStopwords() {
        val mgr = DeduplicationManager(clock)
        val k = mgr.buildDedupKey(detection("comprar de la leche"), "comprar de la leche")
        assertEquals("comprar leche", k)
    }
}
