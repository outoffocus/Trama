package com.trama.app.backup

import java.util.Calendar
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BackupSchedulerTest {
    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Madrid"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `backup before selected hour is scheduled for today`() {
        val now = localTime(2026, Calendar.SEPTEMBER, 13, 8, 30)

        assertEquals(105 * 60 * 1000L, BackupScheduler.calculateDelay(10, 15, now))
    }

    @Test
    fun `backup after selected hour is scheduled for tomorrow`() {
        val now = localTime(2026, Calendar.SEPTEMBER, 13, 10, 30)

        assertEquals(23 * 60 * 60 * 1000L + 30 * 60 * 1000L,
            BackupScheduler.calculateDelay(10, 0, now))
    }

    @Test
    fun `backup exactly on selected hour waits until next day`() {
        val now = localTime(2026, Calendar.SEPTEMBER, 13, 10, 0)

        assertEquals(24 * 60 * 60 * 1000L, BackupScheduler.calculateDelay(10, 0, now))
    }

    private fun localTime(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis
}
