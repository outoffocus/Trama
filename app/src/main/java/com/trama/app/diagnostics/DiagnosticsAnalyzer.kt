package com.trama.app.diagnostics

import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.Recording
import kotlinx.serialization.Serializable
import java.text.Normalizer
import kotlin.math.roundToInt

object DiagnosticsAnalyzer {

    @Serializable
    data class Analysis(
        val funnel: Funnel,
        val quality: Quality,
        val latency: Latency,
        val engines: List<CountStat>,
        val rejectReasons: List<CountStat>,
        val frequentPhrases: List<CountStat>,
        val examples: Examples,
        val recommendations: List<String>
    )

    @Serializable
    data class Funnel(
        val gateAccepted: Int,
        val gateRejected: Int,
        val finalTranscripts: Int,
        val speakerAccepted: Int,
        val speakerRejected: Int,
        val intentAccepted: Int,
        val intentRejected: Int,
        val savedEntries: Int,
        val llmAccepted: Int,
        val llmRejected: Int,
        val recordingProcessed: Int,
        val recordingWithoutActions: Int
    )

    @Serializable
    data class Quality(
        val pendingEntries: Int,
        val suggestedEntries: Int,
        val discardedEntries: Int,
        val completedEntries: Int,
        val fallbackEvents: Int,
        val speakerRejectRatePct: Int,
        val llmRejectRatePct: Int,
        val savedPerFinalTranscriptPct: Int
    )

    @Serializable
    data class Latency(
        val avgDecodeMs: Long?,
        val p95DecodeMs: Long?,
        val avgWindowMs: Long?,
        val p95WindowMs: Long?
    )

    @Serializable
    data class CountStat(
        val value: String,
        val count: Int
    )

    @Serializable
    data class Examples(
        val speakerRejected: List<String>,
        val llmRejected: List<String>,
        val discardedEntries: List<String>,
        val suggestedEntries: List<String>,
        val recordingsWithoutActions: List<String>
    )

    fun analyze(
        events: List<CaptureLog.Event>,
        entries: List<DiaryEntry>,
        recordings: List<Recording>
    ): Analysis {
        val funnel = buildFunnel(events)
        val decodeMs = events
            .filter { it.gate == "ASR_FINAL" && it.result == "OK" }
            .mapNotNull { it.meta["decodeMs"]?.toLongOrNull() }
        val windowMs = events
            .filter { it.gate == "ASR_FINAL" && it.result == "OK" }
            .mapNotNull { it.meta["windowMs"]?.toLongOrNull() }

        val llmTotal = funnel.llmAccepted + funnel.llmRejected
        val speakerTotal = funnel.speakerAccepted + funnel.speakerRejected
        val quality = Quality(
            pendingEntries = entries.count { it.status == "PENDING" },
            suggestedEntries = entries.count { it.status == "SUGGESTED" },
            discardedEntries = entries.count { it.status == "DISCARDED" },
            completedEntries = entries.count { it.status == "COMPLETED" },
            fallbackEvents = events.count { event ->
                event.meta.values.any { it.contains("fallback", ignoreCase = true) } ||
                    event.text?.contains("fallback", ignoreCase = true) == true
            },
            speakerRejectRatePct = pct(funnel.speakerRejected, speakerTotal),
            llmRejectRatePct = pct(funnel.llmRejected, llmTotal),
            savedPerFinalTranscriptPct = pct(funnel.savedEntries, funnel.finalTranscripts)
        )

        val analysis = Analysis(
            funnel = funnel,
            quality = quality,
            latency = Latency(
                avgDecodeMs = decodeMs.averageOrNull(),
                p95DecodeMs = decodeMs.percentile95OrNull(),
                avgWindowMs = windowMs.averageOrNull(),
                p95WindowMs = windowMs.percentile95OrNull()
            ),
            engines = topCounts(
                events.mapNotNull { it.meta["engine"] }
                    .map { it.substringAfter("->").trim().ifBlank { it } }
            ),
            rejectReasons = topCounts(
                events
                    .filter { it.result != "OK" }
                    .mapNotNull { it.meta["reason"] ?: it.meta["discardReason"] ?: it.text }
                    .map { normalizeReason(it) }
            ),
            frequentPhrases = frequentPhrases(entries, recordings),
            examples = Examples(
                speakerRejected = eventTexts(events, gate = "SPEAKER", result = "REJECT"),
                llmRejected = eventTexts(events, gate = "LLM", result = "REJECT"),
                discardedEntries = entryTexts(entries, status = "DISCARDED"),
                suggestedEntries = entryTexts(entries, status = "SUGGESTED"),
                recordingsWithoutActions = recordings
                    .filter { recording -> events.any { it.gate == "RECORDING" && it.result == "NO_MATCH" && it.meta["id"] == recording.id.toString() } }
                    .mapNotNull { it.transcription.takeUseful() }
                    .take(EXAMPLE_LIMIT)
            ),
            recommendations = emptyList()
        )

        return analysis.copy(recommendations = recommendations(analysis))
    }

    private fun buildFunnel(events: List<CaptureLog.Event>): Funnel =
        Funnel(
            gateAccepted = events.count { it.gate == "ASR_GATE" && it.result == "OK" },
            gateRejected = events.count { it.gate == "ASR_GATE" && it.result == "NO_MATCH" },
            finalTranscripts = events.count { it.gate == "ASR_FINAL" && it.result == "OK" },
            speakerAccepted = events.count { it.gate == "SPEAKER" && it.result == "OK" },
            speakerRejected = events.count { it.gate == "SPEAKER" && it.result == "REJECT" },
            intentAccepted = events.count { it.gate == "INTENT" && it.result == "OK" },
            intentRejected = events.count { it.gate == "INTENT" && it.result == "NO_MATCH" },
            savedEntries = events.count { it.gate == "SAVE" && it.result == "OK" },
            llmAccepted = events.count { it.gate == "LLM" && it.result == "OK" },
            llmRejected = events.count { it.gate == "LLM" && it.result == "REJECT" },
            recordingProcessed = events.count { it.gate == "RECORDING" && it.result == "OK" },
            recordingWithoutActions = events.count { it.gate == "RECORDING" && it.result == "NO_MATCH" }
        )

    private fun frequentPhrases(
        entries: List<DiaryEntry>,
        recordings: List<Recording>
    ): List<CountStat> {
        val texts = buildList {
            entries.forEach { entry ->
                add(entry.cleanText ?: entry.correctedText ?: entry.text)
            }
            recordings.forEach { recording ->
                add(recording.transcription)
            }
        }
        val phrases = texts.flatMap(::extractPhrases)
        return topCounts(phrases, limit = 20)
    }

    private fun extractPhrases(text: String): List<String> {
        val tokens = normalizeText(text)
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 3 && it !in STOP_WORDS && it.none(Char::isDigit) }
        if (tokens.isEmpty()) return emptyList()

        return buildList {
            addAll(tokens)
            for (size in 2..3) {
                tokens.windowed(size).forEach { window ->
                    if (window.any { it in ACTION_HINTS }) add(window.joinToString(" "))
                }
            }
        }
    }

    private fun eventTexts(
        events: List<CaptureLog.Event>,
        gate: String,
        result: String
    ): List<String> =
        events
            .filter { it.gate == gate && it.result == result }
            .mapNotNull { it.text.takeUseful() }
            .distinct()
            .take(EXAMPLE_LIMIT)

    private fun entryTexts(entries: List<DiaryEntry>, status: String): List<String> =
        entries
            .filter { it.status == status }
            .mapNotNull { (it.cleanText ?: it.correctedText ?: it.text).takeUseful() }
            .distinct()
            .take(EXAMPLE_LIMIT)

    private fun recommendations(analysis: Analysis): List<String> = buildList {
        if (analysis.funnel.finalTranscripts == 0 && analysis.funnel.gateAccepted > 0) {
            add("Hay gate aceptado sin transcripción final: revisar disponibilidad/rendimiento de Whisper y fallbacks de ASR_FINAL vacío/error.")
        }
        if (analysis.quality.speakerRejectRatePct >= 25 && analysis.funnel.speakerRejected >= 3) {
            add("Speaker verification rechaza mucho: recalibrar con más muestras reales, bajar umbral o añadir ejemplos de TV/ambiente como negativos.")
        }
        if (analysis.funnel.intentRejected > analysis.funnel.intentAccepted && analysis.funnel.finalTranscripts >= 5) {
            add("Muchas transcripciones no pasan intent: revisar triggers/frases frecuentes y considerar patrones más naturales para recordatorios/notas.")
        }
        if (analysis.quality.llmRejectRatePct >= 50 && analysis.funnel.llmRejected >= 3) {
            add("El LLM está mandando demasiadas capturas a revisión/descartes: endurecer prompt con ejemplos positivos/negativos del export o simplificar tipos de acción.")
        }
        if (analysis.quality.savedPerFinalTranscriptPct in 1..40 && analysis.funnel.finalTranscripts >= 5) {
            add("Baja conversión de transcripción a entrada guardada: mirar ejemplos INTENT NO_MATCH y frases frecuentes para detectar pérdida por gate/intents.")
        }
        if (analysis.latency.p95DecodeMs != null && analysis.latency.p95DecodeMs > 8_000L) {
            add("ASR lento en p95: considerar modelo Whisper menor, menos ventana de audio o descarga de modelo por SoC.")
        }
        if (analysis.quality.fallbackEvents > 0) {
            add("Hay eventos de fallback: separar métricas de SpeechRecognizer frente a ASR local para saber si la promesa local-first se está cumpliendo.")
        }
        if (analysis.frequentPhrases.take(5).any { it.value in TV_HINTS }) {
            add("Aparecen términos típicos de TV/ruido: reforzar speaker verification y añadir reglas negativas para habla no dirigida a Trama.")
        }
        if (isEmpty()) {
            add("No hay una señal dominante. Revisar ejemplos de suggested/discarded y comparar frases frecuentes contra tareas que realmente querías recordar.")
        }
    }

    private fun topCounts(values: List<String>, limit: Int = 12): List<CountStat> =
        values
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { CountStat(value = it.key, count = it.value) }

    private fun pct(part: Int, total: Int): Int =
        if (total <= 0) 0 else ((part.toDouble() / total.toDouble()) * 100).roundToInt()

    private fun List<Long>.averageOrNull(): Long? =
        if (isEmpty()) null else average().roundToInt().toLong()

    private fun List<Long>.percentile95OrNull(): Long? {
        if (isEmpty()) return null
        val sorted = sorted()
        val index = ((sorted.size - 1) * 0.95).roundToInt().coerceIn(sorted.indices)
        return sorted[index]
    }

    private fun normalizeReason(value: String): String =
        value.lowercase()
            .replace(Regex("\\d+"), "#")
            .replace(Regex("\\s+"), " ")
            .take(80)
            .trim()

    private fun normalizeText(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace(Regex("[^a-záéíóúñü0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun String?.takeUseful(max: Int = 180): String? =
        this?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.length >= 3 }
            ?.take(max)

    private const val EXAMPLE_LIMIT = 8

    private val ACTION_HINTS = setOf(
        "recuerdame", "recordar", "llamar", "comprar", "enviar", "hacer", "pedir",
        "mirar", "revisar", "pagar", "reservar", "avisar", "mandar", "llevar"
    )

    private val TV_HINTS = setOf(
        "capitulo", "temporada", "anuncio", "publicidad", "noticias", "partido",
        "serie", "pelicula", "presentador", "programa"
    )

    private val STOP_WORDS = setOf(
        "que", "con", "para", "por", "los", "las", "una", "uno", "unos", "unas",
        "del", "las", "esta", "este", "esto", "esa", "ese", "eso", "hay", "muy",
        "pero", "como", "cuando", "donde", "porque", "entonces", "tengo", "quiero",
        "debo", "debe", "deberia", "mañana", "manana", "hoy", "ayer", "luego",
        "trama", "vale", "bueno", "pues", "solo", "tambien", "también"
    )
}
