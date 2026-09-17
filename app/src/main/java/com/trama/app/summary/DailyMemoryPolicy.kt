package com.trama.app.summary

import java.util.Calendar
import java.util.TimeZone

/** Calendar-safe boundary for the most recent fully completed local day. */
object DailyMemoryPolicy {
    fun previousDayStart(
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long = Calendar.getInstance(timeZone).run {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -1)
        timeInMillis
    }
}
