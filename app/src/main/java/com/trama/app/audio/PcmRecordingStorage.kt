package com.trama.app.audio

import android.content.Context
import com.trama.shared.audio.CapturedAudioWindow
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

object PcmRecordingStorage {
    data class AudioStats(
        val sampleCount: Long,
        val rms: Double,
        val peak: Int
    )

    const val SAMPLE_RATE_HZ = 16_000
    const val BYTES_PER_SAMPLE = 2
    private const val DIRECTORY = "recordings"
    private const val PENDING_SUFFIX = ".part"

    fun createPendingFile(context: Context, createdAt: Long): File {
        val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        return File(directory, "recording-$createdAt-${UUID.randomUUID()}.pcm$PENDING_SUFFIX")
    }

    fun finalizePending(file: File): File {
        if (!file.name.endsWith(PENDING_SUFFIX)) return file
        require(file.exists()) { "Pending PCM file does not exist" }
        val finalFile = File(file.parentFile, file.name.removeSuffix(PENDING_SUFFIX))
        runCatching {
            Files.move(
                file.toPath(),
                finalFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        }.recoverCatching {
            Files.move(file.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.getOrThrow()
        return finalFile
    }

    fun resolveManagedFile(context: Context, path: String?): File? {
        if (path.isNullOrBlank()) return null
        val root = File(context.filesDir, DIRECTORY).canonicalFile
        val candidate = File(path).canonicalFile
        return candidate.takeIf { it.parentFile == root && it.exists() && it.isFile }
    }

    fun durationSeconds(file: File, sampleRateHz: Int = SAMPLE_RATE_HZ): Int {
        if (sampleRateHz <= 0) return 0
        return (file.length() / (BYTES_PER_SAMPLE * sampleRateHz)).toInt()
    }

    fun audioStats(file: File): AudioStats {
        var sampleCount = 0L
        var squaredSum = 0.0
        var peak = 0
        val bytes = ByteArray(32 * 1024)
        var trailingByte: Int? = null

        FileInputStream(file).buffered().use { input ->
            while (true) {
                val count = input.read(bytes)
                if (count < 0) break
                var offset = 0
                trailingByte?.let { lowByte ->
                    if (count > 0) {
                        val sample = (((bytes[0].toInt() and 0xff) shl 8) or lowByte).toShort()
                        val value = sample.toInt()
                        squaredSum += value.toDouble() * value
                        peak = maxOf(peak, kotlin.math.abs(value))
                        sampleCount++
                        offset = 1
                        trailingByte = null
                    }
                }
                while (offset + 1 < count) {
                    val sample = (((bytes[offset + 1].toInt() and 0xff) shl 8) or
                        (bytes[offset].toInt() and 0xff)).toShort()
                    val value = sample.toInt()
                    squaredSum += value.toDouble() * value
                    peak = maxOf(peak, kotlin.math.abs(value))
                    sampleCount++
                    offset += BYTES_PER_SAMPLE
                }
                if (offset < count) trailingByte = bytes[offset].toInt() and 0xff
            }
        }

        return AudioStats(
            sampleCount = sampleCount,
            rms = if (sampleCount == 0L) 0.0 else kotlin.math.sqrt(squaredSum / sampleCount),
            peak = peak
        )
    }

    fun readWindows(
        file: File,
        chunkDurationMs: Long,
        sampleRateHz: Int = SAMPLE_RATE_HZ
    ): Sequence<CapturedAudioWindow> = sequence {
        val samplesPerChunk = ((chunkDurationMs * sampleRateHz) / 1000L)
            .toInt()
            .coerceAtLeast(1)
        val bytes = ByteArray(samplesPerChunk * BYTES_PER_SAMPLE)
        FileInputStream(file).buffered().use { input ->
            while (true) {
                var count = 0
                while (count < bytes.size) {
                    val read = input.read(bytes, count, bytes.size - count)
                    if (read < 0) break
                    count += read
                }
                val completeByteCount = count - (count % BYTES_PER_SAMPLE)
                if (completeByteCount <= 0) break
                val pcm = ShortArray(completeByteCount / BYTES_PER_SAMPLE) { index ->
                    val offset = index * BYTES_PER_SAMPLE
                    (((bytes[offset + 1].toInt() and 0xff) shl 8) or
                        (bytes[offset].toInt() and 0xff)).toShort()
                }
                yield(
                    CapturedAudioWindow(
                        preRollPcm = shortArrayOf(),
                        livePcm = pcm,
                        sampleRateHz = sampleRateHz
                    )
                )
                if (count < bytes.size) break
            }
        }
    }
}
