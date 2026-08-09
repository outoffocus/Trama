package com.trama.app.security

import android.content.Context

object SecureSecretStore {
    private const val PREFS = "daily_summary"
    private const val GEMINI_API_KEY = "gemini_api_key"

    fun getGeminiApiKey(context: Context): String? {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = preferences.getString(GEMINI_API_KEY, null) ?: return null
        val plain = SecretCipher.decryptOrPlain(stored)
        if (plain.isNotBlank() && !SecretCipher.isEncrypted(stored)) {
            runCatching { SecretCipher.encrypt(plain) }
                .onSuccess { encrypted ->
                    preferences.edit().putString(GEMINI_API_KEY, encrypted).apply()
                }
        }
        return plain.ifBlank { null }
    }

    fun setGeminiApiKey(context: Context, value: String) {
        val trimmed = value.trim()
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (trimmed.isBlank()) {
            preferences.edit().remove(GEMINI_API_KEY).apply()
        } else {
            preferences.edit()
                .putString(GEMINI_API_KEY, SecretCipher.encrypt(trimmed))
                .apply()
        }
    }
}
