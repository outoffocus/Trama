package com.trama.app.summary

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Persistent store of user-flagged "this was noise" deletion signals.
 *
 * Two consumers:
 *  1. ActionItemProcessor's pre-LLM gate: if a new entry's text overlaps a
 *     stored noise signal above [SIMILARITY_BLOCK_THRESHOLD], discard it
 *     directly without spending an LLM call.
 *  2. Prompt construction: the most recent noise signals are injected as
 *     DISCARD few-shot examples to help the model generalize.
 *
 * Only signals from reasons that the user explicitly attributes to noise
 * are stored — not "duplicate" or "no longer applies", which are not
 * quality signals about the entry's original content.
 */
object DeletionFeedbackStore {

    /**
     * Why the user deleted an entry. Drives whether we treat the deletion
     * as a quality signal and which corrective layer it feeds.
     */
    enum class Reason(val storageKey: String, val isQualitySignal: Boolean) {
        NOISE("noise", true),               // "Era ruido / no es una tarea"
        NOT_FOR_ME("not_for_me", true),     // "No es para mí" (instruction to others)
        BAD_TRANSCRIPTION("bad_asr", false),// "Texto mal transcrito" — feeds dictionary, not gate
        DUPLICATE_OR_DONE("dup_done", false),// "Duplicada / ya hecha"
        NO_LONGER_APPLIES("expired", false), // "Ya no aplica"
        OTHER("other", false);

        companion object {
            fun fromKey(key: String?): Reason? =
                values().firstOrNull { it.storageKey == key }
        }
    }

    @Serializable
    data class Signal(
        val text: String,
        val reason: String,
        val deletedAt: Long,
        /** Pre-tokenized lowercase set, persisted to skip recomputing on every gate check. */
        val tokens: List<String>
    )

    private const val FILE = "deletion_feedback.json"
    private const val MAX_ENTRIES = 100
    private const val MIN_TOKEN_LENGTH = 3

    /**
     * Jaccard threshold above which an incoming entry is auto-discarded
     * because it looks like an already-rejected pattern. Tuned conservatively
     * so we only block clear repeats, not vaguely similar text.
     */
    const val SIMILARITY_BLOCK_THRESHOLD = 0.7f

    /** Number of most-recent noise examples injected into the LLM prompt. */
    const val FEW_SHOT_LIMIT = 3

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val lock = ReentrantLock()

    @Volatile private var fileRef: File? = null
    @Volatile private var cache: List<Signal>? = null

    private fun file(context: Context): File {
        val cached = fileRef
        if (cached != null) return cached
        val dir = File(context.filesDir, "diagnostics").apply { mkdirs() }
        val f = File(dir, FILE)
        fileRef = f
        return f
    }

    private fun load(context: Context): List<Signal> {
        cache?.let { return it }
        val f = file(context)
        val list = if (!f.exists()) {
            emptyList()
        } else {
            try {
                json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Signal.serializer()), f.readText())
            } catch (_: Throwable) {
                emptyList()
            }
        }
        cache = list
        return list
    }

    private fun persist(context: Context, list: List<Signal>) {
        cache = list
        try {
            file(context).writeText(
                json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(Signal.serializer()),
                    list
                )
            )
        } catch (_: Throwable) {
            // best effort
        }
    }

    fun record(context: Context, text: String, reason: Reason) {
        if (!reason.isQualitySignal) return
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return
        lock.withLock {
            val current = load(context).toMutableList()
            current.add(0, Signal(text = text.trim(), reason = reason.storageKey, deletedAt = System.currentTimeMillis(), tokens = tokens))
            // Drop oldest beyond MAX_ENTRIES.
            val trimmed = current.take(MAX_ENTRIES)
            persist(context, trimmed)
        }
    }

    fun clear(context: Context) {
        lock.withLock {
            persist(context, emptyList())
        }
    }

    fun count(context: Context): Int = load(context).size

    /**
     * Returns the highest Jaccard similarity between [text] and any stored
     * noise signal, paired with the matching signal. Null if the store is
     * empty or no signal exceeded a minimal floor (0.3) — we don't want to
     * waste comparison cycles downstream on near-zero matches.
     */
    fun bestMatch(context: Context, text: String): Pair<Signal, Float>? {
        val signals = load(context)
        if (signals.isEmpty()) return null
        val incoming = tokenize(text).toSet()
        if (incoming.isEmpty()) return null
        var best: Signal? = null
        var bestScore = 0f
        for (s in signals) {
            val stored = s.tokens.toSet()
            if (stored.isEmpty()) continue
            val intersection = incoming.intersect(stored).size
            if (intersection == 0) continue
            val union = incoming.union(stored).size
            val score = intersection.toFloat() / union.toFloat()
            if (score > bestScore) {
                bestScore = score
                best = s
            }
        }
        return if (best != null && bestScore >= 0.3f) best to bestScore else null
    }

    /**
     * Recent noise signals to inject as DISCARD few-shot examples. Only the
     * raw deleted text is exposed — reason metadata is internal.
     */
    fun recentNoiseExamples(context: Context, limit: Int = FEW_SHOT_LIMIT): List<String> {
        val signals = load(context)
        if (signals.isEmpty()) return emptyList()
        return signals.take(limit).map { it.text }
    }

    private fun tokenize(text: String): List<String> {
        val normalized = text.lowercase(Locale.getDefault())
        return normalized
            .split(Regex("[^\\p{L}0-9]+"))
            .filter { it.length >= MIN_TOKEN_LENGTH && it !in STOPWORDS }
    }

    private val STOPWORDS = setOf(
        "el", "la", "los", "las", "un", "una", "unos", "unas",
        "de", "del", "al", "para", "con", "sin", "sobre", "por",
        "que", "y", "o", "u", "si", "no", "se", "es", "ya", "lo",
        "me", "te", "le", "nos", "os", "les",
        "mi", "mis", "tu", "tus", "su", "sus",
        "este", "esta", "esto", "esos", "esas", "eso",
        "tengo", "tienes", "tiene", "tenemos", "hay", "ser", "estar"
    )
}
