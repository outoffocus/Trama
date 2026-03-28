package com.mydiary.app.speech

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.mydiary.shared.speech.EntryValidatorHeuristics
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
        val apiKey = getApiKey()
        if (apiKey.isNullOrBlank()) {
            // No API key → accept ambiguous entries
            return ValidationResult(
                isValid = true,
                correctedText = null,
                confidence = 0.5f,
                reason = "Sin clave API, aceptado por defecto"
            )
        }

        val prompt = """Analiza esta transcripción de voz y responde SOLO con JSON:
{
  "es_nota_personal": true/false,
  "correccion": "texto corregido o null",
  "confianza": 0.0-1.0
}

Reglas:
- es_nota_personal=true si parece una intención, tarea, recordatorio o nota dicha por alguien para sí mismo o para un "nosotros" (grupo al que pertenece)
- es_nota_personal=false si parece radio, TV, conversación ajena, publicidad o fragmento sin sentido
- En "correccion": corrige errores de transcripción, puntuación y gramática. Si el texto está bien, pon null
- Sé permisivo: en caso de duda, marca como true

Transcripción: "$text"
"""

        val model = GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.1f
                maxOutputTokens = 256
            }
        )

        val response = model.generateContent(prompt)
        val responseText = response.text
            ?.replace("```json", "")?.replace("```", "")?.trim()
            ?: throw Exception("Empty LLM response")

        Log.d(TAG, "LLM validation response: $responseText")

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
