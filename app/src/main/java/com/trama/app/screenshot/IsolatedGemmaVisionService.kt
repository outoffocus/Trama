package com.trama.app.screenshot

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.trama.app.summary.GemmaClient
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IsolatedGemmaVisionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val imagePath = intent?.getStringExtra(EXTRA_IMAGE_PATH)
        val outputPath = intent?.getStringExtra(EXTRA_OUTPUT_PATH)
        val prompt = intent?.getStringExtra(EXTRA_PROMPT)
        val systemInstruction = intent?.getStringExtra(EXTRA_SYSTEM_INSTRUCTION)

        if (imagePath.isNullOrBlank() || outputPath.isNullOrBlank() || prompt.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        scope.launch {
            val outputFile = File(outputPath)
            try {
                val result = GemmaClient.generateMultimodalFromFiles(
                    context = applicationContext,
                    prompt = prompt,
                    imageFiles = listOf(File(imagePath)),
                    maxTokens = 768,
                    responsePrefix = "{",
                    systemInstruction = systemInstruction
                )
                if (result.isNullOrBlank()) {
                    outputFile.writeText("ERROR:empty_local_multimodal_response")
                } else {
                    outputFile.writeText(result)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Isolated Gemma vision failed", t)
                outputFile.writeText("ERROR:${t.javaClass.simpleName}:${t.message.orEmpty()}")
            } finally {
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    companion object {
        private const val TAG = "IsolatedGemmaVision"
        const val EXTRA_IMAGE_PATH = "image_path"
        const val EXTRA_OUTPUT_PATH = "output_path"
        const val EXTRA_PROMPT = "prompt"
        const val EXTRA_SYSTEM_INSTRUCTION = "system_instruction"
    }
}
