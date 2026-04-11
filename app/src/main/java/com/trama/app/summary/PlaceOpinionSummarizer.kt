package com.trama.app.summary

import android.content.Context
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.trama.app.GeminiConfig

class PlaceOpinionSummarizer(private val context: Context) {

    suspend fun summarize(
        placeName: String,
        rating: Int?,
        opinionText: String
    ): String? {
        val trimmedOpinion = opinionText.trim()
        if (trimmedOpinion.isBlank()) return null

        val prompt = PromptTemplateStore.render(
            context,
            PromptTemplateStore.PLACE_OPINION_SUMMARY,
            mapOf(
                "placeName" to placeName,
                "ratingText" to (rating?.let { "$it/5 estrellas" } ?: "Sin valoración"),
                "opinionText" to trimmedOpinion
            )
        )

        val apiKey = getApiKey()
        if (!apiKey.isNullOrBlank()) {
            runCatching {
                val model = GenerativeModel(
                    modelName = GeminiConfig.MODEL_NAME,
                    apiKey = apiKey,
                    generationConfig = generationConfig {
                        temperature = 0.2f
                        maxOutputTokens = 256
                    }
                )
                model.generateContent(prompt).text?.trim()
            }.getOrNull()
                ?.cleanSummary()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }

        if (GemmaClient.isModelAvailable(context)) {
            GemmaClient.generate(context, prompt, maxTokens = 256)
                ?.cleanSummary()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }

        return null
    }

    private fun getApiKey(): String? =
        context.getSharedPreferences("daily_summary", Context.MODE_PRIVATE)
            .getString("gemini_api_key", null)

    private fun String.cleanSummary(): String =
        trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
}
