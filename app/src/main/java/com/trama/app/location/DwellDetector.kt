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
    val dwellThresholdMillis: Long = 15 * 60 * 1000L
)

data class DwellDetectorResult(
    val nextState: DwellDetectionState,
    val closedDwells: List<ClosedDwell> = emptyList()
)

class DwellDetector(
    private val config: DwellDetectorConfig
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
            val distance = distanceMeters(
                state.candidateLat ?: sample.latitude,
                state.candidateLon ?: sample.longitude,
                sample.latitude,
                sample.longitude
            )

            if (distance <= config.entryRadiusMeters) {
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

        val distanceFromAnchor = distanceMeters(
            state.anchorLat ?: sample.latitude,
            state.anchorLon ?: sample.longitude,
            sample.latitude,
            sample.longitude
        )

        if (distanceFromAnchor <= config.exitRadiusMeters) {
            return DwellDetectorResult(nextState = state.copy(updatedAt = updatedAt))
        }

        val closed = splitAcrossDays(
            startTimestamp = state.dwellStartedAt ?: sample.timestamp,
            endTimestamp = sample.timestamp,
            latitude = state.anchorLat ?: sample.latitude,
            longitude = state.anchorLon ?: sample.longitude
        )

        return DwellDetectorResult(
            nextState = DwellDetectionState(
                candidateLat = sample.latitude,
                candidateLon = sample.longitude,
                candidateStartedAt = sample.timestamp,
                candidateLastSeenAt = sample.timestamp,
                updatedAt = updatedAt
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

    private fun distanceMeters(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double
    ): Float {
        val result = FloatArray(1)
        Location.distanceBetween(startLat, startLon, endLat, endLon, result)
        return result[0]
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
