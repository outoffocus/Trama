package com.trama.app.diagnostics

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Structured diagnostic log for the capture pipeline.
 *
 * Every gate (ASR transcription, speaker verification, intent detection,
 * dedup, LLM actionability, persistence, recording extraction) emits one
 * event here. Two sinks:
 *  1. `Log.i(TAG, "...")` in a single-line key=value format so that
 *     `adb logcat -s TRAMA_CAPTURE` yields a readable trace.
 *  2. `capture_events.jsonl` inside `filesDir/diagnostics/` for the
 *     Settings > Diagnóstico screen to aggregate over the last 24h.
 *
 * Events older than [RETENTION_MS] are dropped on the next write so the
 * file stays bounded without a scheduled job.
 */
object CaptureLog {

    const val TAG = "TRAMA_CAPTURE"

    /** Pipeline stage that emitted the event. */
    enum class Gate {
        ASR_FINAL,       // Whisper produced a final transcript (always OK)
        SPEAKER,         // Speaker verification
        INTENT,          // Trigger / keyword match
        DEDUP_MEM,       // In-memory 5s dedup window
        DEDUP_SEM,       // Persisted semantic dedup (DuplicateHeuristics)
        LLM,             // ActionItemProcessor actionability gate
        SAVE,            // Final persistence
        RECORDING        // RecordingProcessor outcome per recording
    }

    /** Outcome for the given gate. */
    enum class Result { OK, REJECT, DUP, NO_MATCH }

    @Serializable
    data class Event(
        val ts: Long,
        val gate: String,
        val result: String,
        val text: String? = null,
        val meta: Map<String, String> = emptyMap()
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val fileLock = ReentrantLock()

    @Volatile private var file: File? = null

    /** Wire the log to a file under the app's private storage. Safe to call repeatedly. */
    fun init(context: Context) {
        if (file != null) return
        val dir = File(context.filesDir, "diagnostics").apply { mkdirs() }
        file = File(dir, "capture_events.jsonl")
    }

    fun event(
        gate: Gate,
        result: Result,
        text: String? = null,
        meta: Map<String, Any?> = emptyMap()
    ) {
        val ts = System.currentTimeMillis()
        val metaStr = meta
            .filterValues { it != null }
            .mapValues { (_, v) -> v.toString() }

        // Logcat line — keep short and parseable.
        val metaText = if (metaStr.isEmpty()) "" else " " + metaStr.entries.joinToString(" ") { (k, v) ->
            "$k=${sanitize(v)}"
        }
        val textPart = if (text.isNullOrBlank()) "" else " text=\"${sanitize(text.take(160))}\""
        Log.i(TAG, "gate=${gate.name} result=${result.name}$textPart$metaText")

        // File append.
        val f = file ?: return
        val line = try {
            json.encodeToString(
                Event.serializer(),
                Event(ts = ts, gate = gate.name, result = result.name, text = text, meta = metaStr)
            )
        } catch (_: Throwable) {
            return
        }
        fileLock.withLock {
            try {
                rotateIfNeeded(f)
                f.appendText(line + "\n")
            } catch (_: Throwable) {
                // best effort; never crash the pipeline on a log write
            }
        }
    }

    /** Events newer than [sinceMs]. Returns empty list on any failure. */
    fun recentEvents(sinceMs: Long): List<Event> {
        val f = file ?: return emptyList()
        if (!f.exists()) return emptyList()
        val out = mutableListOf<Event>()
        fileLock.withLock {
            try {
                f.useLines { lines ->
                    for (line in lines) {
                        if (line.isBlank()) continue
                        val ev = try {
                            json.decodeFromString(Event.serializer(), line)
                        } catch (_: Throwable) {
                            continue
                        }
                        if (ev.ts >= sinceMs) out += ev
                    }
                }
            } catch (_: Throwable) {
                return emptyList()
            }
        }
        return out
    }

    private fun rotateIfNeeded(f: File) {
        if (!f.exists()) return
        if (f.length() < MAX_FILE_BYTES) return
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        val kept = try {
            f.readLines().filter { line ->
                val ev = try {
                    json.decodeFromString(Event.serializer(), line)
                } catch (_: Throwable) {
                    return@filter false
                }
                ev.ts >= cutoff
            }
        } catch (_: Throwable) {
            return
        }
        f.writeText(kept.joinToString("\n", postfix = "\n"))
    }

    private fun sanitize(s: String): String =
        s.replace('\n', ' ').replace('\r', ' ').replace("\"", "'").trim()

    private const val RETENTION_MS = 72L * 60 * 60 * 1000 // 72h
    private const val MAX_FILE_BYTES = 512L * 1024        // 512 KB — rotate opportunistically
}
