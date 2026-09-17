package com.trama.shared.sync

import java.io.File
import java.security.MessageDigest

/** Receipt is issued only after durable storage on the phone. */
object RecordingReceipt {
    const val PATH = "/trama/recording-receipt"

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun matches(file: File, byteCount: Long, checksum: String): Boolean =
        file.isFile && !file.name.endsWith(".part") && byteCount > 0 &&
            file.length() == byteCount && checksum.length == 64 && sha256(file) == checksum
}
