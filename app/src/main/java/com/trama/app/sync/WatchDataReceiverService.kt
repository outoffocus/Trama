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
            """\[(música|musica|silencio|inaudible|ruido|audio vacío|blank_audio|music|noise|silence)\]""" +
            """|\(Música\)|\(música\)""",
            RegexOption.IGNORE_CASE
        )

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
                val metadata = Json.decodeFromString<WatchAudioSyncMetadata>(metadataJson)

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
                Log.d(TAG, "Watch audio: ${pcm.size} samples (${durationSec}s), RMS=${"%.1f".format(rms)}")

                if (rms < MIN_AUDIO_RMS) {
                    Log.w(TAG, "Watch audio too quiet (RMS=${"%.1f".format(rms)} < $MIN_AUDIO_RMS), skipping — mic may not be capturing speech")
                    return@launch
                }

                val whisper = SherpaWhisperAsrEngine(applicationContext)
                val rawTranscript = if (whisper.isAvailable) {
                    transcribeWithChunking(whisper, pcm, metadata.sampleRateHz)
                } else {
                    ""
                }

                // Strip Whisper noise/hallucination tokens (generated for silence or music)
                val transcript = WHISPER_NOISE_TOKENS_RE.replace(rawTranscript, "").trim()
                if (rawTranscript.isNotBlank() && transcript.isBlank()) {
                    Log.w(TAG, "Watch audio transcript was only noise tokens: '$rawTranscript' — treating as empty")
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
