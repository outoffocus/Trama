package com.trama.shared.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DayRangeTest {

    private val madrid = TimeZone.getTimeZone("Europe/Madrid")

    private fun epochOf(
        year: Int, month: Int, day: Int,
        hour: Int = 0, minute: Int = 0, second: Int = 0, ms: Int = 0,
        zone: TimeZone = madrid
    ): Long = Calendar.getInstance(zone).apply {
        clear()
        set(year, month, day, hour, minute, second)
        set(Calendar.MILLISECOND, ms)
    }.timeInMillis

    @Test
    fun of_returnsMidnightToMidnight() {
        val noon = epochOf(2026, Calendar.APRIL, 22, hour = 12)
        val range = DayRange.of(noon, madrid)

        assertEquals(epochOf(2026, Calendar.APRIL, 22), range.startMs)
        assertEquals(epochOf(2026, Calendar.APRIL, 23), range.endExclusiveMs)
        assertEquals(range.endExclusiveMs - 1L, range.endInclusiveMs)
    }

    @Test
    fun containsBoundariesAreHalfOpen() {
        val range = DayRange.of(epochOf(2026, Calendar.APRIL, 22, hour = 10), madrid)
        assertTrue(range.startMs in range)
        assertTrue(range.endInclusiveMs in range)
        assertFalse(range.endExclusiveMs in range)
    }

    @Test
    fun dstSpringForward_dayIs23HoursLong() {
        // Europe/Madrid: 2026-03-29 spring forward at 02:00 → 03:00
        val range = DayRange.of(epochOf(2026, Calendar.MARCH, 29, hour = 12), madrid)
        val expectedMs = 23L * 60 * 60 * 1000
        assertEquals(expectedMs, range.durationMs)
    }

    @Test
    fun dstFallBack_dayIs25HoursLong() {
        // Europe/Madrid: 2026-10-25 fall back at 03:00 → 02:00
        val range = DayRange.of(epochOf(2026, Calendar.OCTOBER, 25, hour = 12), madrid)
        val expectedMs = 25L * 60 * 60 * 1000
        assertEquals(expectedMs, range.durationMs)
    }
}
