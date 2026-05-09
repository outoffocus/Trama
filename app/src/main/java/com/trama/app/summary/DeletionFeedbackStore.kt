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
 * All explicit delete reasons are stored. Only reasons that say something
 * about the original content quality are used for automatic blocking; the rest
 * still help prompts and diagnostics without suppressing future captures.
 */
object DeletionFeedbackStore {

    /**
     * Why the user deleted an entry. Drives whether we treat the deletion
     * as a quality signal and which corrective layer it feeds.
     */
    enum class Reason(
        val storageKey: String,
        val isQualitySignal: Boolean,
        val promptLabel: String
    ) {
        NOISE("noise", true, "ruido/no tarea"),
        NOT_FOR_ME("not_for_me", true, "no era para mi"),
        BAD_TRANSCRIPTION("bad_asr", false, "mala transcripcion"),
        DUPLICATE_OR_DONE("dup_done", false, "duplicada o ya hecha"),
        NO_LONGER_APPLIES("expired", false, "ya no aplica"),
        OTHER("other", false, "otro motivo");

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

    @Serializable
    data class AcceptedSignal(
        val text: String,
        val source: String,
        val acceptedAt: Long,
        /** Pre-tokenized lowercase set, persisted to skip recomputing on every prompt build. */
        val tokens: List<String>
    )

    data class LearnedRule(
        val reason: String,
        val pattern: String,
        val support: Int,
        val instruction: String
    )

    private const val FILE = "deletion_feedback.json"
    private const val ACCEPTED_FILE = "accepted_feedback.json"
    private const val MAX_ENTRIES = 100
    private const val MAX_ACCEPTED_ENTRIES = 100
    private const val MIN_TOKEN_LENGTH = 3

    /**
     * Jaccard threshold above which an incoming entry is auto-discarded
     * because it looks like an already-rejected pattern. Tuned conservatively
     * so we only block clear repeats, not vaguely similar text.
     */
    const val SIMILARITY_BLOCK_THRESHOLD = 0.7f

    /** Number of most-recent noise examples injected into the LLM prompt. */
    const val FEW_SHOT_LIMIT = 3

    /** Number of generalized feedback rules injected into the LLM prompt. */
    const val RULE_LIMIT = 5

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val lock = ReentrantLock()

    @Volatile private var fileRef: File? = null
    @Volatile private var acceptedFileRef: File? = null
    @Volatile private var cache: List<Signal>? = null
    @Volatile private var acceptedCache: List<AcceptedSignal>? = null

    private fun file(context: Context): File {
        val cached = fileRef
        if (cached != null) return cached
        val dir = File(context.filesDir, "diagnostics").apply { mkdirs() }
        val f = File(dir, FILE)
        fileRef = f
        return f
    }

    private fun acceptedFile(context: Context): File {
        val cached = acceptedFileRef
        if (cached != null) return cached
        val dir = File(context.filesDir, "diagnostics").apply { mkdirs() }
        val f = File(dir, ACCEPTED_FILE)
        acceptedFileRef = f
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

    private fun loadAccepted(context: Context): List<AcceptedSignal> {
        acceptedCache?.let { return it }
        val f = acceptedFile(context)
        val list = if (!f.exists()) {
            emptyList()
        } else {
            try {
                json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(AcceptedSignal.serializer()), f.readText())
            } catch (_: Throwable) {
                emptyList()
            }
        }
        acceptedCache = list
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

    private fun persistAccepted(context: Context, list: List<AcceptedSignal>) {
        acceptedCache = list
        try {
            acceptedFile(context).writeText(
                json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(AcceptedSignal.serializer()),
                    list
                )
            )
        } catch (_: Throwable) {
            // best effort
        }
    }

    fun record(context: Context, text: String, reason: Reason) {
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

    fun recordAccepted(context: Context, text: String, source: String) {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return
        lock.withLock {
            val current = loadAccepted(context).toMutableList()
            current.removeAll { it.text.equals(text.trim(), ignoreCase = true) }
            current.add(
                0,
                AcceptedSignal(
                    text = text.trim(),
                    source = source,
                    acceptedAt = System.currentTimeMillis(),
                    tokens = tokens
                )
            )
            persistAccepted(context, current.take(MAX_ACCEPTED_ENTRIES))
        }
    }

    fun clear(context: Context) {
        lock.withLock {
            persist(context, emptyList())
            persistAccepted(context, emptyList())
        }
    }

    fun count(context: Context): Int = load(context).size + loadAccepted(context).size

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
            val reason = Reason.fromKey(s.reason)
            if (reason?.isQualitySignal != true) continue
            val stored = s.tokens.toSet()
            if (stored.isEmpty()) continue
            val intersection = incoming.intersect(stored).size
            if (intersection == 0) continue
            val union = incoming.union(stored).size
            val jaccard = intersection.toFloat() / union.toFloat()
            val containment = intersection.toFloat() / minOf(incoming.size, stored.size).toFloat()
            val score = maxOf(jaccard, containment * 0.85f)
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
        return signals
            .filter { Reason.fromKey(it.reason)?.isQualitySignal == true }
            .take(limit)
            .map { it.text }
    }

    fun recentPromptExamples(context: Context, limit: Int = FEW_SHOT_LIMIT): List<Pair<String, String>> {
        val signals = load(context)
        if (signals.isEmpty()) return emptyList()
        return signals.take(limit).mapNotNull { signal ->
            val reason = Reason.fromKey(signal.reason) ?: return@mapNotNull null
            signal.text to reason.promptLabel
        }
    }

    fun recentAcceptedExamples(context: Context, limit: Int = FEW_SHOT_LIMIT): List<String> {
        val signals = loadAccepted(context)
        if (signals.isEmpty()) return emptyList()
        return signals.take(limit).map { it.text }
    }

    fun learnedRules(context: Context, limit: Int = RULE_LIMIT): List<LearnedRule> {
        val signals = load(context)
            .filter { Reason.fromKey(it.reason)?.isQualitySignal == true }
        if (signals.size < 2) return emptyList()

        val rules = mutableListOf<LearnedRule>()
        signals.groupBy { it.reason }.forEach { (reasonKey, grouped) ->
            if (grouped.size < 2) return@forEach
            val reason = Reason.fromKey(reasonKey) ?: return@forEach
            val counts = linkedMapOf<String, Int>()
            grouped.forEach { signal ->
                val phrases = extractRulePhrases(signal.tokens)
                phrases.forEach { phrase ->
                    counts[phrase] = (counts[phrase] ?: 0) + 1
                }
            }
            counts.entries
                .asSequence()
                .filter { it.value >= 2 }
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key.length })
                .take(2)
                .forEach { (pattern, support) ->
                    rules += LearnedRule(
                        reason = reason.storageKey,
                        pattern = pattern,
                        support = support,
                        instruction = instructionFor(reason, pattern)
                    )
                }
        }
        return rules
            .sortedWith(compareByDescending<LearnedRule> { it.support }.thenBy { it.reason })
            .take(limit)
    }

    fun learnedAcceptedRules(context: Context, limit: Int = RULE_LIMIT): List<LearnedRule> {
        val signals = loadAccepted(context)
        if (signals.size < 2) return emptyList()

        val counts = linkedMapOf<String, Int>()
        signals.forEach { signal ->
            extractRulePhrases(signal.tokens).forEach { phrase ->
                counts[phrase] = (counts[phrase] ?: 0) + 1
            }
        }
        return counts.entries
            .asSequence()
            .filter { it.value >= 2 }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key.length })
            .take(limit)
            .map { (pattern, support) ->
                LearnedRule(
                    reason = "accepted",
                    pattern = pattern,
                    support = support,
                    instruction = "Si una nota contiene un patron parecido a \"$pattern\" con verbo accionable, objeto concreto y ownership del usuario, favorece ACTION aunque sea breve."
                )
            }
            .toList()
    }

    private fun instructionFor(reason: Reason, pattern: String): String {
        return when (reason) {
            Reason.NOT_FOR_ME ->
                "Si una nota contiene un patron parecido a \"$pattern\" y parece dirigida a otra persona, marca DISCARD: no es accion personal del usuario."
            Reason.NOISE ->
                "Si una nota contiene un patron parecido a \"$pattern\" pero no incluye compromiso personal, verbo accionable y objeto concreto, marca DISCARD."
            else ->
                "Si una nota contiene un patron parecido a \"$pattern\", se conservador y marca UNCLEAR o DISCARD salvo accion personal clara."
        }
    }

    private fun extractRulePhrases(tokens: List<String>): Set<String> {
        if (tokens.isEmpty()) return emptySet()
        val phrases = mutableSetOf<String>()
        tokens.forEach { token ->
            if (token.length >= 5) phrases += token
        }
        tokens.windowed(size = 2, step = 1, partialWindows = false)
            .mapTo(phrases) { it.joinToString(" ") }
        tokens.windowed(size = 3, step = 1, partialWindows = false)
            .mapTo(phrases) { it.joinToString(" ") }
        return phrases
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
