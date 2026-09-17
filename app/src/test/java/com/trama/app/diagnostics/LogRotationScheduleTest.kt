package com.trama.app.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRotationScheduleTest {
    @Test fun `oversized recent log does not rotate again on every append`() {
        val schedule = LogRotationSchedule()
        val size = 3L * 1024 * 1024
        assertTrue(schedule.shouldRotate(size, 0))
        for (second in 1L..899L) {
            assertFalse(schedule.shouldRotate(size + second * 1_000, second * 1_000_000_000))
        }
        assertTrue(schedule.shouldRotate(size, 900L * 1_000_000_000))
    }

    @Test fun `small file does not delay rotation when threshold is crossed`() {
        val schedule = LogRotationSchedule()
        assertFalse(schedule.shouldRotate(100, 0))
        assertTrue(schedule.shouldRotate(2L * 1024 * 1024, 1))
    }
}
