package com.trama.app.audio

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.trama.shared.audio.AssetFileCache
import com.trama.shared.audio.AsrTranscript
import com.trama.shared.audio.CapturedAudioWindow
import com.trama.shared.audio.OnDeviceAsrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Whisper backend powered by sherpa-onnx.
 *
 * It activates only when the expected model bundle exists in app assets.
 * Audio stays in memory; only model files are copied to app-private storage.
 */
class SherpaWhisperAsrEngine(
    context: Context
) : OnDeviceAsrEngine {
    companion object {
        private const val TAG = "SherpaWhisperAsr"
        private const val MODEL_DIR = "asr/whisper"
        private val CANDIDATE_BUNDLES = listOf(
            WhisperBundle(
                encoderAssets = listOf(
                    "$MODEL_DIR/small-encoder.int8.onnx",
                    "$MODEL_DIR/small-encoder.onnx"
                ),
                decoderAssets = listOf(
                    "$MODEL_DIR/small-decoder.int8.onnx",
                    "$MODEL_DIR/small-decoder.onnx"
                ),
                tokensAssets = listOf(
                    "$MODEL_DIR/small-tokens.txt"
                ),
                language = "es",
                label = "whisper-small-multilingual"
            ),
            WhisperBundle(
                encoderAssets = listOf(
                    "$MODEL_DIR/medium-encoder.int8.onnx",
                    "$MODEL_DIR/medium-encoder.onnx"
                ),
                decoderAssets = listOf(
                    "$MODEL_DIR/medium-decoder.int8.onnx",
                    "$MODEL_DIR/medium-decoder.onnx"
                ),
                tokensAssets = listOf(
                    "$MODEL_DIR/medium-tokens.txt"
                ),
                language = "es",
                label = "whisper-medium-multilingual"
            ),
            WhisperBundle(
                encoderAssets = listOf(
                    "$MODEL_DIR/base-encoder.int8.onnx",
                    "$MODEL_DIR/base-encoder.onnx"
                ),
                decoderAssets = listOf(
                    "$MODEL_DIR/base-decoder.int8.onnx",
                    "$MODEL_DIR/base-decoder.onnx"
                ),
                tokensAssets = listOf(
                    "$MODEL_DIR/base-tokens.txt"
                ),
                language = "es",
                label = "whisper-base-multilingual"
            ),
            WhisperBundle(
                encoderAssets = listOf(
                    "$MODEL_DIR/tiny-encoder.int8.onnx",
                    "$MODEL_DIR/tiny-encoder.onnx"
                ),
                decoderAssets = listOf(
                    "$MODEL_DIR/tiny-decoder.int8.onnx",
                    "$MODEL_DIR/tiny-decoder.onnx"
                ),
                tokensAssets = listOf(
                    "$MODEL_DIR/tiny-tokens.txt"
                ),
                language = "es",
                label = "whisper-tiny-multilingual"
            )
        )
    }

    private data class WhisperBundle(
        val encoderAssets: List<String>,
        val decoderAssets: List<String>,
        val tokensAssets: List<String>,
        val language: String,
        val label: String
    )

    override val name: String
        get() = "sherpa-whisper:${locateBundle()?.label ?: "unavailable"}"

    private val appContext = context.applicationContext
    private val assetCache = AssetFileCache(appContext)
    private val recognizerMutex = Mutex()
    private var recognizer: OfflineRecognizer? = null
    private var activeHotwordsHash: Int = 0
    private var selectedBundle: WhisperBundle? = null
    private var selectedEncoderAsset: String? = null
    private var selectedDecoderAsset: String? = null
    private var selectedTokensAsset: String? = null

    // Words to bias toward during decoding (proper nouns, acronyms, custom vocabulary).
    // Changing this invalidates the cached recognizer.
    @Volatile private var hotwords: List<String> = emptyList()

    /**
     * Update the hotwords list used to bias Whisper's decoder.
     * Call with custom keywords, place names, acronyms, etc.
     * The recognizer is rebuilt lazily on the next transcription call.
     */
    fun setHotwords(words: List<String>) {
        val filtered = words.filter { it.isNotBlank() }.distinct()
        if (filtered.toSet().hashCode() == activeHotwordsHash) return
        hotwords = filtered
        Log.i(TAG, "Hotwords updated: ${filtered.size} words")
    }

    override val isAvailable: Boolean
        get() = locateBundle() != null

    override suspend fun transcribe(window: CapturedAudioWindow, languageTag: String): AsrTranscript? {
        val pcm = window.mergedPcm()
        if (pcm.isEmpty()) return null

        return withContext(Dispatchers.IO) {
            recognizerMutex.withLock {
                val currentHotwords = hotwords
                val newHash = currentHotwords.toSet().hashCode()
                if (recognizer == null || newHash != activeHotwordsHash) {
                    recognizer = createRecognizer(languageTag, currentHotwords)
                    activeHotwordsHash = newHash
                }
                val recognizer = recognizer!!

                val samples = FloatArray(pcm.size) { index ->
                    pcm[index] / 32768.0f
                }

                val stream = recognizer.createStream()
                try {
                    stream.acceptWaveform(samples, window.sampleRateHz)
                    recognizer.decode(stream)
                    val result = recognizer.getResult(stream).text?.trim().orEmpty()
                    if (result.isBlank()) null else AsrTranscript(text = result)
                } finally {
                    stream.release()
                }
            }
        }
    }

    private fun locateBundle(): WhisperBundle? {
        return selectedBundle ?: CANDIDATE_BUNDLES.firstOrNull { bundle ->
            val encoder = bundle.encoderAssets.firstOrNull(assetCache::assetExists)
            val decoder = bundle.decoderAssets.firstOrNull(assetCache::assetExists)
            val tokens = bundle.tokensAssets.firstOrNull(assetCache::assetExists)
            val isValid = encoder != null && decoder != null && tokens != null
            if (isValid) {
                selectedEncoderAsset = encoder
                selectedDecoderAsset = decoder
                selectedTokensAsset = tokens
            }
            isValid
        }?.also { selectedBundle = it }
    }

    private fun createRecognizer(languageTag: String, currentHotwords: List<String> = emptyList()): OfflineRecognizer {
        val bundle = locateBundle()
            ?: error("Whisper assets not found in app/src/main/assets/$MODEL_DIR")
        val encoderAsset = selectedEncoderAsset ?: error("Missing whisper encoder asset")
        val decoderAsset = selectedDecoderAsset ?: error("Missing whisper decoder asset")
        val tokensAsset = selectedTokensAsset ?: error("Missing whisper tokens asset")

        val whisperConfig = OfflineWhisperModelConfig.builder()
            .setEncoder(assetCache.ensureCopied(encoderAsset))
            .setDecoder(assetCache.ensureCopied(decoderAsset))
            .setLanguage(languageTag.ifBlank { bundle.language })
            .setTask("transcribe")
            .build()

        val modelConfig = OfflineModelConfig.builder()
            .setWhisper(whisperConfig)
            .setTokens(assetCache.ensureCopied(tokensAsset))
            .setModelType("whisper")
            .setNumThreads(2)
            .setDebug(false)
            .build()

        val configBuilder = OfflineRecognizerConfig.builder()
            .setOfflineModelConfig(modelConfig)
            .setDecodingMethod("greedy_search")

        if (currentHotwords.isNotEmpty()) {
            try {
                val hotwordsFile = writeHotwordsFile(currentHotwords)
                configBuilder.setHotwordsFile(hotwordsFile)
                configBuilder.setHotwordsScore(10.0f)
                Log.i(TAG, "Whisper hotwords: ${currentHotwords.size} words injected")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write hotwords file, proceeding without bias", e)
            }
        }

        Log.i(TAG, "Initializing sherpa-onnx bundle ${bundle.label}")
        return OfflineRecognizer(configBuilder.build())
    }

    private fun writeHotwordsFile(words: List<String>): String {
        val file = File(appContext.cacheDir, "whisper_hotwords.txt")
        file.writeText(words.joinToString("\n"))
        return file.absolutePath
    }
}
