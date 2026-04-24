package com.trama.app.location

import android.location.Location
import com.trama.shared.model.DwellDetectionState
import kotlin.math.min

data class GeoSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestamp: Long
)

data class ClosedDwell(
    val startTimestamp: Long,
    val endTimestamp: Long,
    val latitude: Double,
    val longitude: Double
)

data class DwellDetectorConfig(
    val entryRadiusMeters: Float = 80f,
    val exitRadiusMeters: Float = 200f,
    val dwellThresholdMillis: Long = 15 * 60 * 1000L,
    /**
     * A dwell is not closed on the first sample outside the exit radius. That
     * first sample is treated as a pending exit and must remain outside long
     * enough to avoid shortening visits because of a single GPS jump.
     */
    val exitConfirmationMillis: Long = 2 * 60 * 1000L,
    /** Window during which re-entering the radius of a just-closed dwell does NOT
     *  start a new candidate; instead it is ignored (treated as GPS drift). */
    val reentryCooldownMillis: Long = 5 * 60 * 1000L
)

data class DwellDetectorResult(
    val nextState: DwellDetectionState,
    val closedDwells: List<ClosedDwell> = emptyList()
)

class DwellDetector(
    private val config: DwellDetectorConfig,
    private val distance: (Double, Double, Double, Double) -> Float = ::defaultDistanceMeters
) {

    fun process(
        currentState: DwellDetectionState?,
        sample: GeoSample
    ): DwellDetectorResult {
        val state = currentState ?: DwellDetectionState()
        val updatedAt = sample.timestamp

        if (sample.accuracyMeters > 100f) {
            return DwellDetectorResult(
                nextState = state.copy(updatedAt = updatedAt)
            )
        }

        // Re-entry suppression: if the previous dwell was just closed and the user
        // is still within its exit radius, treat the sample as drift — do not
        // accumulate a new candidate that would trigger a duplicate dwell on the
        // same place (user never actually left the extended vicinity).
        val lastClosedLat = state.lastClosedLat
        val lastClosedLon = state.lastClosedLon
        val lastClosedAt = state.lastClosedAt
        if (!state.active &&
            lastClosedLat != null && lastClosedLon != null && lastClosedAt != null &&
            sample.timestamp - lastClosedAt <= config.reentryCooldownMillis
        ) {
            val distanceFromLastClosed = distance(
                lastClosedLat, lastClosedLon, sample.latitude, sample.longitude
            )
            if (distanceFromLastClosed <= config.exitRadiusMeters) {
                // Inside cooldown window and still near the closed dwell: discard any
                // in-flight candidate and wait. When the cooldown expires or the user
                // walks past the exit radius, normal detection resumes.
                return DwellDetectorResult(
                    nextState = state.copy(
                        candidateLat = null,
                        candidateLon = null,
                        candidateStartedAt = null,
                        candidateLastSeenAt = null,
                        updatedAt = updatedAt
                    )
                )
            }
        }

        if (!state.active && state.candidateLat == null) {
            return DwellDetectorResult(
                nextState = state.copy(
                    candidateLat = sample.latitude,
                    candidateLon = sample.longitude,
                    candidateStartedAt = sample.timestamp,
                    candidateLastSeenAt = sample.timestamp,
                    updatedAt = updatedAt
                )
            )
        }

        if (!state.active) {
            val distanceFromCandidate = distance(
                state.candidateLat ?: sample.latitude,
                state.candidateLon ?: sample.longitude,
                sample.latitude,
                sample.longitude
            )

            if (distanceFromCandidate <= config.entryRadiusMeters) {
                val startedAt = state.candidateStartedAt ?: sample.timestamp
                val elapsed = sample.timestamp - startedAt
                return if (elapsed >= config.dwellThresholdMillis) {
                    DwellDetectorResult(
                        nextState = state.copy(
                            anchorLat = state.candidateLat,
                            anchorLon = state.candidateLon,
                            dwellStartedAt = startedAt,
                            active = true,
                            candidateLat = null,
                            candidateLon = null,
                            candidateStartedAt = null,
                            candidateLastSeenAt = null,
                            updatedAt = updatedAt
                        )
                    )
                } else {
                    DwellDetectorResult(
                        nextState = state.copy(
                            candidateLastSeenAt = sample.timestamp,
                            updatedAt = updatedAt
                        )
                    )
                }
            }

            return DwellDetectorResult(
                nextState = state.copy(
                    candidateLat = sample.latitude,
                    candidateLon = sample.longitude,
                    candidateStartedAt = sample.timestamp,
                    candidateLastSeenAt = sample.timestamp,
                    updatedAt = updatedAt
                )
            )
        }

        val distanceFromAnchor = distance(
            state.anchorLat ?: sample.latitude,
            state.anchorLon ?: sample.longitude,
            sample.latitude,
            sample.longitude
        )

        if (distanceFromAnchor <= config.exitRadiusMeters) {
            return DwellDetectorResult(
                nextState = state.copy(
                    candidateLat = null,
                    candidateLon = null,
                    candidateStartedAt = null,
                    candidateLastSeenAt = null,
                    updatedAt = updatedAt
                )
            )
        }

        val pendingExitStartedAt = state.candidateStartedAt
        if (pendingExitStartedAt == null) {
            return DwellDetectorResult(
                nextState = state.copy(
                    candidateLat = sample.latitude,
                    candidateLon = sample.longitude,
                    candidateStartedAt = sample.timestamp,
                    candidateLastSeenAt = sample.timestamp,
                    updatedAt = updatedAt
                )
            )
        }

        val pendingExitElapsed = sample.timestamp - pendingExitStartedAt
        if (pendingExitElapsed < config.exitConfirmationMillis) {
            return DwellDetectorResult(
                nextState = state.copy(
                    candidateLat = sample.latitude,
                    candidateLon = sample.longitude,
                    candidateLastSeenAt = sample.timestamp,
                    updatedAt = updatedAt
                )
            )
        }

        val closed = splitAcrossDays(
            startTimestamp = state.dwellStartedAt ?: sample.timestamp,
            endTimestamp = pendingExitStartedAt,
            latitude = state.anchorLat ?: sample.latitude,
            longitude = state.anchorLon ?: sample.longitude
        )

        val closedAnchorLat = state.anchorLat ?: sample.latitude
        val closedAnchorLon = state.anchorLon ?: sample.longitude
        return DwellDetectorResult(
            nextState = DwellDetectionState(
                candidateLat = sample.latitude,
                candidateLon = sample.longitude,
                candidateStartedAt = sample.timestamp,
                candidateLastSeenAt = sample.timestamp,
                updatedAt = updatedAt,
                lastClosedLat = closedAnchorLat,
                lastClosedLon = closedAnchorLon,
                lastClosedAt = sample.timestamp
            ),
            closedDwells = closed
        )
    }

    private fun splitAcrossDays(
        startTimestamp: Long,
        endTimestamp: Long,
        latitude: Double,
        longitude: Double
    ): List<ClosedDwell> {
        if (endTimestamp <= startTimestamp) return emptyList()

        val dwells = mutableListOf<ClosedDwell>()
        var segmentStart = startTimestamp

        while (segmentStart < endTimestamp) {
            val nextMidnight = ((segmentStart / DAY_MS) + 1) * DAY_MS
            val segmentEnd = min(endTimestamp, nextMidnight)
            dwells += ClosedDwell(
                startTimestamp = segmentStart,
                endTimestamp = segmentEnd,
                latitude = latitude,
                longitude = longitude
            )
            segmentStart = segmentEnd
        }

        return dwells
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}

private fun defaultDistanceMeters(
    startLat: Double,
    startLon: Double,
    endLat: Double,
    endLon: Double
): Float {
    val result = FloatArray(1)
    Location.distanceBetween(startLat, startLon, endLat, endLon, result)
    return result[0]
}
