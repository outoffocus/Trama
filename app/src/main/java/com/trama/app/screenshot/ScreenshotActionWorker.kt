package com.trama.app.screenshot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.trama.app.MainActivity
import com.trama.app.NotificationConfig
import com.trama.app.R
import com.trama.app.summary.GemmaClient
import com.trama.app.summary.JsonRepair
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryActionType
import com.trama.shared.model.EntryPriority
import com.trama.shared.model.EntryStatus
import com.trama.shared.model.Source
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.io.File
import java.io.FileInputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ScreenshotActionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uri = inputData.getString(KEY_IMAGE_URI)?.let(Uri::parse) ?: return Result.failure()

        if (!GemmaClient.isModelAvailable(applicationContext)) {
            showResultNotification(
                title = "Modelo local no disponible",
                text = "Activa un modelo Gemma local multimodal para leer capturas."
            )
            return Result.failure()
        }

        val bitmap = try {
            decodeDownsampledBitmap(uri)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to decode screenshot", t)
            deletePrivateFile(uri)
            return Result.failure()
        } ?: run {
            deletePrivateFile(uri)
            return Result.failure()
        }

        var retrying = false
        return try {
            val extraction = extractActions(bitmap, uri)
            val inserted = persistExtraction(extraction)
            showResultNotification(
                title = if (inserted > 0) "Captura revisada" else "Captura sin acciones",
                text = if (inserted > 0) "$inserted sugerencias listas para revisar."
                else "No he visto nada accionable en la imagen."
            )
            Result.success()
        } catch (t: LocalMultimodalUnavailableException) {
            Log.w(TAG, "Local screenshot model unavailable: ${t.message}")
            showResultNotification(
                title = "Captura no procesada",
                text = t.message ?: "Hace falta un modelo local multimodal compatible."
            )
            Result.failure()
        } catch (t: Throwable) {
            Log.w(TAG, "Screenshot processing failed", t)
            retrying = true
            Result.retry()
        } finally {
            bitmap.recycle()
            // Keep the file alive if we're going to retry — delete it otherwise.
            if (!retrying) deletePrivateFile(uri)
        }
    }

    private suspend fun extractActions(bitmap: Bitmap, uri: Uri): ScreenshotExtraction {
        val imageFile = uri.toPrivateImageFile()

        // Try file-based path first (LiteRT only; returns null for MediaPipe models).
        // Fall back to bitmap path which works for both LiteRT and MediaPipe.
        val local = imageFile?.let {
            GemmaClient.generateMultimodalFromFiles(
                context = applicationContext,
                prompt = EXTRACT_FROM_IMAGE_PROMPT,
                imageFiles = listOf(it),
                maxTokens = 768,
                responsePrefix = "{",
                systemInstruction = "Eres un extractor local de acciones desde capturas. Devuelve solo JSON."
            )
        } ?: GemmaClient.generateMultimodal(
            context = applicationContext,
            prompt = EXTRACT_FROM_IMAGE_PROMPT,
            images = listOf(bitmap),
            maxTokens = 768,
            responsePrefix = "{",
            systemInstruction = "Eres un extractor local de acciones desde capturas. Devuelve solo JSON."
        )

        if (!local.isNullOrBlank()) {
            return json.decodeFromString(JsonRepair.extractAndRepair(local))
        }
        throw LocalMultimodalUnavailableException(
            "No hay respuesta local multimodal. Usa un modelo .task compatible con vision."
        )
    }

    private suspend fun persistExtraction(extraction: ScreenshotExtraction): Int {
        val repository = DatabaseProvider.getRepository(applicationContext)
        val contextPrefix = extraction.context?.takeIf { it.isNotBlank() }
            ?.let { "Captura: $it" }
            ?: "Captura de pantalla"
        val extractedText = extraction.extractedText.orEmpty().take(MAX_RAW_TEXT_CHARS)
        var inserted = 0

        extraction.actions
            .filter { it.text.isNotBlank() }
            .take(MAX_ACTIONS_PER_SCREENSHOT)
            .forEach { action ->
                repository.insert(
                    DiaryEntry(
                        text = listOf(contextPrefix, extractedText)
                            .filter { it.isNotBlank() }
                            .joinToString("\n"),
                        keyword = "screenshot",
                        category = action.type ?: "SCREENSHOT",
                        confidence = extraction.confidence?.toFloat() ?: 0.75f,
                        source = Source.SCREENSHOT,
                        duration = 0,
                        wasReviewedByLLM = true,
                        llmConfidence = extraction.confidence?.toFloat(),
                        status = EntryStatus.SUGGESTED,
                        actionType = action.type.toActionType(),
                        cleanText = action.text.trim(),
                        dueDate = action.dueDate.toDueDateMillis(),
                        priority = action.priority.toEntryPriority()
                    )
                )
                inserted += 1
            }

        return inserted
    }

    private fun decodeDownsampledBitmap(uri: Uri): Bitmap? {
        val resolver = applicationContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openImageInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = openImageInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null

        val maxSide = maxOf(decoded.width, decoded.height)
        if (maxSide <= MAX_IMAGE_SIDE) return decoded

        val scale = MAX_IMAGE_SIDE.toFloat() / maxSide.toFloat()
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true
        )
        decoded.recycle()
        return scaled
    }

    private fun openImageInputStream(uri: Uri) =
        if (uri.scheme == "file") FileInputStream(requireNotNull(uri.path))
        else applicationContext.contentResolver.openInputStream(uri)

    private fun Uri.toPrivateImageFile(): File? {
        if (scheme != "file") return null
        val path = path ?: return null
        val file = File(path)
        val screenshotsDir = File(applicationContext.filesDir, "screenshots").canonicalFile
        val canonical = file.canonicalFile
        return canonical.takeIf { it.parentFile == screenshotsDir && it.exists() }
    }

    private fun deletePrivateFile(uri: Uri) {
        if (uri.scheme != "file") return
        val path = uri.path ?: return
        val file = File(path)
        val screenshotsDir = File(applicationContext.filesDir, "screenshots").canonicalFile
        val canonical = file.canonicalFile
        if (canonical.parentFile == screenshotsDir) {
            canonical.delete()
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > MAX_IMAGE_SIDE * 2 || height / sampleSize > MAX_IMAGE_SIDE * 2) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun showResultNotification(title: String, text: String) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    NotificationConfig.CHANNEL_SCREENSHOT,
                    "Capturas de pantalla",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Resultado de extraer acciones desde capturas"
                }
            )
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra("navigate_to", "home")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, NotificationConfig.CHANNEL_SCREENSHOT)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NotificationConfig.ID_SCREENSHOT, notification)
    }

    private fun String?.toActionType(): String = when (this?.uppercase()) {
        "EVENT" -> EntryActionType.EVENT
        "CONTACT" -> EntryActionType.TALK_TO
        "RECEIPT" -> EntryActionType.REVIEW
        "REMINDER", "TASK", "NOTE" -> EntryActionType.GENERIC
        else -> EntryActionType.GENERIC
    }

    private fun String?.toEntryPriority(): String = when (this?.uppercase()) {
        "HIGH" -> EntryPriority.HIGH
        "LOW" -> EntryPriority.LOW
        "URGENT" -> EntryPriority.URGENT
        else -> EntryPriority.NORMAL
    }

    private fun String?.toDueDateMillis(): Long? {
        val value = this?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?: return null
        return try {
            Instant.parse(value).toEpochMilli()
        } catch (_: DateTimeParseException) {
            try {
                LocalDate.parse(value)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    @Serializable
    private data class ScreenshotExtraction(
        val actions: List<ScreenshotAction> = emptyList(),
        val extractedText: String? = null,
        val context: String? = null,
        val confidence: Double? = null
    )

    @Serializable
    private data class ScreenshotAction(
        val text: String,
        val dueDate: String? = null,
        val priority: String? = null,
        val type: String? = null
    )

    private class LocalMultimodalUnavailableException(message: String) : Exception(message)

    companion object {
        const val KEY_IMAGE_URI = "image_uri"
        private const val TAG = "ScreenshotActionWorker"
        private const val MAX_IMAGE_SIDE = 768
        private const val MAX_RAW_TEXT_CHARS = 1200
        private const val MAX_ACTIONS_PER_SCREENSHOT = 6

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        private const val EXTRACT_FROM_IMAGE_PROMPT = """
Eres Trama, un asistente que extrae acciones, recordatorios e informacion estructurada desde capturas de pantalla.
Devuelve SOLO JSON valido con esta forma:
{
  "actions": [
    {
      "text": "accion concreta en espanol",
      "dueDate": "ISO-8601 o null",
      "priority": "LOW|MEDIUM|HIGH",
      "type": "REMINDER|TASK|NOTE|CONTACT|EVENT|RECEIPT"
    }
  ],
  "extractedText": "solo el texto literal relevante; omite tarjetas, DNI, IBAN y numeros de tarjeta",
  "context": "una frase sobre la app o situacion probable",
  "confidence": 0.0
}
Si no hay nada accionable, devuelve {"actions":[],"extractedText":"","context":"","confidence":0.0}.
No inventes fechas; usa null si la fecha no esta clara.
"""
    }
}
