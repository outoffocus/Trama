package com.trama.app.diagnostics

import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.Recording
import com.trama.shared.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsAnalyzerTest {

    @Test
    fun `analysis computes funnel rates and recommendations`() {
        val events = listOf(
            event("ASR_GATE", "OK", "recuerdame comprar leche"),
            event("ASR_FINAL", "OK", "recuerdame comprar leche", mapOf("engine" to "vosk -> whisper", "decodeMs" to "900", "windowMs" to "5000")),
            event("SPEAKER", "REJECT", "anuncio de television", mapOf("sim" to "0.31")),
            event("SPEAKER", "REJECT", "noticias de la television", mapOf("sim" to "0.28")),
            event("SPEAKER", "REJECT", "publicidad del programa", mapOf("sim" to "0.22")),
            event("ASR_FINAL", "OK", "recuerdame llamar a maria", mapOf("engine" to "vosk -> whisper", "decodeMs" to "1200", "windowMs" to "6000")),
            event("SPEAKER", "OK", meta = mapOf("sim" to "0.82")),
            event("INTENT", "OK", "recuerdame llamar a maria"),
            event("SAVE", "OK", "llamar a maria"),
            event("LLM", "REJECT", "llamar", mapOf("discardReason" to "missing_object"))
        )
        val entries = listOf(
            entry("recuerdame llamar a maria", status = "SUGGESTED"),
            entry("anuncio de television", status = "DISCARDED")
        )

        val analysis = DiagnosticsAnalyzer.analyze(events, entries, emptyList())

        assertEquals(2, analysis.funnel.finalTranscripts)
        assertEquals(3, analysis.funnel.speakerRejected)
        assertEquals(75, analysis.quality.speakerRejectRatePct)
        assertEquals(50, analysis.quality.savedPerFinalTranscriptPct)
        assertTrue(analysis.engines.any { it.value == "whisper" && it.count == 2 })
        assertTrue(analysis.rejectReasons.any { it.value == "missing_object" })
        assertTrue(analysis.frequentPhrases.any { it.value.contains("llamar") })
        assertTrue(analysis.recommendations.any { it.contains("Speaker verification") })
    }

    @Test
    fun `recordings without actions become examples`() {
        val events = listOf(
            event("RECORDING", "NO_MATCH", "reunion sin acciones", mapOf("id" to "7"))
        )
        val recordings = listOf(
            Recording(
                id = 7,
                transcription = "Hablamos del proveedor de Madrid pero no habia tareas claras.",
                durationSeconds = 120,
                source = Source.PHONE
            )
        )

        val analysis = DiagnosticsAnalyzer.analyze(events, emptyList(), recordings)

        assertEquals(1, analysis.funnel.recordingWithoutActions)
        assertEquals(1, analysis.examples.recordingsWithoutActions.size)
    }

    private fun event(
        gate: String,
        result: String,
        text: String? = null,
        meta: Map<String, String> = emptyMap()
    ) = CaptureLog.Event(
        ts = 1,
        gate = gate,
        result = result,
        text = text,
        meta = meta
    )

    private fun entry(
        text: String,
        status: String
    ) = DiaryEntry(
        text = text,
        keyword = "test",
        category = "Test",
        confidence = 1.0f,
        source = Source.PHONE,
        duration = 0,
        status = status
    )
}
