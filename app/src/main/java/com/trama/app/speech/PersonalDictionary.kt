package com.trama.app.speech

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dictStore: DataStore<Preferences> by preferencesDataStore(name = "personal_dictionary")

/**
 * Learns word/phrase corrections from user edits and applies them to future transcriptions.
 *
 * Supports both single-word and multi-word corrections:
 * - "conprar" → "comprar" (typo correction)
 * - "ce tag" → "ctag" (Vosk splitting a proper noun)
 * - "setac" → "ctag" (Vosk completely mishearing a proper noun)
 */
class PersonalDictionary(private val context: Context) {

    companion object {
        private const val TAG = "PersonalDictionary"
        private val CORRECTIONS = stringPreferencesKey("corrections")
        // Format: wrong1|correct1|count1,wrong2|correct2|count2,...
        // wrong/correct can contain spaces (phrase corrections)
    }

    data class Correction(val wrong: String, val correct: String, val count: Int)

    val corrections: Flow<List<Correction>> = context.dictStore.data.map { prefs ->
        parseCorrections(prefs[CORRECTIONS] ?: "")
    }

    /**
     * Learn corrections by diffing the original transcription with the user's edit.
     */
    suspend fun learnFromEdit(originalText: String, editedText: String) {
        if (originalText.trim() == editedText.trim()) return

        val originalWords = originalText.trim().lowercase().split("\\s+".toRegex())
        val editedWords = editedText.trim().lowercase().split("\\s+".toRegex())

        val newCorrections = diffWords(originalWords, editedWords)
        if (newCorrections.isEmpty()) return

        val current = corrections.first().toMutableList()

        for ((wrong, correct) in newCorrections) {
            if (wrong == correct) continue
            if (wrong.length < 2 || correct.length < 2) continue

            val existing = current.indexOfFirst { it.wrong == wrong }
            if (existing >= 0) {
                val old = current[existing]
                if (old.correct == correct) {
                    current[existing] = old.copy(count = old.count + 1)
                } else if (old.count <= 1) {
                    current[existing] = Correction(wrong, correct, 1)
                }
            } else {
                current.add(Correction(wrong, correct, 1))
            }
        }

        saveCorrections(current)
        Log.i(TAG, "Learned ${newCorrections.size} corrections: ${newCorrections.joinToString { "${it.first} → ${it.second}" }}, total: ${current.size}")
    }

    /**
     * Apply learned corrections to a transcribed text.
     * Checks multi-word phrases first (longest match), then single words.
     */
    suspend fun correct(text: String): String {
        val dict = corrections.first()
        if (dict.isEmpty()) return text

        val words = text.split("\\s+".toRegex()).toMutableList()

        // Sort by phrase length (longer phrases first) to match greedily
        val sortedDict = dict.filter { it.count >= 1 }.sortedByDescending { it.wrong.split(" ").size }

        // Try multi-word corrections first
        for (correction in sortedDict) {
            val phraseWords = correction.wrong.split(" ")
            if (phraseWords.size < 2) continue

            var i = 0
            while (i <= words.size - phraseWords.size) {
                val match = phraseWords.indices.all { j ->
                    words[i + j].lowercase() == phraseWords[j]
                }
                if (match) {
                    val replacement = applyCapitalization(words[i], correction.correct)
                    // Remove matched words and insert replacement
                    repeat(phraseWords.size) { words.removeAt(i) }
                    words.add(i, replacement)
                    i++ // move past replacement
                } else {
                    i++
                }
            }
        }

        // Then single-word corrections
        for (i in words.indices) {
            val lower = words[i].lowercase()
            val match = sortedDict.find { it.wrong == lower && it.wrong.split(" ").size == 1 }
            if (match != null) {
                words[i] = applyCapitalization(words[i], match.correct)
            }
        }

        return words.joinToString(" ")
    }

    private fun applyCapitalization(original: String, replacement: String): String {
        return if (original.first().isUpperCase()) {
            replacement.replaceFirstChar { it.uppercase() }
        } else {
            replacement
        }
    }

    suspend fun removeCorrection(wrong: String) {
        val current = corrections.first().toMutableList()
        current.removeAll { it.wrong == wrong }
        saveCorrections(current)
    }

    suspend fun clearAll() {
        context.dictStore.edit { it.remove(CORRECTIONS) }
    }

    private suspend fun saveCorrections(list: List<Correction>) {
        val raw = list.joinToString(",") { "${it.wrong}|${it.correct}|${it.count}" }
        context.dictStore.edit { it[CORRECTIONS] = raw }
    }

    private fun parseCorrections(raw: String): List<Correction> {
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { entry ->
            val parts = entry.trim().split("|")
            if (parts.size == 3) {
                val count = parts[2].toIntOrNull() ?: 1
                Correction(parts[0].trim(), parts[1].trim(), count)
            } else null
        }
    }

    /**
     * Diff original vs edited words using LCS alignment.
     * Groups consecutive changes into "runs" and pairs them as phrase corrections.
     * This handles: single word corrections, multi-word→single-word (Vosk splits a word),
     * and single-word→multi-word corrections.
     */
    private fun diffWords(original: List<String>, edited: List<String>): List<Pair<String, String>> {
        // Use LCS to find matching (unchanged) words
        val m = original.size
        val n = edited.size
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (original[i - 1] == edited[j - 1]) {
                    dp[i - 1][j - 1] + 1
                } else {
                    maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }

        // Backtrack to build alignment: list of (origIdx, editIdx) for matched words
        // and identify unmatched regions
        val matched = mutableListOf<Pair<Int, Int>>()
        var i = m
        var j = n
        while (i > 0 && j > 0) {
            when {
                original[i - 1] == edited[j - 1] -> {
                    matched.add(0, i - 1 to j - 1)
                    i--; j--
                }
                dp[i - 1][j] > dp[i][j - 1] -> i--
                else -> j--
            }
        }

        // Build change runs: regions between matched anchors
        // Each run is a pair of (original word range, edited word range)
        val result = mutableListOf<Pair<String, String>>()

        // Add sentinel anchors at start and end
        data class Anchor(val origIdx: Int, val editIdx: Int)
        val anchors = mutableListOf(Anchor(-1, -1))
        matched.forEach { anchors.add(Anchor(it.first, it.second)) }
        anchors.add(Anchor(m, n))

        for (k in 0 until anchors.size - 1) {
            val prevOrig = anchors[k].origIdx
            val nextOrig = anchors[k + 1].origIdx
            val prevEdit = anchors[k].editIdx
            val nextEdit = anchors[k + 1].editIdx

            // Words between these anchors are the changed region
            val origRun = (prevOrig + 1 until nextOrig).map { original[it] }
            val editRun = (prevEdit + 1 until nextEdit).map { edited[it] }

            if (origRun.isEmpty() && editRun.isEmpty()) continue
            if (origRun.isEmpty() || editRun.isEmpty()) continue // pure insertion/deletion, skip

            // This is a substitution region — pair as phrase correction
            val wrongPhrase = origRun.joinToString(" ")
            val correctPhrase = editRun.joinToString(" ")

            if (wrongPhrase != correctPhrase) {
                result.add(wrongPhrase to correctPhrase)
            }
        }

        return result
    }
}
