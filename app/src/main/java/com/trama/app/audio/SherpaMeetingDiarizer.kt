package com.trama.app.audio

import android.content.Context
import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.trama.app.speech.speaker.SherpaSpeakerEmbeddingEngine
import com.trama.shared.audio.AssetFileCache
import com.trama.shared.audio.CapturedAudioWindow
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/** Offline, on-device speaker diarization for long meeting PCM files. */
class SherpaMeetingDiarizer(context: Context) {
    companion object {
        private const val SEGMENTATION_ASSET = "asr/diarization/segmentation.int8.onnx"
        private const val EMBEDDING_ASSET = "asr/speaker/model.onnx"
        private const val PROCESSING_WINDOW_MS = 5 * 60_000L
        private const val CLUSTER_THRESHOLD = 0.72f
        private const val STITCH_THRESHOLD = 0.60f
    }

    private val appContext = context.applicationContext
    private val assetCache = AssetFileCache(appContext)
    private val embeddingEngine = SherpaSpeakerEmbeddingEngine(appContext)

    val isAvailable: Boolean
        get() = assetCache.assetExists(SEGMENTATION_ASSET) &&
            assetCache.assetExists(EMBEDDING_ASSET) && embeddingEngine.isAvailable

    suspend fun diarize(
        file: File,
        sampleRateHz: Int,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<DiarizationSpan> = withContext(Dispatchers.IO) {
        if (!isAvailable || sampleRateHz != 16_000) return@withContext emptyList()
        val totalChunks = PcmRecordingTranscriber.expectedChunkCountForDuration(
            file,
            sampleRateHz,
            PROCESSING_WINDOW_MS
        )

        val diarizer = createDiarizer()
        val stitcher = SpeakerIdentityStitcher(STITCH_THRESHOLD)
        val result = mutableListOf<DiarizationSpan>()
        try {
            PcmRecordingStorage.readWindows(file, PROCESSING_WINDOW_MS, sampleRateHz)
                .forEachIndexed { chunkIndex, window ->
                onProgress(chunkIndex + 1, totalChunks)
                val pcm = window.livePcm
                val samples = FloatArray(pcm.size) { pcm[it] / 32768.0f }
                val localSegments = diarizer.process(samples).toList()
                val localToGlobal = mutableMapOf<Int, Int>()
                val usedGlobal = mutableSetOf<Int>()
                localSegments.groupBy { it.speaker }.forEach { (localSpeaker, speakerSegments) ->
                    val representative = speakerSegments
                        .filter { it.end - it.start >= 0.8f }
                        .maxByOrNull { it.end - it.start }
                    val embedding = representative?.let { segment ->
                        val start = (segment.start * sampleRateHz).toInt().coerceIn(0, pcm.size)
                        val end = (segment.end * sampleRateHz).toInt().coerceIn(start, pcm.size)
                        val maxSamples = sampleRateHz * 8
                        val clippedEnd = minOf(end, start + maxSamples)
                        if (clippedEnd - start < sampleRateHz / 2) null else embeddingEngine.embed(
                            CapturedAudioWindow(shortArrayOf(), pcm.copyOfRange(start, clippedEnd), sampleRateHz)
                        )?.vector
                    }
                    val global = stitcher.assign(embedding, usedGlobal)
                    localToGlobal[localSpeaker] = global
                    usedGlobal += global
                }

                val offsetMs = chunkIndex * PROCESSING_WINDOW_MS
                localSegments.forEach { segment ->
                    val startMs = offsetMs + (segment.start * 1_000).toLong()
                    val endMs = offsetMs + (segment.end * 1_000).toLong()
                    if (endMs > startMs) {
                        result += DiarizationSpan(
                            startMs = startMs,
                            endMs = endMs,
                            speaker = localToGlobal[segment.speaker] ?: 0
                        )
                    }
                }
            }
        } finally {
            diarizer.release()
            embeddingEngine.close()
        }
        mergeAdjacent(result)
    }

    private fun createDiarizer(): OfflineSpeakerDiarization {
        val segmentation = OfflineSpeakerSegmentationModelConfig.builder()
            .setPyannote(
                OfflineSpeakerSegmentationPyannoteModelConfig.builder()
                    .setModel(assetCache.ensureCopied(SEGMENTATION_ASSET))
                    .build()
            )
            .setNumThreads(2)
            .setDebug(false)
            .setProvider("cpu")
            .build()
        val embedding = SpeakerEmbeddingExtractorConfig.builder()
            .setModel(assetCache.ensureCopied(EMBEDDING_ASSET))
            .setNumThreads(2)
            .setDebug(false)
            .setProvider("cpu")
            .build()
        val clustering = FastClusteringConfig.builder()
            .setNumClusters(-1)
            .setThreshold(CLUSTER_THRESHOLD)
            .build()
        return OfflineSpeakerDiarization(
            OfflineSpeakerDiarizationConfig.builder()
                .setSegmentation(segmentation)
                .setEmbedding(embedding)
                .setClustering(clustering)
                .setMinDurationOn(0.3f)
                .setMinDurationOff(0.5f)
                .build()
        )
    }

    private fun mergeAdjacent(spans: List<DiarizationSpan>): List<DiarizationSpan> =
        spans.sortedBy { it.startMs }.fold(mutableListOf()) { merged, span ->
            val previous = merged.lastOrNull()
            if (previous != null && previous.speaker == span.speaker && span.startMs - previous.endMs <= 500L) {
                merged[merged.lastIndex] = previous.copy(endMs = maxOf(previous.endMs, span.endMs))
            } else {
                merged += span
            }
            merged
        }

    internal class SpeakerIdentityStitcher(private val threshold: Float) {
        private data class Cluster(var centroid: FloatArray, var count: Int)
        private val clusters = mutableListOf<Cluster>()

        fun assign(embedding: FloatArray?, excluded: Set<Int> = emptySet()): Int {
            if (embedding == null || embedding.isEmpty()) return newCluster(embedding ?: FloatArray(0))
            val best = clusters.indices
                .filterNot(excluded::contains)
                .map { it to cosine(clusters[it].centroid, embedding) }
                .maxByOrNull { it.second }
            if (best == null || best.second < threshold) return newCluster(embedding)
            update(best.first, embedding)
            return best.first
        }

        private fun newCluster(embedding: FloatArray): Int {
            clusters += Cluster(embedding.copyOf(), 1)
            return clusters.lastIndex
        }

        private fun update(index: Int, embedding: FloatArray) {
            val cluster = clusters[index]
            if (cluster.centroid.size != embedding.size) return
            val count = cluster.count.toFloat()
            cluster.centroid.indices.forEach { i ->
                cluster.centroid[i] = (cluster.centroid[i] * count + embedding[i]) / (count + 1f)
            }
            cluster.count++
        }

        private fun cosine(a: FloatArray, b: FloatArray): Float {
            if (a.isEmpty() || a.size != b.size) return -1f
            var dot = 0.0
            var normA = 0.0
            var normB = 0.0
            a.indices.forEach { i ->
                dot += a[i] * b[i]
                normA += a[i] * a[i]
                normB += b[i] * b[i]
            }
            if (normA == 0.0 || normB == 0.0) return -1f
            return (dot / (sqrt(normA) * sqrt(normB))).toFloat()
        }
    }
}
