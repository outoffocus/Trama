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
        val power: Power,
        val engines: List<CountStat>,
        val rejectReasons: List<CountStat>,
        val qualityBuckets: List<CountStat>,
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
        val segmentsClosedBySilence: Int,
        val segmentsClosedByCap: Int,
        val uncertainFallbacks: Int,
        val uncertainFallbacksBlocked: Int,
        val unexpectedServiceStops: Int,
        val explicitUserStops: Int,
        val mediaPlaybackPauses: Int,
        val mediaPlaybackBlockedWindows: Int,
        val llmAcceptedPending: Int,
        val llmRejectedDiscarded: Int,
        val llmRejectedSuggested: Int,
        val discardedPossibleFalseNegatives: Int,
        val acceptedLowConfidence: Int,
        val entriesWithoutLlmReview: Int,
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
    data class Power(
        val avgBatteryTempC: Float?,
        val maxBatteryTempC: Float?,
        val thermalStatusCounts: List<CountStat>,
        val worstThermalStatus: String?,
        val observedBatteryDropPct: Int?,
        val observedBatteryDropPerHourPct: Float?
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
        val discardedPossibleFalseNegatives: List<String>,
        val suggestedEntries: List<String>,
        val acceptedLowConfidence: List<String>,
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
        val power = buildPower(events)

        val llmTotal = funnel.llmAccepted + funnel.llmRejected
        val speakerTotal = funnel.speakerAccepted + funnel.speakerRejected
        val llmAcceptedPending = events.count {
            it.gate == "LLM" && it.result == "OK" && it.meta["route"] == "PENDING"
        }
        val llmRejectedDiscarded = events.count {
            it.gate == "LLM" && it.result == "REJECT" && it.meta["route"] == "DISCARDED"
        }
        val llmRejectedSuggested = events.count {
            it.gate == "LLM" && it.result == "REJECT" && it.meta["route"] == "SUGGESTED"
        }
        val discardedPossibleFalseNegatives = events.count {
            it.gate == "LLM" &&
                it.result == "REJECT" &&
                it.meta["qualityBucket"] == "discarded_possible_false_negative"
        }
        val acceptedLowConfidence = events.count {
            it.gate == "LLM" &&
                it.result == "OK" &&
                it.meta["qualityBucket"] == "accepted_low_confidence"
        }
        val quality = Quality(
            pendingEntries = entries.count { it.status == "PENDING" },
            suggestedEntries = entries.count { it.status == "SUGGESTED" },
            discardedEntries = entries.count { it.status == "DISCARDED" },
            completedEntries = entries.count { it.status == "COMPLETED" },
            fallbackEvents = events.count { event ->
                event.meta.values.any { it.contains("fallback", ignoreCase = true) } ||
                    event.text?.contains("fallback", ignoreCase = true) == true
            },
            segmentsClosedBySilence = events.countSegmentFinalized("silence_stop"),
            segmentsClosedByCap = events.countSegmentFinalized("unmatched_segment_cap"),
            uncertainFallbacks = events.count {
                it.gate == "ASR_GATE" && it.meta["reason"] == "uncertain_gate_fallback"
            },
            uncertainFallbacksBlocked = events.count {
                it.gate == "ASR_GATE" && it.text == "uncertain_gate_fallback_blocked"
            },
            unexpectedServiceStops = countUnexpectedServiceStops(events),
            explicitUserStops = events.count {
                it.gate == "SERVICE" && it.text == "service_stop_requested"
            },
            mediaPlaybackPauses = events.count {
                it.gate == "SERVICE" && it.text == "media_playback_pause"
            },
            mediaPlaybackBlockedWindows = events.count {
                (it.gate == "ASR_FINAL" && it.text == "media_playback_blocked_window") ||
                    (it.gate == "ASR_GATE" && it.text == "media_playback_gate_blocked")
            },
            llmAcceptedPending = llmAcceptedPending,
            llmRejectedDiscarded = llmRejectedDiscarded,
            llmRejectedSuggested = llmRejectedSuggested,
            discardedPossibleFalseNegatives = discardedPossibleFalseNegatives,
            acceptedLowConfidence = acceptedLowConfidence,
            entriesWithoutLlmReview = entries.count { !it.wasReviewedByLLM },
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
            power = power,
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
            qualityBuckets = topCounts(
                events
                    .filter { it.gate == "LLM" }
                    .mapNotNull { it.meta["qualityBucket"] }
            ),
            frequentPhrases = frequentPhrases(entries, recordings),
            examples = Examples(
                speakerRejected = eventTexts(events, gate = "SPEAKER", result = "REJECT"),
                llmRejected = eventTexts(events, gate = "LLM", result = "REJECT"),
                discardedEntries = entryTexts(entries, status = "DISCARDED"),
                discardedPossibleFalseNegatives = events
                    .filter {
                        it.gate == "LLM" &&
                            it.result == "REJECT" &&
                            it.meta["qualityBucket"] == "discarded_possible_false_negative"
                    }
                    .mapNotNull { it.text.takeUseful() }
                    .distinct()
                    .take(EXAMPLE_LIMIT),
                suggestedEntries = entryTexts(entries, status = "SUGGESTED"),
                acceptedLowConfidence = events
                    .filter {
                        it.gate == "LLM" &&
                            it.result == "OK" &&
                            it.meta["qualityBucket"] == "accepted_low_confidence"
                    }
                    .mapNotNull { it.text.takeUseful() }
                    .distinct()
                    .take(EXAMPLE_LIMIT),
                recordingsWithoutActions = recordings
                    .filter { recording -> events.any { it.gate == "RECORDING" && it.result == "NO_MATCH" && it.meta["id"] == recording.id.toString() } }
                    .mapNotNull { it.transcription.takeUseful() }
                    .take(EXAMPLE_LIMIT)
            ),
            recommendations = emptyList()
        )

        return analysis.copy(recommendations = recommendations(analysis))
    }

    private fun buildPower(events: List<CaptureLog.Event>): Power {
        val temps = events.mapNotNull { it.meta["batteryTempC"]?.toFloatOrNull() }
        val heartbeats = events
            .filter { it.gate == "SERVICE" && it.text == "heartbeat" }
            .mapNotNull { event ->
                event.meta["batteryPct"]?.toIntOrNull()?.let { pct -> event.ts to pct }
            }
            .sortedBy { it.first }
        val dischargeSamples = mutableListOf<Pair<Long, Int>>()
        var bestDrop: Pair<Int, Float>? = null
        fun finishDischargeSession() {
            if (dischargeSamples.size < 2) return
            val first = dischargeSamples.first()
            val last = dischargeSamples.last()
            val drop = first.second - last.second
            val hours = (last.first - first.first) / 3_600_000f
            if (drop > 0 && hours > 0f && (bestDrop == null || drop > bestDrop!!.first)) {
                bestDrop = drop to (drop / hours)
            }
        }
        for ((ts, pct) in heartbeats) {
            val previous = dischargeSamples.lastOrNull()
            if (previous == null || pct <= previous.second) {
                dischargeSamples += ts to pct
            } else {
                finishDischargeSession()
                dischargeSamples.clear()
                dischargeSamples += ts to pct
            }
        }
        finishDischargeSession()
        val thermalCounts = topCounts(
            events.mapNotNull { it.meta["thermalStatusLabel"] ?: it.meta["thermalStatus"] }
        )
        val worstThermal = events
            .mapNotNull { event ->
                val numeric = event.meta["thermalStatus"]?.toIntOrNull()
                val label = event.meta["thermalStatusLabel"]
                numeric?.let { it to (label ?: it.toString()) }
            }
            .maxByOrNull { it.first }
            ?.second
        return Power(
            avgBatteryTempC = temps.averageFloatOrNull(),
            maxBatteryTempC = temps.maxOrNull(),
            thermalStatusCounts = thermalCounts,
            worstThermalStatus = worstThermal,
            observedBatteryDropPct = bestDrop?.first,
            observedBatteryDropPerHourPct = bestDrop?.second
        )
    }

    private fun buildFunnel(events: List<CaptureLog.Event>): Funnel =
        Funnel(
            gateAccepted = events.count { it.isAsrGateDecision() && it.result == "OK" },
            gateRejected = events.count { it.isAsrGateDecision() && it.result == "NO_MATCH" },
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

    private fun CaptureLog.Event.isAsrGateDecision(): Boolean =
        gate == "ASR_GATE" &&
            text != "segment_finalized" &&
            text != "uncertain_gate_fallback_blocked"

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
        if (analysis.quality.discardedPossibleFalseNegatives > 0) {
            add("Hay descartes con señal de utilidad/actionability: revisar qualityDecisions bucket=discarded_possible_false_negative como candidatos de recall perdido.")
        }
        if (analysis.quality.acceptedLowConfidence > 0) {
            add("Hay aceptaciones con baja confianza: revisar qualityDecisions bucket=accepted_low_confidence como posibles falsos positivos.")
        }
        if (analysis.quality.savedPerFinalTranscriptPct in 1..40 && analysis.funnel.finalTranscripts >= 5) {
            add("Baja conversión de transcripción a entrada guardada: mirar ejemplos INTENT NO_MATCH y frases frecuentes para detectar pérdida por gate/intents.")
        }
        if (analysis.latency.p95DecodeMs != null && analysis.latency.p95DecodeMs > 8_000L) {
            add("ASR lento en p95: considerar modelo Whisper menor, menos ventana de audio o descarga de modelo por SoC.")
        }
        if (analysis.power.maxBatteryTempC != null && analysis.power.maxBatteryTempC >= 40f) {
            add("Temperatura de batería alta durante la escucha: correlacionar batteryTempC/thermalStatus con ASR_FINAL y ASR_GATE para localizar picos de CPU.")
        }
        if (analysis.power.worstThermalStatus != null &&
            analysis.power.worstThermalStatus !in setOf("none", "0")
        ) {
            add("Android reportó presión térmica: revisar analysis.power.thermalStatusCounts y reducir ventanas/modelo si coincide con decodes largos.")
        }
        if (analysis.quality.fallbackEvents > 0) {
            add("Hay eventos de fallback: separar métricas de SpeechRecognizer frente a ASR local para saber si la promesa local-first se está cumpliendo.")
        }
        if (analysis.quality.segmentsClosedByCap >= 3) {
            add("Hay segmentos cerrados por cap de 30s: esto indica voz/ruido continuo sin trigger; revisar si el gate está demasiado estricto o si el entorno era conversación ambiental.")
        }
        if (analysis.quality.uncertainFallbacksBlocked >= 3) {
            add("Hay fallbacks inciertos bloqueados por batería/cooldown: el ahorro está funcionando, pero puede limitar recall en entornos ruidosos.")
        }
        if (analysis.quality.unexpectedServiceStops > 0) {
            add("Hay destrucciones del servicio sin parada explícita cercana: revisar watchdog, permisos de segundo plano y restricciones del fabricante.")
        }
        if (analysis.quality.mediaPlaybackPauses > 0 || analysis.quality.mediaPlaybackBlockedWindows > 0) {
            add("La escucha se pauso por audio de otra app: revisar si coincide con YouTube/Spotify y confirmar que no se guardan eventos de media externa.")
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

    private fun List<CaptureLog.Event>.countSegmentFinalized(reason: String): Int =
        count {
            it.gate == "ASR_GATE" &&
                it.text == "segment_finalized" &&
                it.meta["reason"] == reason
        }

    private fun countUnexpectedServiceStops(events: List<CaptureLog.Event>): Int {
        val stopRequests = events
            .filter { it.gate == "SERVICE" && it.text == "service_stop_requested" }
            .map { it.ts }
        return events.count { event ->
            event.gate == "SERVICE" &&
                event.result == "REJECT" &&
                event.text == "onDestroy" &&
                stopRequests.none { stopTs -> stopTs in (event.ts - 5_000L)..event.ts }
        }
    }

    private fun List<Long>.averageOrNull(): Long? =
        if (isEmpty()) null else average().roundToInt().toLong()

    private fun List<Float>.averageFloatOrNull(): Float? =
        if (isEmpty()) null else average().toFloat()

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
