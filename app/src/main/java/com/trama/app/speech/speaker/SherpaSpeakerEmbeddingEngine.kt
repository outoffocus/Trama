package com.trama.app.speech.speaker

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.trama.shared.audio.AssetFileCache
import com.trama.shared.audio.CapturedAudioWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SherpaSpeakerEmbeddingEngine(
    context: Context
) : SpeakerEmbeddingEngine {
    companion object {
        private const val TAG = "SherpaSpeakerEmbed"
        private const val MODEL_ASSET = "asr/speaker/model.onnx"
    }

    private val appContext = context.applicationContext
    private val assetCache = AssetFileCache(appContext)
    private val extractorMutex = Mutex()
    private var extractor: SpeakerEmbeddingExtractor? = null

    override val name: String = "sherpa-speaker-embedding"

    override val isAvailable: Boolean
        get() = assetCache.assetExists(MODEL_ASSET)

    override suspend fun embed(window: CapturedAudioWindow): SpeakerEmbedding? {
        val pcm = window.mergedPcm()
        if (pcm.isEmpty() || !isAvailable) return null

        return withContext(Dispatchers.IO) {
            extractorMutex.withLock {
                val extractor = extractor ?: createExtractor().also { extractor = it }
                val stream = extractor.createStream()
                try {
                    val samples = FloatArray(pcm.size) { index -> pcm[index] / 32768.0f }
                    stream.acceptWaveform(samples, window.sampleRateHz)
                    stream.inputFinished()
                    if (!extractor.isReady(stream)) {
                        Log.w(TAG, "Speaker extractor not ready for ${window.durationMs()}ms window")
                        return@withLock null
                    }
                    val embedding = extractor.compute(stream)
                    if (embedding.isEmpty()) null else SpeakerEmbedding(
                        vector = embedding,
                        sampleRateHz = window.sampleRateHz,
                        durationMs = window.durationMs()
                    )
                } finally {
                    stream.release()
                }
            }
        }
    }

    private fun createExtractor(): SpeakerEmbeddingExtractor {
        require(isAvailable) { "Speaker embedding model missing at $MODEL_ASSET" }
        val modelPath = assetCache.ensureCopied(MODEL_ASSET)
        Log.i(TAG, "Initializing speaker embedding extractor")
        val config = SpeakerEmbeddingExtractorConfig.builder()
            .setModel(modelPath)
            .setNumThreads(2)
            .setDebug(false)
            .setProvider("cpu")
            .build()
        return SpeakerEmbeddingExtractor(config)
    }
}
