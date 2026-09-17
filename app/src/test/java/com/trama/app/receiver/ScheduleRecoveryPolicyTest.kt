package com.trama.app.receiver

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleRecoveryPolicyTest {
    @Test
    fun `boot time and timezone changes realign summaries`() {
        assertTrue(ScheduleRecoveryPolicy.isSupported(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(ScheduleRecoveryPolicy.isSupported(Intent.ACTION_TIME_CHANGED))
        assertTrue(ScheduleRecoveryPolicy.isSupported(Intent.ACTION_TIMEZONE_CHANGED))
    }

    @Test
    fun `unrelated broadcasts are ignored`() {
        assertFalse(ScheduleRecoveryPolicy.isSupported(Intent.ACTION_SCREEN_ON))
    }
}
