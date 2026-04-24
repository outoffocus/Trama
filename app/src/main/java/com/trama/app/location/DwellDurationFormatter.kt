package com.trama.app.location

import java.util.Locale

object DwellDurationFormatter {
    fun formatHours(startTimestamp: Long, endTimestamp: Long?): String {
        val end = endTimestamp ?: return "En curso"
        val durationMs = (end - startTimestamp).coerceAtLeast(0L)
        return formatHours(durationMs)
    }

    fun formatHours(durationMs: Long): String {
        val hours = durationMs / HOUR_MS.toDouble()

        return when {
            hours < 0.1 -> "<0,1 h"
            hours >= 10.0 -> "${hours.toLong()} h"
            else -> String.format(SPANISH_LOCALE, "%.1f h", hours)
        }
    }

    private const val HOUR_MS = 60 * 60 * 1000L
    private val SPANISH_LOCALE = Locale("es", "ES")
}
