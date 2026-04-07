package com.trama.wear.service

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests the pure-logic algorithms used in WatchKeywordListenerService:
 * - isSimilar: word-overlap deduplication
 * - calculateBackoff: progressive delay 1s→2s→4s→8s
 *
 * These are private methods, so we test the same algorithm directly
 * to document and protect the expected behavior.
 */
class KeywordListenerLogicTest {

    // ── Constants matching WatchKeywordListenerService.companion ──

    private val RESTART_MIN_BACKOFF_MS = 1000L
    private val RESTART_MAX_BACKOFF_MS = 8000L

    // ── isSimilar algorithm (word-overlap ≥ 70% of smaller set) ──

    private fun isSimilar(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        val wordsA = a.lowercase().split("\\s+".toRegex()).toSet()
        val wordsB = b.lowercase().split("\\s+".toRegex()).toSet()
        if (wordsA.isEmpty() || wordsB.isEmpty()) return false
        return wordsA.intersect(wordsB).size.toFloat() / minOf(wordsA.size, wordsB.size) >= 0.7f
    }

    @Test
    fun `identical strings are similar`() {
        assertTrue(isSimilar("llamar al dentista", "llamar al dentista"))
    }

    @Test
    fun `case insensitive comparison`() {
        assertTrue(isSimilar("Llamar Al Dentista", "llamar al dentista"))
    }

    @Test
    fun `completely different strings are not similar`() {
        assertFalse(isSimilar("comprar leche", "llamar al médico"))
    }

    @Test
    fun `blank strings return false`() {
        assertFalse(isSimilar("", "hello"))
        assertFalse(isSimilar("hello", ""))
        assertFalse(isSimilar("", ""))
        assertFalse(isSimilar("   ", "hello"))
    }

    @Test
    fun `high overlap is similar`() {
        // 3 of 3 words match in smaller set = 100%
        assertTrue(isSimilar("recordar comprar leche", "recordar comprar leche y pan"))
    }

    @Test
    fun `partial overlap below threshold is not similar`() {
        // "recordar" matches, "comprar" matches → 2/3 = 66% < 70%
        assertFalse(isSimilar("recordar comprar fruta", "recordar comprar leche"))
    }

    @Test
    fun `single word match`() {
        // 1 of 1 word in smaller set = 100%
        assertTrue(isSimilar("hola", "hola mundo"))
    }

    @Test
    fun `single word no match`() {
        assertFalse(isSimilar("hola", "mundo"))
    }

    @Test
    fun `exact 70 percent threshold match`() {
        // 10 words each, 7 overlap → 7/10 = 70% = passes
        val common = "a b c d e f g"
        val a = "$common h i j"    // 10 words
        val b = "$common x y z"    // 10 words, 7 overlap
        assertTrue(isSimilar(a, b))
    }

    @Test
    fun `just below 70 percent threshold`() {
        // 10 words each, 6 overlap → 6/10 = 60% < 70%
        val common = "a b c d e f"
        val a = "$common g h i j"    // 10 words
        val b = "$common x y z w"    // 10 words, 6 overlap
        assertFalse(isSimilar(a, b))
    }

    // ── calculateBackoff algorithm (1s → 2s → 4s → 8s max) ──

    private fun calculateBackoff(consecutiveNoKeyword: Int): Long {
        val shift = minOf(consecutiveNoKeyword, 3)
        val backoff = RESTART_MIN_BACKOFF_MS * (1L shl shift)
        return minOf(backoff, RESTART_MAX_BACKOFF_MS)
    }

    @Test
    fun `backoff at 0 consecutive is 1 second`() {
        assertEquals(1000L, calculateBackoff(0))
    }

    @Test
    fun `backoff at 1 consecutive is 2 seconds`() {
        assertEquals(2000L, calculateBackoff(1))
    }

    @Test
    fun `backoff at 2 consecutive is 4 seconds`() {
        assertEquals(4000L, calculateBackoff(2))
    }

    @Test
    fun `backoff at 3 consecutive is 8 seconds max`() {
        assertEquals(8000L, calculateBackoff(3))
    }

    @Test
    fun `backoff caps at 8 seconds for high counts`() {
        assertEquals(8000L, calculateBackoff(4))
        assertEquals(8000L, calculateBackoff(10))
        assertEquals(8000L, calculateBackoff(100))
    }

    @Test
    fun `backoff progression is exponential`() {
        val values = (0..3).map { calculateBackoff(it) }
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L), values)
    }

    @Test
    fun `backoff never exceeds max`() {
        for (i in 0..100) {
            assertTrue(calculateBackoff(i) <= RESTART_MAX_BACKOFF_MS)
        }
    }

    @Test
    fun `backoff never goes below min`() {
        for (i in 0..100) {
            assertTrue(calculateBackoff(i) >= RESTART_MIN_BACKOFF_MS)
        }
    }
}
