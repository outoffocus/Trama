package com.trama.app.location

import com.trama.shared.model.DwellDetectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reproduces the Bug 2 scenario: the user stays at a restaurant, the GPS
 * oscillates around the exit radius, and the detector must not close a dwell
 * and immediately reopen a new one on the same spot.
 *
 * Uses a stub distance function (axis-aligned, 1° ~= 111 km) to avoid the
 * Android framework dependency on [android.location.Location.distanceBetween].
 */
class DwellDetectorHysteresisTest {

    /** Treat inputs as (meters, meters) on a flat plane: distance = hypot delta. */
    private val planarDistance: (Double, Double, Double, Double) -> Float =
        { aX, aY, bX, bY ->
            val dx = bX - aX
            val dy = bY - aY
            kotlin.math.sqrt(dx * dx + dy * dy).toFloat()
        }

    private val minute = 60_000L

    private fun sample(x: Double, y: Double, t: Long, acc: Float = 10f) =
        GeoSample(latitude = x, longitude = y, accuracyMeters = acc, timestamp = t)

    @Test
    fun gpsOscillation_doesNotCloseOrOpenSecondDwell() {
        val detector = DwellDetector(
            config = DwellDetectorConfig(
                entryRadiusMeters = 80f,
                exitRadiusMeters = 200f,
                dwellThresholdMillis = 15 * minute,
                exitConfirmationMillis = 2 * minute,
                reentryCooldownMillis = 5 * minute
            ),
            distance = planarDistance
        )

        // 1) Sit at (0,0) for 20 min — dwell should open after 15.
        val samples = mutableListOf<GeoSample>().apply {
            var t = 0L
            while (t <= 20 * minute) {
                add(sample(0.0, 0.0, t))
                t += minute
            }
            // 2) GPS jumps to 210m away once → detector thinks user exited.
            add(sample(210.0, 0.0, 21 * minute))
            // 3) Next samples come back near the anchor within cooldown.
            add(sample(70.0, 0.0, 22 * minute))
            add(sample(30.0, 0.0, 23 * minute))
            add(sample(0.0, 0.0, 24 * minute))
            // 4) User stays for 30 more minutes — should NOT reopen a new dwell.
            var t2 = 25 * minute
            while (t2 <= 55 * minute) {
                add(sample(0.0, 0.0, t2))
                t2 += minute
            }
        }

        var state: DwellDetectionState? = null
        val closed = mutableListOf<ClosedDwell>()
        samples.forEach { s ->
            val r = detector.process(state, s)
            state = r.nextState
            closed += r.closedDwells
        }

        assertEquals("GPS oscillation should not close the dwell", 0, closed.size)
        assertTrue("Dwell should remain active at the original place", state?.active == true)
        assertEquals(0.0, state?.anchorLat)
    }

    @Test
    fun realExit_afterCooldown_opensNewDwell() {
        val detector = DwellDetector(
            config = DwellDetectorConfig(
                entryRadiusMeters = 80f,
                exitRadiusMeters = 200f,
                dwellThresholdMillis = 15 * minute,
                exitConfirmationMillis = 2 * minute,
                reentryCooldownMillis = 5 * minute
            ),
            distance = planarDistance
        )

        val samples = mutableListOf<GeoSample>().apply {
            // Sit at (0,0) for 20 min.
            var t = 0L
            while (t <= 20 * minute) { add(sample(0.0, 0.0, t)); t += minute }
            // Walk away 500 m for 10 min (exceeds exit radius, closes dwell).
            add(sample(500.0, 0.0, 21 * minute))
            var t2 = 22 * minute
            while (t2 <= 31 * minute) { add(sample(500.0, 0.0, t2)); t2 += minute }
            // Sit at a second spot 1 km away for 20 min — beyond cooldown and radius.
            var t3 = 32 * minute
            while (t3 <= 55 * minute) { add(sample(1000.0, 0.0, t3)); t3 += minute }
        }

        var state: DwellDetectionState? = null
        val closed = mutableListOf<ClosedDwell>()
        samples.forEach { s ->
            val r = detector.process(state, s)
            state = r.nextState
            closed += r.closedDwells
        }

        assertEquals("Expected one closed dwell at the first spot", 1, closed.size)
        assertEquals("The dwell should end at the first outside sample, not after confirmation delay", 21 * minute, closed[0].endTimestamp)
        assertTrue(state?.active == true)
        assertEquals(1000.0, state?.anchorLat)
    }
}
