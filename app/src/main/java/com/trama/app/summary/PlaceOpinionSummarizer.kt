package com.trama.app.summary

import android.content.Context

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

        if (GemmaClient.isModelAvailable(context)) {
            GemmaClient.generate(context, prompt, maxTokens = 256)
                ?.cleanSummary()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }

        return null
    }

    private fun String.cleanSummary(): String =
        trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
}
