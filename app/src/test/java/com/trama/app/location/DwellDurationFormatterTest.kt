package com.trama.app.location

import com.trama.shared.model.TimelineEvent
import com.trama.shared.model.TimelineEventType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DwellDurationFormatterTest {
    @Test
    fun activeVisitKeepsMeasuredDurationAndShowsThatItIsOngoing() {
        val event = TimelineEvent(
            type = TimelineEventType.DWELL,
            timestamp = 0,
            endTimestamp = 10 * 60_000L,
            title = "Cafetería",
            dataJson = "{\"active\":true}"
        )

        assertTrue(DwellDurationFormatter.isActive(event))
        assertTrue(DwellDurationFormatter.formatVisit(event).startsWith("En curso · "))
    }

    @Test
    fun closedVisitDoesNotLookOngoing() {
        val event = TimelineEvent(
            type = TimelineEventType.DWELL,
            timestamp = 0,
            endTimestamp = 10 * 60_000L,
            title = "Cafetería",
            dataJson = "{\"active\":false}"
        )

        assertFalse(DwellDurationFormatter.isActive(event))
        assertFalse(DwellDurationFormatter.formatVisit(event).startsWith("En curso"))
    }
}
