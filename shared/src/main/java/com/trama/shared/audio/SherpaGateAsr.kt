package com.trama.shared.audio

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OnlineZipformer2CtcModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Optional sherpa-onnx online ASR gate.
 *
 * It is deliberately asset-driven: when no small streaming Sherpa bundle is
 * present, this backend reports unavailable and callers can fall back to Vosk.
 */
class SherpaGateAsr(
    context: Context
) : LightweightGateAsr {

    companion object {
        private const val TAG = "SherpaGateAsr"
        private const val MODEL_DIR = "asr/sherpa-gate"
        private const val SAMPLE_RATE_HZ = 16_000

        private val CANDIDATE_BUNDLES = listOf(
            GateBundle.Transducer(
                encoderAsset = "$MODEL_DIR/bookbot-phoneme-es/encoder.int8.onnx",
                decoderAsset = "$MODEL_DIR/bookbot-phoneme-es/decoder.int8.onnx",
                joinerAsset = "$MODEL_DIR/bookbot-phoneme-es/joiner.int8.onnx",
                modelType = "zipformer",
                tokensAsset = "$MODEL_DIR/bookbot-phoneme-es/tokens.txt",
                label = "bookbot-phoneme-es"
            ),
            GateBundle.Zipformer2Ctc(
                modelAsset = "$MODEL_DIR/zipformer2-ctc/model.onnx",
                tokensAsset = "$MODEL_DIR/zipformer2-ctc/tokens.txt",
                label = "zipformer2-ctc"
            ),
            GateBundle.Transducer(
                encoderAsset = "$MODEL_DIR/transducer/encoder.onnx",
                decoderAsset = "$MODEL_DIR/transducer/decoder.onnx",
                joinerAsset = "$MODEL_DIR/transducer/joiner.onnx",
                modelType = "zipformer",
                tokensAsset = "$MODEL_DIR/transducer/tokens.txt",
                label = "transducer"
            )
        )
    }

    private sealed class GateBundle(open val tokensAsset: String, open val label: String) {
        data class Zipformer2Ctc(
            val modelAsset: String,
            override val tokensAsset: String,
            override val label: String
        ) : GateBundle(tokensAsset, label)

        data class Transducer(
            val encoderAsset: String,
            val decoderAsset: String,
            val joinerAsset: String,
            val modelType: String,
            override val tokensAsset: String,
            override val label: String
        ) : GateBundle(tokensAsset, label)
    }

    private val appContext = context.applicationContext
    private val assetCache = AssetFileCache(appContext)
    private val recognizerMutex = Mutex()
    private var recognizer: OnlineRecognizer? = null
    private var selectedBundle: GateBundle? = null

    override val name: String
        get() = "sherpa-gate:${locateBundle()?.label ?: "unavailable"}"

    override val isAvailable: Boolean
        get() = locateBundle() != null

    fun canInitialize(): Boolean {
        if (!isAvailable) return false
        return runCatching {
            if (recognizer == null) {
                recognizer = createRecognizer()
            }
            true
        }.getOrElse { error ->
            Log.w(TAG, "Sherpa gate unavailable at runtime", error)
            recognizer?.release()
            recognizer = null
            false
        }
    }

    override suspend fun transcribe(window: CapturedAudioWindow, languageTag: String): String? {
        val pcm = window.mergedPcm()
        if (pcm.isEmpty() || !isAvailable) return null

        return withContext(Dispatchers.IO) {
            recognizerMutex.withLock {
                val recognizer = recognizer ?: runCatching {
                    createRecognizer().also { recognizer = it }
                }.getOrElse { error ->
                    Log.w(TAG, "Sherpa gate recognizer init failed", error)
                    return@withLock null
                }
                val samples = FloatArray(pcm.size) { index -> pcm[index] / 32768.0f }
                val stream = recognizer.createStream()
                try {
                    stream.acceptWaveform(samples, window.sampleRateHz)
                    stream.inputFinished()
                    while (recognizer.isReady(stream)) {
                        recognizer.decode(stream)
                    }
                    recognizer.getResult(stream).text?.trim()?.takeIf { it.isNotBlank() }
                } catch (e: Exception) {
                    Log.w(TAG, "Sherpa gate transcription failed", e)
                    null
                } finally {
                    stream.release()
                }
            }
        }
    }

    private fun locateBundle(): GateBundle? {
        selectedBundle?.let { return it }
        return CANDIDATE_BUNDLES.firstOrNull(::bundleExists)
            ?.also { selectedBundle = it }
    }

    private fun bundleExists(bundle: GateBundle): Boolean {
        val requiredAssets = when (bundle) {
            is GateBundle.Zipformer2Ctc -> listOf(bundle.modelAsset, bundle.tokensAsset)
            is GateBundle.Transducer -> listOf(
                bundle.encoderAsset,
                bundle.decoderAsset,
                bundle.joinerAsset,
                bundle.tokensAsset
            )
        }
        return requiredAssets.all { assetCache.assetSize(it) > 0L }
    }

    private fun createRecognizer(): OnlineRecognizer {
        val bundle = locateBundle()
            ?: error("Sherpa gate assets not found in app/src/main/assets/$MODEL_DIR")

        val modelConfigBuilder = OnlineModelConfig.builder()
            .setTokens(assetCache.ensureCopied(bundle.tokensAsset))
            .setNumThreads(1)
            .setProvider("cpu")
            .setDebug(false)

        when (bundle) {
            is GateBundle.Zipformer2Ctc -> {
                modelConfigBuilder
                    .setZipformer2Ctc(
                        OnlineZipformer2CtcModelConfig.builder()
                            .setModel(assetCache.ensureCopied(bundle.modelAsset))
                            .build()
                    )
                    .setModelType("zipformer2_ctc")
            }
            is GateBundle.Transducer -> {
                modelConfigBuilder
                    .setTransducer(
                        OnlineTransducerModelConfig.builder()
                            .setEncoder(assetCache.ensureCopied(bundle.encoderAsset))
                            .setDecoder(assetCache.ensureCopied(bundle.decoderAsset))
                            .setJoiner(assetCache.ensureCopied(bundle.joinerAsset))
                            .build()
                    )
                    .setModelType(bundle.modelType)
            }
        }

        val config = OnlineRecognizerConfig.builder()
            .setFeatureConfig(FeatureConfig.builder().setSampleRate(SAMPLE_RATE_HZ).build())
            .setOnlineModelConfig(modelConfigBuilder.build())
            .setDecodingMethod("greedy_search")
            .setEnableEndpoint(false)
            .build()

        Log.i(TAG, "Initializing sherpa-onnx gate ${bundle.label}")
        return OnlineRecognizer(config)
    }
}
