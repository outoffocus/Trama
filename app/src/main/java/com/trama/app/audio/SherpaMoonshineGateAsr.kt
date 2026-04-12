package com.trama.app.audio

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.trama.shared.audio.AssetFileCache
import com.trama.shared.audio.CapturedAudioWindow
import com.trama.shared.audio.LightweightGateAsr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Lightweight semantic gate using Moonshine.
 *
 * It runs on short VAD windows and is meant to decide whether a full Whisper pass
 * is worth it. It is not the final transcription backend.
 */
class SherpaMoonshineGateAsr(
    context: Context
) : LightweightGateAsr {
    companion object {
        private const val TAG = "SherpaMoonshineGate"
        private const val MODEL_DIR = "asr/moonshine"
        private val CANDIDATE_BUNDLES = listOf(
            MoonshineBundle(
                preprocessorAssets = emptyList(),
                encoderAssets = listOf(
                    "$MODEL_DIR/encoder_model.ort",
                    "$MODEL_DIR/encoder_model.onnx",
                    "$MODEL_DIR/encoder_model.int8.onnx"
                ),
                uncachedDecoderAssets = emptyList(),
                cachedDecoderAssets = emptyList(),
                mergedDecoderAssets = listOf(
                    "$MODEL_DIR/decoder_model_merged.ort",
                    "$MODEL_DIR/decoder_model_merged.onnx",
                    "$MODEL_DIR/decoder_model_merged.int8.onnx"
                ),
                tokensAssets = listOf(
                    "$MODEL_DIR/tokens.txt"
                ),
                label = "moonshine-base-es-v2"
            ),
            MoonshineBundle(
                preprocessorAssets = listOf(
                    "$MODEL_DIR/preprocessor.onnx",
                    "$MODEL_DIR/preprocess.onnx"
                ),
                encoderAssets = listOf(
                    "$MODEL_DIR/encoder.int8.onnx",
                    "$MODEL_DIR/encoder.onnx"
                ),
                uncachedDecoderAssets = listOf(
                    "$MODEL_DIR/uncached_decoder.int8.onnx",
                    "$MODEL_DIR/uncached_decoder.onnx"
                ),
                cachedDecoderAssets = listOf(
                    "$MODEL_DIR/cached_decoder.int8.onnx",
                    "$MODEL_DIR/cached_decoder.onnx"
                ),
                mergedDecoderAssets = listOf(
                    "$MODEL_DIR/merged_decoder.int8.onnx",
                    "$MODEL_DIR/merged_decoder.onnx"
                ),
                tokensAssets = listOf(
                    "$MODEL_DIR/tokens.txt"
                ),
                label = "moonshine-base-es"
            )
        )
    }

    private data class MoonshineBundle(
        val preprocessorAssets: List<String>,
        val encoderAssets: List<String>,
        val uncachedDecoderAssets: List<String>,
        val cachedDecoderAssets: List<String>,
        val mergedDecoderAssets: List<String>,
        val tokensAssets: List<String>,
        val label: String
    )

    override val name: String
        get() = "sherpa-moonshine:${locateBundle()?.label ?: "unavailable"}"

    private val appContext = context.applicationContext
    private val assetCache = AssetFileCache(appContext)
    private val recognizerMutex = Mutex()
    private var recognizer: OfflineRecognizer? = null
    private var selectedBundle: MoonshineBundle? = null
    private var selectedPreprocessorAsset: String? = null
    private var selectedEncoderAsset: String? = null
    private var selectedUncachedDecoderAsset: String? = null
    private var selectedCachedDecoderAsset: String? = null
    private var selectedMergedDecoderAsset: String? = null
    private var selectedTokensAsset: String? = null

    override val isAvailable: Boolean
        get() = locateBundle() != null

    override suspend fun transcribe(window: CapturedAudioWindow, languageTag: String): String? {
        val pcm = window.mergedPcm()
        if (pcm.isEmpty()) return null

        return withContext(Dispatchers.IO) {
            recognizerMutex.withLock {
                val recognizer = recognizer ?: createRecognizer().also { recognizer = it }

                val samples = FloatArray(pcm.size) { index ->
                    pcm[index] / 32768.0f
                }

                val stream = recognizer.createStream()
                try {
                    stream.acceptWaveform(samples, window.sampleRateHz)
                    recognizer.decode(stream)
                    val result = recognizer.getResult(stream).text?.trim().orEmpty()
                    if (result.isBlank()) null else result
                } finally {
                    stream.release()
                }
            }
        }
    }

    private fun locateBundle(): MoonshineBundle? {
        return selectedBundle ?: CANDIDATE_BUNDLES.firstOrNull { bundle ->
            val preprocessor = bundle.preprocessorAssets.firstOrNull(assetCache::assetExists)
            val encoder = bundle.encoderAssets.firstOrNull(assetCache::assetExists)
            val uncachedDecoder = bundle.uncachedDecoderAssets.firstOrNull(assetCache::assetExists)
            val cachedDecoder = bundle.cachedDecoderAssets.firstOrNull(assetCache::assetExists)
            val mergedDecoder = bundle.mergedDecoderAssets.firstOrNull(assetCache::assetExists)
            val tokens = bundle.tokensAssets.firstOrNull(assetCache::assetExists)
            val usesMergedOnly = bundle.preprocessorAssets.isEmpty() &&
                bundle.uncachedDecoderAssets.isEmpty() &&
                bundle.cachedDecoderAssets.isEmpty()
            val isValid = encoder != null &&
                ((usesMergedOnly && mergedDecoder != null) || (
                    preprocessor != null &&
                        uncachedDecoder != null &&
                        cachedDecoder != null &&
                        mergedDecoder != null
                    )) &&
                tokens != null

            if (isValid) {
                selectedPreprocessorAsset = preprocessor
                selectedEncoderAsset = encoder
                selectedUncachedDecoderAsset = uncachedDecoder
                selectedCachedDecoderAsset = cachedDecoder
                selectedMergedDecoderAsset = mergedDecoder
                selectedTokensAsset = tokens
            }
            isValid
        }?.also { selectedBundle = it }
    }

    private fun createRecognizer(): OfflineRecognizer {
        val bundle = locateBundle()
            ?: error("Moonshine assets not found in app/src/main/assets/$MODEL_DIR")
        val moonshineBuilder = OfflineMoonshineModelConfig.builder()
            .setEncoder(assetCache.ensureCopied(selectedEncoderAsset ?: error("Missing moonshine encoder")))
            .setMergedDecoder(assetCache.ensureCopied(selectedMergedDecoderAsset ?: error("Missing moonshine merged decoder")))

        selectedPreprocessorAsset?.let {
            moonshineBuilder.setPreprocessor(assetCache.ensureCopied(it))
        }
        selectedUncachedDecoderAsset?.let {
            moonshineBuilder.setUncachedDecoder(assetCache.ensureCopied(it))
        }
        selectedCachedDecoderAsset?.let {
            moonshineBuilder.setCachedDecoder(assetCache.ensureCopied(it))
        }

        val moonshineConfig = moonshineBuilder.build()

        val modelConfig = OfflineModelConfig.builder()
            .setMoonshine(moonshineConfig)
            .setTokens(assetCache.ensureCopied(selectedTokensAsset ?: error("Missing moonshine tokens")))
            .setModelType("moonshine")
            .setNumThreads(1)
            .setDebug(false)
            .build()

        Log.i(TAG, "Initializing sherpa Moonshine gate ${bundle.label}")
        return OfflineRecognizer(
            OfflineRecognizerConfig.builder()
                .setFeatureConfig(
                    FeatureConfig.builder()
                        .setSampleRate(16_000)
                        .setFeatureDim(80)
                        .setDither(0.0f)
                        .build()
                )
                .setOfflineModelConfig(modelConfig)
                .setDecodingMethod("greedy_search")
                .build()
        )
    }
}
