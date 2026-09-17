package com.trama.app.summary

import kotlinx.serialization.json.*
import java.util.Calendar
import java.util.TimeZone

internal object CalendarImportIdentity {
    private val json = Json { ignoreUnknownKeys = true }

    fun key(payload: String?): String? = runCatching {
        val data = json.parseToJsonElement(requireNotNull(payload)).jsonObject
        listOf("calendarId", "eventId", "startMillis").joinToString(":") {
            data.getValue(it).jsonPrimitive.content
        }
    }.getOrNull()

    fun allDay(payload: String?): Boolean = runCatching {
        json.parseToJsonElement(requireNotNull(payload))
            .jsonObject["allDay"]
            ?.jsonPrimitive
            ?.booleanOrNull == true
    }.getOrDefault(false)

    fun localAllDay(utcMillis: Long, zone: TimeZone = TimeZone.getDefault()): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
        return Calendar.getInstance(zone).apply {
            clear()
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }
}
