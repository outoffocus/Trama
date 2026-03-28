package com.mydiary.app.util

import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Sends text to Google Keep via ACTION_SEND.
 * The user can then select which list to add it to.
 */
object KeepHelper {

    private const val KEEP_PACKAGE = "com.google.android.keep"

    fun sendToKeep(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            // Target Google Keep specifically
            setPackage(KEEP_PACKAGE)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Keep not installed — fall back to generic share
            try {
                val fallback = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(fallback, "Compartir"))
            } catch (_: Exception) {
                Toast.makeText(context, "No se pudo compartir", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
