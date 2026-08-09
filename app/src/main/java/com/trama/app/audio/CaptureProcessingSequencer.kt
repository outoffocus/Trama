package com.trama.app.audio

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Ensures only one final capture owns Whisper and the intent pipeline at a time. */
class CaptureProcessingSequencer {
    private val mutex = Mutex()

    suspend fun <T> process(block: suspend () -> T): T = mutex.withLock { block() }
}
