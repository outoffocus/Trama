package com.trama.app.service

import com.trama.shared.speech.IntentDetector.DetectionResult
import com.trama.shared.model.DiaryEntry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.Normalizer

/**
 * Encapsulates capture deduplication across two layers:
 *
 *  1. **In-memory gate** ([tryReserve]): fast, synchronous check-then-reserve guarded by a
 *     monitor. Rejects the same dedup key seen within [IN_MEMORY_WINDOW_MS].
 *  2. **Persisted gate** ([isDuplicateOfLatestPending] + [withSaveLock]): serializes
 *     concurrent DB inserts and compares the candidate against the most recent pending
 *     entry within [PERSISTED_WINDOW_MS].
 *
 * Both gates are needed: the in-memory gate catches rapid partial/final bursts from a
 * single recognition cycle; the persisted gate catches duplicates that slip past
 * in-memory (e.g. after a service restart where the in-memory state is lost).
 */
class DeduplicationManager(
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val dedupLock = Any()
    private val saveMutex = Mutex()

    @Volatile private var lastSavedText: String = ""
    @Volatile private var lastSavedDedupKey: String = ""
    @Volatile private var lastSavedTime: Long = 0L

    sealed interface Reservation {
        data object Reserved : Reservation
        data object Duplicate : Reservation
    }

    /** Atomically checks the in-memory window and — if free — claims it for [text]. */
    fun tryReserve(result: DetectionResult, text: String): Reservation {
        val now = clock()
        val key = buildDedupKey(result, text)
        synchronized(dedupLock) {
            val withinWindow = now - lastSavedTime < IN_MEMORY_WINDOW_MS
            if (withinWindow && key.isNotBlank() && key == lastSavedDedupKey) {
                return Reservation.Duplicate
            }
            lastSavedText = text
            lastSavedDedupKey = key
            lastSavedTime = now
            return Reservation.Reserved
        }
    }

    /** Serializes the persisted-dedup critical section across concurrent callers. */
    suspend fun <T> withSaveLock(block: suspend () -> T): T = saveMutex.withLock { block() }

    /** Returns true if [latest] is a near-duplicate of the candidate within the persisted window. */
    fun isDuplicateOfLatestPending(
        latest: DiaryEntry?,
        capturedText: String,
        savedText: String,
        now: Long = clock()
    ): Boolean {
        if (latest == null) return false
        if (now - latest.createdAt > PERSISTED_WINDOW_MS) return false
        val normCaptured = normalize(capturedText)
        val normSaved = normalize(savedText)
        val normLatestText = normalize(latest.text)
        val normLatestClean = normalize(latest.cleanText ?: "")
        return (normCaptured.isNotBlank() &&
            (normCaptured == normLatestText || normCaptured == normLatestClean)) ||
            (normSaved.isNotBlank() &&
                (normSaved == normLatestText || normSaved == normLatestClean)) ||
            isSimilar(normSaved, normLatestText) ||
            isSimilar(normCaptured, normLatestText)
    }

    internal fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")
            .replace(NON_ALNUM_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()

    /** 70%+ token overlap (vs the smaller set) counts as a duplicate. */
    internal fun isSimilar(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        val wordsA = a.lowercase().split(WHITESPACE_REGEX).toSet()
        val wordsB = b.lowercase().split(WHITESPACE_REGEX).toSet()
        if (wordsA.isEmpty() || wordsB.isEmpty()) return false
        val intersection = wordsA.intersect(wordsB)
        val smaller = minOf(wordsA.size, wordsB.size)
        return intersection.size.toFloat() / smaller >= SIMILARITY_THRESHOLD
    }

    internal fun buildDedupKey(result: DetectionResult, text: String): String {
        val normalized = normalize(text)
        if (normalized.isBlank()) return ""

        val stripped = result.pattern
            ?.normalizedTriggers
            ?.mapNotNull { trigger ->
                normalized.removePrefix("$trigger ").takeIf { it != normalized }?.trim()
            }
            ?.firstOrNull()
            ?: result.customKeyword?.let { keyword ->
                val normalizedKeyword = normalize(keyword)
                normalized.removePrefix("$normalizedKeyword ").trim()
            }
            ?: normalized

        val meaningful = stripped
            .split(" ")
            .filter { token -> token.isNotBlank() && token !in STOPWORDS }
            .joinToString(" ")

        return meaningful.ifBlank { normalized }
    }

    companion object {
        const val IN_MEMORY_WINDOW_MS = 5000L
        const val PERSISTED_WINDOW_MS = 15000L
        private const val SIMILARITY_THRESHOLD = 0.7f

        private val DIACRITICS_REGEX = "\\p{M}+".toRegex()
        private val NON_ALNUM_REGEX = "[^\\p{L}\\p{N}\\s]".toRegex()
        private val WHITESPACE_REGEX = "\\s+".toRegex()

        private val STOPWORDS = setOf(
            "recordar", "recorda", "acordarme", "acordarnos",
            "de", "me", "olvide", "se", "fue", "la", "olla"
        )
    }
}
