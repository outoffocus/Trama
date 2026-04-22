package com.trama.shared.util

import java.util.Calendar
import java.util.TimeZone

/**
 * A calendar-day range in the given timezone.
 *
 * [startMs] is inclusive (00:00:00.000 of the day).
 * [endExclusiveMs] is exclusive (00:00:00.000 of the next day).
 *
 * Use [endExclusiveMs] with `until`/`<` comparisons, or [endInclusiveMs] with
 * `BETWEEN`/`<=` comparisons. The two differ by 1 ms — do not mix conventions.
 *
 * The range is computed via [Calendar] so it honors DST transitions (a day may be
 * 23h or 25h long on transition days, not always 86_400_000 ms).
 */
data class DayRange(
    val startMs: Long,
    val endExclusiveMs: Long
) {
    val endInclusiveMs: Long get() = endExclusiveMs - 1L
    val durationMs: Long get() = endExclusiveMs - startMs

    operator fun contains(epochMs: Long): Boolean =
        epochMs in startMs until endExclusiveMs

    companion object {
        /** Day containing [epochMs] in [zone] (defaults to system zone). */
        fun of(epochMs: Long, zone: TimeZone = TimeZone.getDefault()): DayRange {
            val cal = Calendar.getInstance(zone).apply {
                timeInMillis = epochMs
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
            return DayRange(start, cal.timeInMillis)
        }

        /** Today's range in [zone]. */
        fun today(zone: TimeZone = TimeZone.getDefault()): DayRange =
            of(System.currentTimeMillis(), zone)
    }
}
