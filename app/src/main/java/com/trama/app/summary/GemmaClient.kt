package com.trama.app.summary

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
/**
 * Unified local model client. Supports two runtimes:
 *
 *  - **.task** files  → MediaPipe LLM Inference API
 *  - **.litertlm** files → LiteRT-LM API
 *
 * The runtime is chosen automatically from the file extension.
 * Thread-safe via Mutex. Model is loaded lazily and kept in memory.
 * Call release() to free RAM when under memory pressure.
 */
object GemmaClient {

    private const val TAG = "GemmaClient"
    const val DEFAULT_FILENAME = "gemma3-1b-it-int4.task"
    private const val GENERATION_TIMEOUT_MS = 30_000L
    // Vision inference needs more time: cold model load (~20s for E4B) + image encoding + inference.
    private const val MULTIMODAL_TIMEOUT_MS = 120_000L
    // Max side before PNG-encoding for multimodal. Gemma 3n supports 256/512/768 natively;
    // Google AI Edge Gallery caps at 1024 as pre-processing guard before the model rescales.
    private const val MULTIMODAL_MAX_SIDE_PX = 1024

    // ── Active runtime (only one at a time) ──
    private var mediaPipeInference: LlmInference? = null
    private var litertEngine: com.google.ai.edge.litertlm.Engine? = null
    private var loadedModelPath: String? = null
    private val mutex = Mutex()

    fun getModelFilename(context: Context): String =
        GemmaModelManager.getModelFilename(context)

    /** @deprecated Use GemmaModelManager.setModelUrl() instead — filename is auto-derived. */
    fun setModelFilename(context: Context, filename: String) { /* no-op, derived from URL */ }

    private const val PREFS = "daily_summary"
    private const val KEY_LOCAL_MODEL_ENABLED = "local_model_enabled"

    /** True if the model file exists on disk (regardless of enabled state). Used by UI/GemmaModelManager. */
    fun isModelDownloaded(context: Context): Boolean =
        getModelFile(context).exists()

    /** True if the model is downloaded AND enabled. Used by processors. */
    fun isModelAvailable(context: Context): Boolean =
        isLocalModelEnabled(context) && getModelFile(context).exists()

    fun isLocalModelEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOCAL_MODEL_ENABLED, true)

    fun setLocalModelEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_LOCAL_MODEL_ENABLED, enabled).apply()
        if (!enabled) release()
    }

    fun getModelFile(context: Context): File =
        File(context.filesDir, getModelFilename(context))

    fun isLiteRtModel(context: Context): Boolean =
        getModelFile(context).extension.equals("litertlm", ignoreCase = true)

    // ── Loading ──

    private fun ensureLoaded(context: Context) {
        val modelPath = getModelFile(context).absolutePath

        // If a different model was loaded, release the old one
        if (loadedModelPath != null && loadedModelPath != modelPath) {
            Log.i(TAG, "Model file changed, releasing old model")
            releaseInternal()
        }

        if (loadedModelPath != null) return // already loaded

        Log.i(TAG, "Loading model from $modelPath")

        if (modelPath.endsWith(".litertlm")) {
            loadLiteRtLm(context, modelPath)
        } else {
            loadMediaPipe(context, modelPath)
        }
    }

    private fun loadMediaPipe(context: Context, modelPath: String) {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(2048)
            .setPreferredBackend(LlmInference.Backend.CPU)
            .build()
        mediaPipeInference = LlmInference.createFromOptions(context, options)
        loadedModelPath = modelPath
        Log.i(TAG, "Model loaded via MediaPipe (.task, CPU backend)")
    }

    private fun loadLiteRtLm(context: Context, modelPath: String) {
        val config = com.google.ai.edge.litertlm.EngineConfig(
            modelPath = modelPath,
            backend = com.google.ai.edge.litertlm.Backend.CPU(),
            visionBackend = com.google.ai.edge.litertlm.Backend.GPU(),
            audioBackend = null,
            maxNumTokens = 4096, // keep very conservative; large values trigger SIGSEGV even at 8192
            cacheDir = context.cacheDir.absolutePath
        )
        val engine = com.google.ai.edge.litertlm.Engine(config)
        engine.initialize()
        litertEngine = engine
        loadedModelPath = modelPath
        Log.i(TAG, "Model loaded via LiteRT-LM (.litertlm, CPU backend)")
    }

    // ── Generation ──

    /**
     * Generate text with the local model. Returns null if model not downloaded or fails.
     *
     * @param responsePrefix    Optional prefix to force the model's output format (e.g. "{" for JSON).
     *                          The prefix is prepended to the returned response.
     * @param systemInstruction Optional system instruction injected before the user turn.
     *                          When null, a generic instruction is used.
     */
    suspend fun generate(
        context: Context,
        prompt: String,
        maxTokens: Int = 1024,
        responsePrefix: String? = null,
        systemInstruction: String? = null
    ): String? {
        if (!isModelDownloaded(context)) return null

        return mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val response = withTimeout(GENERATION_TIMEOUT_MS) {
                        ensureLoaded(context)

                        if (litertEngine != null) {
                            generateLiteRtLm(prompt, responsePrefix, systemInstruction)
                        } else {
                            generateMediaPipe(prompt, responsePrefix, systemInstruction)
                        }
                    }

                    Log.d(TAG, "Generated ${response?.length ?: 0} chars")
                    response?.trim()
                } catch (e: Exception) {
                    Log.w(TAG, "Generation failed: ${e.javaClass.simpleName}: ${e.message}")
                    releaseInternal()
                    null
                }
            }
        }
    }

    suspend fun generateMultimodal(
        context: Context,
        prompt: String,
        images: List<Bitmap>,
        maxTokens: Int = 1024,
        responsePrefix: String? = null,
        systemInstruction: String? = null
    ): String? {
        if (!isModelDownloaded(context) || images.isEmpty()) return null

        return mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val response = withTimeout(MULTIMODAL_TIMEOUT_MS) {
                        ensureLoaded(context)

                        if (litertEngine != null) {
                            generateLiteRtLmMultimodal(prompt, images, systemInstruction)
                        } else {
                            generateMediaPipeMultimodal(prompt, images, responsePrefix, systemInstruction)
                        }
                    }

                    Log.d(TAG, "Generated multimodal ${response?.length ?: 0} chars")
                    response?.trim()
                } catch (e: Throwable) {
                    Log.w(TAG, "Multimodal generation failed: ${e.javaClass.simpleName}: ${e.message}")
                    releaseInternal()
                    null
                }
            }
        }
    }

    suspend fun generateMultimodalFromFiles(
        context: Context,
        prompt: String,
        imageFiles: List<File>,
        maxTokens: Int = 1024,
        responsePrefix: String? = null,
        systemInstruction: String? = null
    ): String? {
        if (!isModelDownloaded(context) || imageFiles.isEmpty()) return null

        return mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val response = withTimeout(MULTIMODAL_TIMEOUT_MS) {
                        ensureLoaded(context)

                        if (litertEngine != null) {
                            generateLiteRtLmMultimodalFromFiles(prompt, imageFiles, systemInstruction)
                        } else {
                            null
                        }
                    }

                    Log.d(TAG, "Generated file multimodal ${response?.length ?: 0} chars")
                    response?.trim()
                } catch (e: Throwable) {
                    Log.w(TAG, "File multimodal generation failed: ${e.javaClass.simpleName}: ${e.message}")
                    releaseInternal()
                    null
                }
            }
        }
    }

    private fun generateMediaPipe(
        prompt: String,
        responsePrefix: String?,
        systemInstruction: String?
    ): String? {
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTemperature(0.3f)
            .setTopK(20)
            .build()
        val session = LlmInferenceSession.createFromOptions(mediaPipeInference!!, sessionOptions)
        val formattedPrompt = formatMediaPipePrompt(prompt, responsePrefix, systemInstruction)
        session.addQueryChunk(formattedPrompt)
        val response = session.generateResponse()
        session.close()

        return if (responsePrefix != null) {
            responsePrefix + (response ?: "")
        } else {
            response
        }
    }

    private fun generateMediaPipeMultimodal(
        prompt: String,
        images: List<Bitmap>,
        responsePrefix: String?,
        systemInstruction: String?
    ): String? {
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTemperature(0.2f)
            .setTopK(10)
            .setGraphOptions(GraphOptions.builder().setEnableVisionModality(true).build())
            .build()
        val session = LlmInferenceSession.createFromOptions(mediaPipeInference!!, sessionOptions)
        val formattedPrompt = formatMediaPipePrompt(prompt, responsePrefix, systemInstruction)
        session.addQueryChunk(formattedPrompt)
        images.forEach { bitmap ->
            BitmapImageBuilder(bitmap).build().use { mpImage ->
                session.addImage(mpImage)
            }
        }
        val response = session.generateResponse()
        session.close()

        return if (responsePrefix != null) {
            responsePrefix + (response ?: "")
        } else {
            response
        }
    }

    private fun generateLiteRtLm(
        prompt: String,
        responsePrefix: String?,
        systemInstruction: String?
    ): String? {
        val engine = litertEngine!!
        val samplerConfig = com.google.ai.edge.litertlm.SamplerConfig(
            topK = 10,
            topP = 0.9,
            temperature = 0.1,
            seed = 0
        )

        // IMPORTANT: ConversationConfig.systemInstruction MUST stay short.
        // Passing a large blob (e.g. diary context) here causes a null-deref SIGSEGV
        // inside nativeSendMessage (LiteRT-LM bug: engine enters bad state after
        // an oversized system instruction). Keep it to a single terse sentence.
        val shortSystemText = if (responsePrefix != null) {
            "Responde solo con JSON válido. Sin markdown ni explicaciones."
        } else {
            "Eres un asistente personal conciso. Responde siempre en español."
        }

        val conversationConfig = com.google.ai.edge.litertlm.ConversationConfig(
            systemInstruction = com.google.ai.edge.litertlm.Contents.of(shortSystemText),
            samplerConfig = samplerConfig
        )
        val conversation = engine.createConversation(conversationConfig)

        // If a large systemInstruction was provided (e.g. diary context), embed it at the
        // top of the user message instead — this avoids the SIGSEGV while keeping the context.
        val fullPrompt = if (!systemInstruction.isNullOrBlank()) {
            "[CONTEXTO]\n$systemInstruction\n[FIN_CONTEXTO]\n\n$prompt"
        } else {
            prompt
        }

        val message = conversation.sendMessage(fullPrompt)
        conversation.close()

        // Don't prepend responsePrefix — LiteRT-LM manages the conversation
        // and the model may still wrap in code fences. Caller uses JsonRepair.extractAndRepair.
        return message.toString()
    }

    private fun generateLiteRtLmMultimodal(
        prompt: String,
        images: List<Bitmap>,
        systemInstruction: String?
    ): String? = generateLiteRtLmMultimodalContents(prompt, images, systemInstruction)

    private fun generateLiteRtLmMultimodalFromFiles(
        prompt: String,
        imageFiles: List<File>,
        systemInstruction: String?
    ): String? {
        val bitmaps = imageFiles.mapNotNull { decodeScaled(it.absolutePath, MULTIMODAL_MAX_SIDE_PX) }
        if (bitmaps.isEmpty()) return null
        return try {
            generateLiteRtLmMultimodalContents(prompt, bitmaps, systemInstruction)
        } finally {
            bitmaps.forEach { it.recycle() }
        }
    }

    // Decode image file downsampled to at most maxSide px on the longest side.
    // Gemma 3n natively handles 256/512/768 — the Gallery caps input at 1024 before the model rescales.
    private fun decodeScaled(path: String, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxSide * 2 || bounds.outHeight / sampleSize > maxSide * 2) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeFile(path, opts) ?: return null

        val longest = maxOf(decoded.width, decoded.height)
        if (longest <= maxSide) return decoded

        val scale = maxSide.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true
        )
        decoded.recycle()
        return scaled
    }

    private fun generateLiteRtLmMultimodalContents(
        prompt: String,
        images: List<Bitmap>,
        systemInstruction: String?
    ): String? {
        val engine = litertEngine!!
        val samplerConfig = com.google.ai.edge.litertlm.SamplerConfig(
            topK = 10,
            topP = 0.9,
            temperature = 0.1,
            seed = 0
        )
        val conversationConfig = com.google.ai.edge.litertlm.ConversationConfig(
            systemInstruction = com.google.ai.edge.litertlm.Contents.of(
                "Responde solo con JSON valido. Sin markdown ni explicaciones."
            ),
            samplerConfig = samplerConfig
        )
        val conversation = engine.createConversation(conversationConfig)
        val fullPrompt = if (!systemInstruction.isNullOrBlank()) {
            "[CONTEXTO]\n$systemInstruction\n[FIN_CONTEXTO]\n\n$prompt"
        } else {
            prompt
        }
        val parts = mutableListOf<com.google.ai.edge.litertlm.Content>()
        images.forEach { image ->
            parts += com.google.ai.edge.litertlm.Content.ImageBytes(image.toPngByteArray())
        }
        // AI Edge Gallery appends text after image/audio for accurate last-token handling.
        parts += com.google.ai.edge.litertlm.Content.Text(fullPrompt)

        val message = conversation.sendMessage(com.google.ai.edge.litertlm.Contents.of(parts))
        conversation.close()
        return message.toString()
    }

    private fun Bitmap.toPngByteArray(): ByteArray =
        ByteArrayOutputStream().use { stream ->
            compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }

    private fun formatMediaPipePrompt(
        prompt: String,
        responsePrefix: String?,
        systemInstruction: String?
    ): String {
        val prefix = responsePrefix ?: ""
        // Gemma 4 supports a dedicated system turn — use it when a system instruction is provided
        val systemTurn = if (!systemInstruction.isNullOrBlank()) {
            "<start_of_turn>system\n$systemInstruction<end_of_turn>\n"
        } else ""
        return "${systemTurn}<start_of_turn>user\n$prompt<end_of_turn>\n<start_of_turn>model\n$prefix"
    }

    // ── Lifecycle ──

    /**
     * Release model from memory. Called on memory pressure or model deletion.
     */
    fun release() {
        releaseInternal()
        Log.i(TAG, "Model released")
    }

    private fun releaseInternal() {
        try { mediaPipeInference?.close() } catch (_: Exception) { }
        try { litertEngine?.close() } catch (_: Exception) { }
        mediaPipeInference = null
        litertEngine = null
        loadedModelPath = null
    }
}
