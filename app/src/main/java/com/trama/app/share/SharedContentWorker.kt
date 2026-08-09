package com.trama.app.share

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.trama.app.audio.SherpaWhisperAsrEngine
import com.trama.app.summary.ActionItemProcessor
import com.trama.app.summary.RecordingProcessorWorker
import com.trama.shared.audio.CapturedAudioWindow
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.Recording
import com.trama.shared.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

class SharedContentWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val text = inputData.getString(KEY_TEXT)?.trim().orEmpty()
        val audioPath = inputData.getString(KEY_AUDIO_PATH)?.trim().orEmpty()

        return try {
            when {
                text.isNotBlank() -> importText(text)
                audioPath.isNotBlank() -> importAudio(File(audioPath))
                else -> return Result.failure()
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Shared content import failed", e)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private suspend fun importText(text: String) {
        val repository = DatabaseProvider.getRepository(applicationContext)
        val entryId = repository.insert(
            DiaryEntry(
                text = text,
                keyword = "compartido",
                category = "Compartido",
                confidence = 1f,
                source = Source.PHONE,
                duration = 0,
                isManual = true
            )
        )
        ActionItemProcessor(applicationContext).process(entryId, text, repository)
        Log.i(TAG, "Imported shared text as entry $entryId")
    }

    private suspend fun importAudio(file: File) {
        require(file.exists()) { "Shared audio file does not exist: ${file.absolutePath}" }

        val asrEngine = SherpaWhisperAsrEngine(applicationContext)
        require(asrEngine.isAvailable) { "Offline ASR is not available" }

        val window = decodeAudio(file)
        val transcript = asrEngine.transcribe(window, languageTag = "es")?.text?.trim().orEmpty()
        require(transcript.isNotBlank()) { "Shared audio produced an empty transcript" }

        val repository = DatabaseProvider.getRepository(applicationContext)
        val recordingId = repository.insertRecording(
            Recording(
                title = file.nameWithoutExtension.take(80).ifBlank { null },
                transcription = transcript,
                durationSeconds = (window.durationMs() / 1000L).toInt(),
                source = Source.PHONE,
                processedLocally = true,
                processedBy = asrEngine.name
            )
        )
        RecordingProcessorWorker.enqueue(applicationContext, recordingId)
        Log.i(TAG, "Imported shared audio ${file.name} as recording $recordingId")
    }

    private suspend fun decodeAudio(file: File): CapturedAudioWindow = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("No audio track found")

            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: error("Missing audio MIME")
            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(inputFormat, null, null, 0)
                start()
            }

            val decoded = ShortPcmBuffer()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var outputSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var outputChannels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        inputBuffer?.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer ?: ByteBuffer.allocate(0), 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime,
                                extractor.sampleFlags
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        outputSampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        outputChannels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        pcmEncoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else {
                            AudioFormat.ENCODING_PCM_16BIT
                        }
                    }
                    outputIndex >= 0 -> {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && info.size > 0) {
                            outputBuffer.position(info.offset)
                            outputBuffer.limit(info.offset + info.size)
                            appendMonoPcm(decoded, outputBuffer.slice(), outputChannels, pcmEncoding)
                        }
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            val monoPcm = decoded.toShortArray()
            val resampled = if (outputSampleRate == TARGET_SAMPLE_RATE_HZ) {
                monoPcm
            } else {
                resampleLinear(monoPcm, outputSampleRate, TARGET_SAMPLE_RATE_HZ)
            }
            CapturedAudioWindow(
                preRollPcm = shortArrayOf(),
                livePcm = resampled,
                sampleRateHz = TARGET_SAMPLE_RATE_HZ
            )
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private fun appendMonoPcm(
        out: ShortPcmBuffer,
        buffer: ByteBuffer,
        channels: Int,
        encoding: Int
    ) {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                while (buffer.remaining() >= 4 * channels) {
                    var sum = 0f
                    repeat(channels) { sum += buffer.float.coerceIn(-1f, 1f) }
                    out.append(((sum / channels) * Short.MAX_VALUE).roundToInt().toShort())
                }
            }
            else -> {
                while (buffer.remaining() >= 2 * channels) {
                    var sum = 0
                    repeat(channels) { sum += buffer.short.toInt() }
                    out.append((sum / channels).toShort())
                }
            }
        }
    }

    private fun resampleLinear(input: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (input.isEmpty() || fromRate <= 0 || toRate <= 0) return input
        val outputSize = ((input.size.toLong() * toRate) / fromRate).toInt().coerceAtLeast(1)
        val output = ShortArray(outputSize)
        val ratio = fromRate.toDouble() / toRate.toDouble()
        for (i in output.indices) {
            val source = i * ratio
            val left = source.toInt().coerceIn(0, input.lastIndex)
            val right = (left + 1).coerceAtMost(input.lastIndex)
            val fraction = source - left
            output[i] = (input[left] + (input[right] - input[left]) * fraction).roundToInt().toShort()
        }
        return output
    }

    private class ShortPcmBuffer(initialCapacity: Int = TARGET_SAMPLE_RATE_HZ * 30) {
        private var values = ShortArray(initialCapacity)
        private var size = 0

        fun append(value: Short) {
            if (size == values.size) {
                values = values.copyOf(values.size * 2)
            }
            values[size++] = value
        }

        fun toShortArray(): ShortArray = values.copyOf(size)
    }

    companion object {
        const val KEY_TEXT = "text"
        const val KEY_AUDIO_PATH = "audio_path"
        private const val TAG = "SharedContentWorker"
        private const val TARGET_SAMPLE_RATE_HZ = 16_000
        private const val CODEC_TIMEOUT_US = 10_000L
    }
}
