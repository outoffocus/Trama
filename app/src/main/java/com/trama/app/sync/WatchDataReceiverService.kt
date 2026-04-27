package com.trama.app.sync

import android.util.Log
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.trama.app.audio.SherpaWhisperAsrEngine
import com.trama.app.speech.EntryValidator
import com.trama.app.speech.PersonalDictionary
import com.trama.app.ui.SettingsDataStore
import com.trama.shared.audio.CapturedAudioWindow
import com.trama.app.service.RecordingState
import com.trama.app.service.ServiceController
import com.trama.shared.sync.MicCoordinator
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.data.DiaryRepository
import com.trama.shared.model.Recording
import com.trama.shared.model.RecordingStatus
import com.trama.shared.model.Source
import com.trama.shared.model.SyncPayload
import com.trama.shared.model.WatchAudioSyncMetadata
import com.trama.app.summary.ActionItemProcessor
import com.trama.app.summary.RecordingProcessor
import com.trama.shared.model.DiaryEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/**
 * Receives diary entries and mic coordination from the watch.
 * - DataClient /trama/sync: entry sync
 * - MessageClient /trama/mic: PAUSE/RESUME commands
 */
class WatchDataReceiverService : WearableListenerService() {

    companion object {
        private const val TAG = "WatchDataReceiver"
        private const val SYNC_PATH = "/trama/sync"
        private const val AUDIO_RECORDING_PATH_PREFIX = "/trama/audio-recording"
        private const val MIC_PATH = "/trama/mic"
        private const val SYNC_REQUEST_PATH = "/trama/request-full-sync"
        private const val WATCH_DEBUG_PATH = MicCoordinator.WATCH_DEBUG_PATH
        private const val CMD_PAUSE = MicCoordinator.CMD_PAUSE
        private const val CMD_RESUME = MicCoordinator.CMD_RESUME
        private const val CMD_START_KEYWORD = MicCoordinator.CMD_START_KEYWORD
        private const val CMD_START_RECORDING = MicCoordinator.CMD_START_RECORDING

        /** Minimum RMS energy to consider audio non-silent. PCM16 range ±32768.
         *  Quiet speech ≈ 500–3000 RMS. Silent/muted mic ≈ 0–50 RMS. */
        private const val MIN_AUDIO_RMS = 80.0

        /** Whisper tokens generated for silence/music — not real speech content. */
        private val WHISPER_NOISE_TOKENS_RE = Regex(
            """\[(música|musica|silencio|inaudible|ruido|audio vacío|blank_audio|""" +
            """music|noise|silence|motor|engine|car|vehicle|wind|viento|""" +
            """applause|aplausos|laughter|risas|coughing|tos|breathing|""" +
            """respiración|crowd|multitud|background[_ ]?noise|ambient)\]""" +
            """|\(Música\)|\(música\)""",
            RegexOption.IGNORE_CASE
        )

        /**
         * Any remaining single bracketed tag after known-noise stripping is almost
         * certainly a Whisper descriptor for non-speech audio (e.g. "[Motor]", "[Wind]").
         * Short tags (≤40 chars) with no alphanumeric content outside the brackets
         * mean the whole recording was non-speech — treat as empty so the LLM is
         * not fed meaningless context that becomes a garbage summary.
         */
        private val ONLY_BRACKETED_TAG_RE = Regex("""^\s*[\[(][^\]\)]{1,40}[\])]\s*$""")

        /**
         * Whisper's encoder operates on a fixed 30-second mel spectrogram window.
         * We use 29 seconds per chunk to stay safely under the limit and avoid
         * truncation artifacts at the boundary. For audio shorter than this,
         * a single pass is used.
         */
        private const val WHISPER_CHUNK_SECONDS = 29
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val repository = DatabaseProvider.getRepository(applicationContext)

        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path ?: return@forEach
                when {
                    path == SYNC_PATH -> {
                        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                        val json = dataMap.getString("payload") ?: return@forEach
                        handleEntries(json, repository)
                    }
                    path.startsWith(AUDIO_RECORDING_PATH_PREFIX) -> {
                        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                        handleAudioRecording(dataMap, repository)
                    }
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            MIC_PATH -> {
                val command = String(messageEvent.data)
                Log.i(TAG, "Mic command from watch: $command")

                when (command) {
                    CMD_PAUSE -> {
                        ServiceController.notifyWatchActive()
                        ServiceController.stopByWatch(applicationContext)
                        Log.i(TAG, "Phone stopped (watch is active)")
                    }
                    CMD_RESUME -> {
                        ServiceController.notifyWatchInactive()
                        if (ServiceController.shouldBeRunning(applicationContext)) {
                            ServiceController.start(applicationContext)
                            Log.i(TAG, "Phone service resumed (watch released)")
                        }
                    }
                    CMD_START_KEYWORD -> {
                        ServiceController.notifyWatchInactive()
                        ServiceController.start(applicationContext)
                        Log.i(TAG, "Phone started keyword listening (watch transferred)")
                    }
                    CMD_START_RECORDING -> {
                        ServiceController.notifyWatchInactive()
                        ServiceController.startRecording(applicationContext)
                        Log.i(TAG, "Phone started recording (watch transferred)")
                    }
                }
            }
            SYNC_REQUEST_PATH -> {
                Log.i(TAG, "Full sync requested by watch")
                scope.launch {
                    val repository = DatabaseProvider.getRepository(applicationContext)
                    val syncer = PhoneToWatchSyncer(applicationContext, repository)
                    syncer.syncAllToWatch()
                }
            }
            WATCH_DEBUG_PATH -> {
                val payload = String(messageEvent.data)
                val sep = payload.indexOf('|')
                val status = if (sep >= 0) payload.substring(0, sep) else payload
                val trigger = if (sep >= 0) payload.substring(sep + 1).takeIf { it.isNotBlank() } else null
                Log.i(TAG, "Watch debug: $status${trigger?.let { " ('${it.take(40)}')" } ?: ""}")
                scope.launch {
                    SettingsDataStore(applicationContext).updateWatchDebug(status, trigger)
                }
            }
        }
    }

    private fun handleEntries(json: String, repository: DiaryRepository) {
        scope.launch {
            try {
                val payload = Json.decodeFromString<SyncPayload>(json)

                // Sync diary entries
                var insertedEntries = 0
                for (syncEntry in payload.entries) {
                    val entry = syncEntry.toDiaryEntry()
                    if (!repository.existsByCreatedAtAndText(entry.createdAt, entry.text)) {
                        val insertedId = repository.insert(entry)
                        insertedEntries++
                        // Process with AI to generate cleanText / actionType / dueDate / priority
                        try {
                            ActionItemProcessor(applicationContext).process(insertedId, entry.text, repository)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to AI-process watch entry $insertedId", e)
                        }
                    }
                }

                // Sync recordings and process them with Gemini
                var insertedRecordings = 0
                for (syncRecording in payload.recordings) {
                    if (!repository.existsRecordingByCreatedAt(syncRecording.createdAt)) {
                        val recording = syncRecording.toRecording()
                        val recordingId = repository.insertRecording(recording)
                        insertedRecordings++
                        Log.i(TAG, "Received recording from watch (id=$recordingId), processing...")

                        // Process with Gemini (or local fallback)
                        try {
                            val processor = RecordingProcessor(applicationContext)
                            processor.process(recordingId, repository)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to process recording $recordingId", e)
                            repository.updateRecordingStatus(recordingId, RecordingStatus.FAILED)
                        }
                    }
                }

                Log.i(TAG, "Received from watch: ${payload.entries.size} entries (inserted $insertedEntries), " +
                        "${payload.recordings.size} recordings (inserted $insertedRecordings)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process sync data", e)
            }
        }
    }

    private fun handleAudioRecording(dataMap: DataMap, repository: DiaryRepository) {
        scope.launch {
            try {
                val metadataJson = dataMap.getString("metadata") ?: return@launch
                val asset = dataMap.getAsset("audio_pcm16") ?: return@launch
                val rawMetadata = Json.decodeFromString<WatchAudioSyncMetadata>(metadataJson)
                // Guard against corrupted/zero sampleRateHz — would cause divide-by-zero in
                // duration/RMS math and the capture would be silently discarded.
                val sampleRateHz = rawMetadata.sampleRateHz.coerceIn(8_000, 48_000)
                val metadata = if (sampleRateHz == rawMetadata.sampleRateHz) rawMetadata
                    else rawMetadata.copy(sampleRateHz = sampleRateHz)

                if (repository.existsRecordingByCreatedAt(metadata.createdAt)) {
                    Log.i(TAG, "Watch audio already imported for ${metadata.createdAt}")
                    return@launch
                }

                val assetInput = Wearable.getDataClient(applicationContext)
                    .getFdForAsset(asset)
                    .await()
                    .inputStream
                    ?: return@launch

                val pcmBytes = assetInput.use { it.readBytes() }
                val pcm = bytesToShortArray(pcmBytes)
                if (pcm.isEmpty()) {
                    Log.w(TAG, "Empty watch PCM payload")
                    return@launch
                }

                // Detect silent recordings: Whisper outputs [música]/[silencio] for silence,
                // which then fools the LLM into thinking the recording has content.
                val rms = sqrt(pcm.sumOf { it.toLong() * it.toLong() }.toDouble() / pcm.size)
                val durationSec = pcm.size.toFloat() / metadata.sampleRateHz
                val expectedBytes = metadata.pcmByteCount
                val expectedSamples = metadata.pcmSampleCount
                val byteDelta = expectedBytes?.let { pcmBytes.size - it }
                val sampleDelta = expectedSamples?.let { pcm.size - it }
                // Peak amplitude reveals silent-with-noise-floor captures that still
                // meet the RMS threshold (low signal + constant low noise).
                val peak = pcm.maxOfOrNull { kotlin.math.abs(it.toInt()) } ?: 0
                Log.i(
                    TAG,
                    "Watch audio received: ${pcm.size} samples (${"%.1f".format(durationSec)}s) " +
                        "@${metadata.sampleRateHz}Hz, ${pcmBytes.size} bytes, " +
                        "RMS=${"%.1f".format(rms)}, peak=$peak, " +
                        "kind=${metadata.kind}, source=${metadata.source}, " +
                        "expected=${expectedSamples ?: "?"} samples/${expectedBytes ?: "?"} bytes"
                )

                if (byteDelta != null && byteDelta != 0 || sampleDelta != null && sampleDelta != 0) {
                    Log.w(
                        TAG,
                        "Watch PCM payload size mismatch: byteDelta=${byteDelta ?: "?"}, " +
                            "sampleDelta=${sampleDelta ?: "?"}, watchRms=${metadata.rms ?: "?"}, " +
                            "phoneRms=${"%.1f".format(rms)}"
                    )
                }

                if (rms < MIN_AUDIO_RMS) {
                    Log.w(TAG, "Watch audio too quiet (RMS=${"%.1f".format(rms)} < $MIN_AUDIO_RMS), saving failed recording for inspection")
                    saveFailedWatchRecording(
                        metadata = metadata,
                        repository = repository,
                        reason = "[Audio del reloj recibido sin voz util]"
                    )
                    return@launch
                }

                val whisper = SherpaWhisperAsrEngine(applicationContext)
                val rawTranscript = if (whisper.isAvailable) {
                    transcribeWithChunking(whisper, pcm, metadata.sampleRateHz)
                } else {
                    ""
                }

                // Strip Whisper noise/hallucination tokens (generated for silence or music)
                val stripped = WHISPER_NOISE_TOKENS_RE.replace(rawTranscript, "").trim()
                val transcript = if (ONLY_BRACKETED_TAG_RE.matches(stripped)) {
                    Log.w(TAG, "Watch audio transcript was a single descriptor tag: '$stripped' — treating as empty")
                    ""
                } else {
                    stripped
                }
                if (rawTranscript.isNotBlank() && transcript.isBlank()) {
                    Log.w(TAG, "Watch audio transcript was only noise tokens: '$rawTranscript' — treating as empty")
                }

                if (metadata.kind == "CONTEXTUAL_TRIGGER") {
                    handleContextualWatchCapture(metadata, transcript, repository)
                    return@launch
                }

                val effectiveTranscript = transcript.ifBlank { metadata.triggerText.orEmpty() }

                val recordingId = repository.insertRecording(
                    Recording(
                        transcription = effectiveTranscript.ifBlank { "[Audio del reloj pendiente de transcripcion]" },
                        durationSeconds = metadata.durationSeconds,
                        source = Source.valueOf(metadata.source),
                        createdAt = metadata.createdAt,
                        processingStatus = if (effectiveTranscript.isBlank()) RecordingStatus.FAILED else RecordingStatus.PENDING,
                        isSynced = true
                    )
                )

                if (effectiveTranscript.isNotBlank()) {
                    try {
                        RecordingProcessor(applicationContext).process(recordingId, repository)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to process imported watch audio $recordingId", e)
                        repository.updateRecordingStatus(recordingId, RecordingStatus.FAILED)
                    }
                }

                Log.i(TAG, "Imported watch audio recording (id=$recordingId)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process watch audio payload", e)
            }
        }
    }

    private suspend fun saveFailedWatchRecording(
        metadata: WatchAudioSyncMetadata,
        repository: DiaryRepository,
        reason: String
    ) {
        if (repository.existsRecordingByCreatedAt(metadata.createdAt)) return
        repository.insertRecording(
            Recording(
                transcription = reason,
                durationSeconds = metadata.durationSeconds,
                source = Source.valueOf(metadata.source),
                createdAt = metadata.createdAt,
                processingStatus = RecordingStatus.FAILED,
                isSynced = true
            )
        )
    }

    private suspend fun handleContextualWatchCapture(
        metadata: WatchAudioSyncMetadata,
        transcript: String,
        repository: DiaryRepository
    ) {
        val text = transcript.trim()
        if (text.isBlank()) {
            Log.w(TAG, "Contextual watch capture produced empty transcript; saving failed recording for inspection")
            saveFailedWatchRecording(
                metadata = metadata,
                repository = repository,
                reason = metadata.triggerText?.takeIf { it.isNotBlank() }
                    ?: "[Audio contextual del reloj sin transcripcion]"
            )
            return
        }

        if (repository.existsByCreatedAtAndText(metadata.createdAt, text)) {
            Log.i(TAG, "Contextual watch entry already imported for ${metadata.createdAt}")
            return
        }

        val detector = com.trama.shared.speech.IntentDetector()
        val detection = detector.detect(text)
        val intentId = detection?.pattern?.id
            ?: detection?.customKeyword
            ?: metadata.intentId
            ?: "nota"
        val label = detection?.label
            ?: metadata.label
            ?: "Reloj"

        val validation = runCatching {
            // Cap the validator at 5s so a slow/unreachable Gemini call can't stall
            // the entire audio pipeline; fall back to accepting the raw transcript.
            withTimeoutOrNull(5_000) { EntryValidator(applicationContext).validate(text) }
        }.getOrElse { error ->
            Log.w(TAG, "Watch contextual validation failed, proceeding without correction", error)
            null
        }
        val correctedText = PersonalDictionary(applicationContext).correct(
            validation?.correctedText ?: text
        )

        val entry = DiaryEntry(
            text = text,
            keyword = intentId,
            category = label,
            confidence = validation?.confidence ?: 0.9f,
            source = Source.valueOf(metadata.source),
            duration = metadata.durationSeconds,
            correctedText = correctedText,
            wasReviewedByLLM = validation?.correctedText != null ||
                validation?.reason?.contains("IA") == true,
            llmConfidence = validation?.confidence
        )

        val entryId = repository.insert(entry)
        Log.i(TAG, "Imported contextual watch capture as diary entry (id=$entryId): '${text.take(80)}'")

        try {
            ActionItemProcessor(applicationContext).process(entryId, correctedText, repository)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process contextual watch entry $entryId", e)
        }
    }

    /**
     * Transcribe PCM audio with Whisper, splitting into chunks when the audio is
     * longer than Whisper's 30-second mel spectrogram window.
     *
     * Whisper silently truncates audio beyond 30 seconds — it only encodes the first
     * 480,000 samples (30s × 16kHz). For longer recordings we split into WHISPER_CHUNK_SECONDS
     * windows and concatenate the results.
     */
    private suspend fun transcribeWithChunking(
        whisper: SherpaWhisperAsrEngine,
        pcm: ShortArray,
        sampleRateHz: Int
    ): String {
        val chunkSamples = sampleRateHz * WHISPER_CHUNK_SECONDS

        if (pcm.size <= chunkSamples) {
            // Short enough for a single pass — no chunking needed
            return whisper.transcribe(
                CapturedAudioWindow(
                    preRollPcm = shortArrayOf(),
                    livePcm = pcm,
                    sampleRateHz = sampleRateHz
                ),
                languageTag = "es"
            )?.text?.trim().orEmpty()
        }

        // Audio longer than 29s: split into chunks and concatenate
        val numChunks = (pcm.size + chunkSamples - 1) / chunkSamples
        Log.d(TAG, "Watch audio ${pcm.size} samples → $numChunks Whisper chunks of ${WHISPER_CHUNK_SECONDS}s")

        val parts = mutableListOf<String>()
        var offset = 0
        var chunkIdx = 1
        while (offset < pcm.size) {
            val end = minOf(offset + chunkSamples, pcm.size)
            val chunk = pcm.copyOfRange(offset, end)
            val text = whisper.transcribe(
                CapturedAudioWindow(
                    preRollPcm = shortArrayOf(),
                    livePcm = chunk,
                    sampleRateHz = sampleRateHz
                ),
                languageTag = "es"
            )?.text?.trim().orEmpty()

            Log.d(TAG, "  chunk $chunkIdx/$numChunks (${chunk.size} samples): '${text.take(60)}'")
            if (text.isNotBlank()) parts.add(text)
            offset += chunkSamples
            chunkIdx++
        }

        return parts.joinToString(" ")
    }

    private fun bytesToShortArray(bytes: ByteArray): ShortArray {
        if (bytes.size < 2) return shortArrayOf()
        val result = ShortArray(bytes.size / 2)
        var byteIndex = 0
        for (sampleIndex in result.indices) {
            val lo = bytes[byteIndex].toInt() and 0xFF
            val hi = bytes[byteIndex + 1].toInt()
            result[sampleIndex] = ((hi shl 8) or lo).toShort()
            byteIndex += 2
        }
        return result
    }
}
