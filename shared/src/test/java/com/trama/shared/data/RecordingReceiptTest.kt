package com.trama.shared.data

import com.trama.shared.sync.RecordingReceipt
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class RecordingReceiptTest {
    @Test fun `receipt only matches a finalized identical file`() {
        val file = File.createTempFile("recording", ".pcm")
        try {
            file.writeBytes(byteArrayOf(1,2,3,4))
            val hash = RecordingReceipt.sha256(file)
            assertTrue(RecordingReceipt.matches(file, 4, hash))
            assertFalse(RecordingReceipt.matches(file, 3, hash))
            file.writeBytes(byteArrayOf(4,3,2,1))
            assertFalse(RecordingReceipt.matches(file, 4, hash))
        } finally { file.delete() }
    }
}
