package com.mydiary.wear.speech

import com.mydiary.shared.speech.SpeakerProfile
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests the pure-logic parts of WatchSpeakerEnrollment:
 * - variance calculation
 * - profile merging (running average across enrollment samples)
 * - enrollment constants and phrases
 * - SpeakerProfile serialization roundtrip
 */
class WatchSpeakerEnrollmentLogicTest {

    // ── Variance calculation (same algorithm as WatchSpeakerEnrollment.variance) ──

    private fun variance(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return values.sumOf { (it - mean) * (it - mean) } / values.size
    }

    @Test
    fun `variance of empty list is zero`() {
        assertEquals(0.0, variance(emptyList()), 0.001)
    }

    @Test
    fun `variance of single value is zero`() {
        assertEquals(0.0, variance(listOf(5.0)), 0.001)
    }

    @Test
    fun `variance of identical values is zero`() {
        assertEquals(0.0, variance(listOf(3.0, 3.0, 3.0)), 0.001)
    }

    @Test
    fun `variance of known values`() {
        // [1, 2, 3, 4, 5] → mean=3, var = (4+1+0+1+4)/5 = 2.0
        assertEquals(2.0, variance(listOf(1.0, 2.0, 3.0, 4.0, 5.0)), 0.001)
    }

    @Test
    fun `variance of symmetric values`() {
        // [-1, 1] → mean=0, var = (1+1)/2 = 1.0
        assertEquals(1.0, variance(listOf(-1.0, 1.0)), 0.001)
    }

    @Test
    fun `variance increases with spread`() {
        val tight = variance(listOf(9.0, 10.0, 11.0))
        val wide = variance(listOf(5.0, 10.0, 15.0))
        assertTrue(wide > tight)
    }

    // ── Profile merging (running average) ──

    private fun mergeProfile(existing: SpeakerProfile, count: Int, newAvgRMS: Double, newVariance: Double): SpeakerProfile {
        return SpeakerProfile(
            avgRMS = (existing.avgRMS * count + newAvgRMS) / (count + 1),
            rmsVariance = (existing.rmsVariance * count + newVariance) / (count + 1),
            enrollmentCount = count + 1
        )
    }

    @Test
    fun `first sample creates profile directly`() {
        val profile = SpeakerProfile(avgRMS = 5.0, rmsVariance = 1.0, enrollmentCount = 1)
        assertEquals(5.0, profile.avgRMS, 0.001)
        assertEquals(1.0, profile.rmsVariance, 0.001)
        assertEquals(1, profile.enrollmentCount)
    }

    @Test
    fun `merge two samples averages correctly`() {
        val first = SpeakerProfile(avgRMS = 4.0, rmsVariance = 1.0, enrollmentCount = 1)
        val merged = mergeProfile(first, 1, newAvgRMS = 6.0, newVariance = 2.0)

        assertEquals(5.0, merged.avgRMS, 0.001)   // (4+6)/2
        assertEquals(1.5, merged.rmsVariance, 0.001) // (1+2)/2
        assertEquals(2, merged.enrollmentCount)
    }

    @Test
    fun `merge three samples weighted correctly`() {
        val first = SpeakerProfile(avgRMS = 4.0, rmsVariance = 1.0, enrollmentCount = 1)
        val second = mergeProfile(first, 1, newAvgRMS = 6.0, newVariance = 2.0)
        val third = mergeProfile(second, 2, newAvgRMS = 8.0, newVariance = 3.0)

        // avgRMS: (4+6+8)/3 = 6.0
        // But running average: (5.0*2 + 8.0)/3 = 18/3 = 6.0
        assertEquals(6.0, third.avgRMS, 0.001)
        // rmsVariance: (1+2+3)/3 = 2.0
        assertEquals(2.0, third.rmsVariance, 0.001)
        assertEquals(3, third.enrollmentCount)
    }

    @Test
    fun `merge preserves running average accuracy`() {
        // Verify that the running average formula gives the same result as a simple average
        val values = listOf(3.0, 7.0, 5.0, 9.0)
        val variances = listOf(0.5, 1.5, 1.0, 2.0)

        var profile = SpeakerProfile(avgRMS = values[0], rmsVariance = variances[0], enrollmentCount = 1)
        for (i in 1 until values.size) {
            profile = mergeProfile(profile, i, values[i], variances[i])
        }

        assertEquals(values.average(), profile.avgRMS, 0.001)
        assertEquals(variances.average(), profile.rmsVariance, 0.001)
        assertEquals(4, profile.enrollmentCount)
    }

    // ── Enrollment constants ──

    @Test
    fun `required samples is 3`() {
        assertEquals(3, WatchSpeakerEnrollment.REQUIRED_SAMPLES)
    }

    @Test
    fun `enrollment duration is 5 seconds`() {
        assertEquals(5000L, WatchSpeakerEnrollment.ENROLLMENT_DURATION_MS)
    }

    @Test
    fun `enrollment phrases has correct count`() {
        assertEquals(3, WatchSpeakerEnrollment.ENROLLMENT_PHRASES.size)
    }

    @Test
    fun `enrollment phrases are non-empty`() {
        WatchSpeakerEnrollment.ENROLLMENT_PHRASES.forEach {
            assertTrue("Phrase should not be blank: '$it'", it.isNotBlank())
        }
    }

    // ── SpeakerProfile serialization ──

    @Test
    fun `profile serialization roundtrip`() {
        val original = SpeakerProfile(avgRMS = 5.5, rmsVariance = 1.2, enrollmentCount = 3)
        val json = SpeakerProfile.serialize(original)
        val restored = SpeakerProfile.deserialize(json)

        assertNotNull(restored)
        assertEquals(original.avgRMS, restored!!.avgRMS, 0.001)
        assertEquals(original.rmsVariance, restored.rmsVariance, 0.001)
        assertEquals(original.enrollmentCount, restored.enrollmentCount)
    }

    @Test
    fun `deserialization of invalid json returns null`() {
        assertNull(SpeakerProfile.deserialize("not json"))
        assertNull(SpeakerProfile.deserialize("{}"))
    }

    @Test
    fun `deserialization ignores unknown keys`() {
        val json = """{"avgRMS":5.0,"rmsVariance":1.0,"enrollmentCount":2,"futureField":"hi"}"""
        val profile = SpeakerProfile.deserialize(json)
        assertNotNull(profile)
        assertEquals(5.0, profile!!.avgRMS, 0.001)
    }

    // ── EnrollmentResult sealed class ──

    @Test
    fun `SampleRecorded holds current and required`() {
        val result = WatchSpeakerEnrollment.EnrollmentResult.SampleRecorded(2, 3)
        assertEquals(2, result.current)
        assertEquals(3, result.required)
    }

    @Test
    fun `Complete holds total samples`() {
        val result = WatchSpeakerEnrollment.EnrollmentResult.Complete(3)
        assertEquals(3, result.totalSamples)
    }

    @Test
    fun `Error holds message`() {
        val result = WatchSpeakerEnrollment.EnrollmentResult.Error("test error")
        assertEquals("test error", result.message)
    }
}
