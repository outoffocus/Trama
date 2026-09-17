package com.trama.app.diagnostics

/** Called under CaptureLog's file lock. Retention can leave a file above the size threshold. */
internal class LogRotationSchedule {
    private var lastAttemptNanos: Long? = null

    fun shouldRotate(fileBytes: Long, nowNanos: Long): Boolean {
        if (fileBytes < 2L * 1024 * 1024) return false
        val previous = lastAttemptNanos
        if (previous != null && nowNanos - previous < 15L * 60 * 1_000_000_000) return false
        // Rate-limit failed attempts too, so storage failures cannot cause an I/O loop.
        lastAttemptNanos = nowNanos
        return true
    }
}
