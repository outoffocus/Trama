package com.trama.app.speech

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.trama.app.GeminiConfig
import com.google.ai.client.generativeai.type.generationConfig
import com.trama.app.summary.PromptTemplateStore
import com.trama.shared.speech.EntryValidatorHeuristics
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Validates and optionally corrects transcriptions before saving.
 *
 * Two-stage pipeline:
 * 1. Fast heuristic filter (no cost, instant) — rejects obvious noise
 * 2. Gemini Nano / Flash for ambiguous cases — validates + corrects grammar
 *
 * Rules:
 * - es_nota_personal = true if it sounds like something said by someone
 *   for themselves or for a "we" (nosotros)
 * - es_nota_personal = false if it sounds like radio, TV, someone else's
 *   conversation, or a meaningless fragment
 */
class EntryValidator(private val context: Context) {

    companion object {
        private const val TAG = "EntryValidator"
        private val json = Json { ignoreUnknownKeys = true }
    }

    /**
     * Result of validation.
     */
    data class ValidationResult(
        val isValid: Boolean,
        val correctedText: String?,
        val confidence: Float,
        val reason: String
    )

    /**
     * Validate a transcription. Returns immediately for clear cases,
     * falls through to LLM for ambiguous ones.
     */
    suspend fun validate(text: String): ValidationResult {
        // Stage 1: Fast heuristics
        val heuristicResult = heuristicCheck(text)
        if (heuristicResult != null) {
            return heuristicResult
        }

        // Stage 2: LLM validation for ambiguous cases
        return try {
            llmValidate(text)
        } catch (e: Exception) {
            Log.w(TAG, "LLM validation failed, accepting entry", e)
            // If LLM fails, accept the entry (better to have false positives than miss notes)
            ValidationResult(
                isValid = true,
                correctedText = null,
                confidence = 0.5f,
                reason = "LLM no disponible"
            )
        }
    }

    /**
     * Fast heuristic filter using shared logic. Returns null for ambiguous cases (needs LLM).
     */
    private fun heuristicCheck(text: String): ValidationResult? {
        val result = EntryValidatorHeuristics.check(text) ?: return null
        return ValidationResult(
            isValid = result.isValid,
            correctedText = null,
            confidence = result.confidence,
            reason = result.reason
        )
    }

    /**
     * LLM validation using Gemini (Flash API or Nano on-device).
     */
    private suspend fun llmValidate(text: String): ValidationResult {
        val prompt = buildValidationPrompt(text)

        // Try Gemini Cloud first
        val apiKey = getApiKey()
        if (!apiKey.isNullOrBlank()) {
            try {
                return callCloudGemini(prompt, apiKey)
            } catch (e: Exception) {
                Log.w(TAG, "Cloud failed, trying local model", e)
            }
        }

        // Try local on-device model
        try {
            val localResult = callLocalModel(prompt)
            if (localResult != null) return localResult
        } catch (e: Exception) {
            Log.w(TAG, "Local model failed", e)
        }

        // No LLM available — accept ambiguous entries
        return ValidationResult(
            isValid = true,
            correctedText = null,
            confidence = 0.5f,
            reason = "Sin LLM disponible, aceptado por defecto"
        )
    }

    private fun buildValidationPrompt(text: String): String =
        PromptTemplateStore.render(
            context,
            PromptTemplateStore.ENTRY_VALIDATION,
            mapOf("text" to text)
        )

    private suspend fun callCloudGemini(prompt: String, apiKey: String): ValidationResult {
        val model = GenerativeModel(
            modelName = GeminiConfig.MODEL_NAME,
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.1f
                maxOutputTokens = 256
            }
        )

        val response = model.generateContent(prompt)
        val responseText = response.text?.trim()
            ?: throw Exception("Empty LLM response")

        Log.d(TAG, "Cloud LLM validation response: $responseText")
        return parseValidationResponse(com.trama.app.summary.JsonRepair.extractJson(responseText))
    }

    private suspend fun callLocalModel(prompt: String): ValidationResult? {
        if (!com.trama.app.summary.GemmaClient.isModelAvailable(context)) return null

        val responseText = com.trama.app.summary.GemmaClient.generate(
            context, prompt, maxTokens = 128
        ) ?: return null

        Log.d(TAG, "Local model validation response: $responseText")

        // Try parsing as JSON (same format as Cloud)
        try {
            val cleaned = com.trama.app.summary.JsonRepair.extractJson(responseText)
            return parseValidationResponse(cleaned)
        } catch (_: Exception) {
            // Fallback: interpret as simple yes/no
            val lower = responseText.trim().lowercase()
            val isValid = lower.contains("true") || lower.startsWith("si") ||
                lower.startsWith("sí") || lower.contains("\"es_nota_personal\":true") ||
                lower.contains("es_nota_personal\": true")
            return ValidationResult(
                isValid = isValid,
                correctedText = null,
                confidence = 0.7f,
                reason = if (isValid) "Validado por IA local" else "Rechazado por IA local"
            )
        }
    }

    private fun parseValidationResponse(responseText: String): ValidationResult {
        return try {
            val parsed = json.decodeFromString<LLMValidationResponse>(responseText)
            ValidationResult(
                isValid = parsed.es_nota_personal,
                correctedText = parsed.correccion,
                confidence = parsed.confianza,
                reason = if (parsed.es_nota_personal) "Validado por IA" else "Rechazado por IA"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse LLM response: $responseText", e)
            ValidationResult(
                isValid = true,
                correctedText = null,
                confidence = 0.5f,
                reason = "Error parseando respuesta IA"
            )
        }
    }

    private fun getApiKey(): String? {
        return context.getSharedPreferences("daily_summary", Context.MODE_PRIVATE)
            .getString("gemini_api_key", null)
    }

    @Serializable
    private data class LLMValidationResponse(
        val es_nota_personal: Boolean,
        val correccion: String? = null,
        val confianza: Float = 0.5f
    )
}
