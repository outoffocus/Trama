package com.trama.app.summary

import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyMemoryPolicyTest {
    private val madrid = TimeZone.getTimeZone("Europe/Madrid")
    private val zone = ZoneId.of("Europe/Madrid")

    @Test
    fun `returns start of previous local day`() {
        val now = Instant.parse("2026-09-09T10:15:00Z").toEpochMilli()

        val result = DailyMemoryPolicy.previousDayStart(now, madrid)

        assertEquals("2026-09-08T00:00", Instant.ofEpochMilli(result).atZone(zone).toLocalDateTime().toString())
    }

    @Test
    fun `handles daylight saving boundary as a calendar day`() {
        val now = Instant.parse("2026-03-29T10:00:00Z").toEpochMilli()

        val result = DailyMemoryPolicy.previousDayStart(now, madrid)

        assertEquals("2026-03-28T00:00", Instant.ofEpochMilli(result).atZone(zone).toLocalDateTime().toString())
    }
}
