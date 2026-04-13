package com.trama.app.chat

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.trama.app.GeminiConfig
import com.trama.app.summary.GemmaClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Multi-turn diary assistant.
 *
 * Priority chain per message:
 *   1. Gemini Cloud  — if API key is configured. Uses startChat() for native multi-turn.
 *   2. Gemma local   — if model is downloaded & enabled. Multi-turn is simulated by
 *                       appending the full conversation history to the prompt on every call.
 *   3. No model      — returns a user-facing error string.
 *
 * Call clearHistory() to start a fresh conversation (also resets context cache).
 */
class DiaryAssistant(
    private val context: Context,
    private val contextBuilder: DiaryContextBuilder
) {

    private val prefs
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getApiKey(): String? = prefs.getString(KEY_API_KEY, null)

    fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()

    // ── Gemini state (lazy session, persists for the conversation) ────────────
    private var cloudSession: Chat? = null

    // ── Gemma state (manual history for simulated multi-turn) ────────────────
    // Each entry is Pair(role, text): role is "Usuario" or "Asistente"
    private val localHistory = mutableListOf<Pair<String, String>>()

    // ── Public API ────────────────────────────────────────────────────────────

    suspend fun send(userMessage: String): String {
        // 1. Try Gemini Cloud
        val apiKey = getApiKey()
        if (!apiKey.isNullOrBlank()) {
            try {
                return sendWithCloud(userMessage, apiKey)
            } catch (e: Exception) {
                Log.w(TAG, "Cloud chat failed, trying local model: ${e.message}")
            }
        }

        // 2. Try Gemma local model
        if (GemmaClient.isModelAvailable(context)) {
            try {
                val reply = sendWithLocalModel(userMessage)
                if (reply != null) return reply
            } catch (e: Exception) {
                Log.w(TAG, "Local model failed: ${e.message}")
            }
        }

        // 3. Nothing available
        return if (apiKey.isNullOrBlank()) {
            "⚠️ Configura tu API key de Gemini en Ajustes → IA, o descarga el modelo local, para usar el asistente."
        } else {
            "❌ No se pudo obtener respuesta (Cloud y modelo local fallaron)."
        }
    }

    fun clearHistory() {
        cloudSession = null
        localHistory.clear()
        contextBuilder.invalidate()
    }

    // ── Gemini Cloud ──────────────────────────────────────────────────────────

    private suspend fun sendWithCloud(userMessage: String, apiKey: String): String {
        val session = cloudSession ?: createCloudSession(apiKey).also { cloudSession = it }
        val response = session.sendMessage(userMessage)
        return response.text?.trim() ?: throw Exception("Empty response from Gemini")
    }

    private suspend fun createCloudSession(apiKey: String): Chat {
        val diaryContext = contextBuilder.getContext()
        val today = todayString()

        val systemPrompt = buildSystemPrompt(diaryContext, today)

        val model = GenerativeModel(
            modelName = GeminiConfig.MODEL_NAME,
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.4f
                maxOutputTokens = 1024
            },
            systemInstruction = content { text(systemPrompt) }
        )

        return model.startChat()
    }

    // ── Gemma local (simulated multi-turn) ────────────────────────────────────

    private suspend fun sendWithLocalModel(userMessage: String): String? {
        val compactContext = contextBuilder.getContext()
        val today = todayString()

        // System instruction: diary context + persona.
        // Passed separately so GemmaClient can inject it as a proper system turn
        // (LiteRT-LM: ConversationConfig.systemInstruction; MediaPipe: <start_of_turn>system).
        val systemInstruction = buildSystemPrompt(compactContext, today)

        // Prompt: only conversation history + current question (no context duplication)
        val prompt = buildString {
            val historyWindow = localHistory.takeLast(MAX_LOCAL_HISTORY_MESSAGES)
            if (historyWindow.isNotEmpty()) {
                historyWindow.forEach { (role, text) ->
                    appendLine("$role: $text")
                }
            }
            appendLine("Usuario: $userMessage")
            append("Asistente:")
        }

        val reply = GemmaClient.generate(
            context = context,
            prompt = prompt,
            maxTokens = 1024,
            systemInstruction = systemInstruction
        ) ?: return null

        // Store exchange in local history
        localHistory.add("Usuario" to userMessage)
        localHistory.add("Asistente" to reply)

        return reply
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildSystemPrompt(diaryContext: String, today: String) = buildString {
        appendLine("Eres el asistente personal del usuario de Trama, una app de diario personal y captura de voz.")
        appendLine("Tienes acceso a su historial completo de tareas, lugares visitados y resúmenes diarios.")
        appendLine("Responde siempre en español, de forma directa y concisa.")
        appendLine("Si no tienes información suficiente, dilo con claridad — nunca inventes datos.")
        appendLine("Fecha actual: $today.")
        appendLine()
        append(diaryContext)
    }

    private fun todayString(): String =
        SimpleDateFormat("EEEE d 'de' MMMM yyyy", Locale("es"))
            .format(Date())
            .replaceFirstChar { it.uppercase() }

    companion object {
        private const val TAG = "DiaryAssistant"
        private const val PREFS_NAME = "daily_summary"
        private const val KEY_API_KEY = "gemini_api_key"

        /** Max conversation messages kept in local history (Gemma 4 E4B: 128K token window) */
        private const val MAX_LOCAL_HISTORY_MESSAGES = 60 // 30 user + 30 assistant
    }
}
