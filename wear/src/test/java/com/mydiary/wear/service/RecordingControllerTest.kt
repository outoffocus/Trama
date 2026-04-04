package com.mydiary.wear.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingControllerTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        RecordingController.reset()
        RecordingController.clearSaved()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Default values ──

    @Test
    fun `initial isRecording is false`() = runTest {
        assertFalse(RecordingController.isRecording.value)
    }

    @Test
    fun `initial elapsedSeconds is zero`() = runTest {
        assertEquals(0L, RecordingController.elapsedSeconds.value)
    }

    @Test
    fun `initial transcription is empty`() = runTest {
        assertEquals("", RecordingController.transcription.value)
    }

    @Test
    fun `initial currentPartial is empty`() = runTest {
        assertEquals("", RecordingController.currentPartial.value)
    }

    @Test
    fun `initial savedRecordingId is null`() = runTest {
        assertNull(RecordingController.savedRecordingId.value)
    }

    // ── update() ──

    @Test
    fun `update sets all state fields`() = runTest {
        RecordingController.update(
            recording = true,
            elapsed = 30L,
            text = "recording text",
            partial = "rec"
        )

        assertTrue(RecordingController.isRecording.value)
        assertEquals(30L, RecordingController.elapsedSeconds.value)
        assertEquals("recording text", RecordingController.transcription.value)
        assertEquals("rec", RecordingController.currentPartial.value)
    }

    @Test
    fun `update can stop recording`() = runTest {
        RecordingController.update(recording = true, elapsed = 10L, text = "a", partial = "b")
        RecordingController.update(recording = false, elapsed = 20L, text = "done", partial = "")

        assertFalse(RecordingController.isRecording.value)
        assertEquals(20L, RecordingController.elapsedSeconds.value)
        assertEquals("done", RecordingController.transcription.value)
        assertEquals("", RecordingController.currentPartial.value)
    }

    @Test
    fun `multiple updates accumulate elapsed time`() = runTest {
        RecordingController.update(recording = true, elapsed = 5L, text = "", partial = "")
        RecordingController.update(recording = true, elapsed = 10L, text = "", partial = "")
        RecordingController.update(recording = true, elapsed = 15L, text = "", partial = "")

        assertEquals(15L, RecordingController.elapsedSeconds.value)
    }

    // ── reset() ──

    @Test
    fun `reset clears all recording state`() = runTest {
        RecordingController.update(recording = true, elapsed = 50L, text = "hello", partial = "he")
        RecordingController.reset()

        assertFalse(RecordingController.isRecording.value)
        assertEquals(0L, RecordingController.elapsedSeconds.value)
        assertEquals("", RecordingController.transcription.value)
        assertEquals("", RecordingController.currentPartial.value)
    }

    @Test
    fun `reset does not clear savedRecordingId`() = runTest {
        RecordingController.notifySaved(99L)
        RecordingController.reset()

        // reset only clears the 4 recording fields, not savedRecordingId
        assertEquals(99L, RecordingController.savedRecordingId.value)
    }

    // ── notifySaved / clearSaved ──

    @Test
    fun `notifySaved sets savedRecordingId`() = runTest {
        RecordingController.notifySaved(42L)
        assertEquals(42L, RecordingController.savedRecordingId.value)
    }

    @Test
    fun `clearSaved resets savedRecordingId to null`() = runTest {
        RecordingController.notifySaved(42L)
        RecordingController.clearSaved()
        assertNull(RecordingController.savedRecordingId.value)
    }

    @Test
    fun `notifySaved overwrites previous value`() = runTest {
        RecordingController.notifySaved(1L)
        RecordingController.notifySaved(2L)
        assertEquals(2L, RecordingController.savedRecordingId.value)
    }

    // ── requestToggle ──

    @Test
    fun `requestToggle changes toggleRequest value`() = runTest {
        val before = RecordingController.toggleRequest.value
        RecordingController.requestToggle()
        val after = RecordingController.toggleRequest.value

        assertTrue(after > before)
    }

    @Test
    fun `requestToggle produces monotonically increasing values`() = runTest {
        RecordingController.requestToggle()
        val first = RecordingController.toggleRequest.value
        // Small delay to ensure System.currentTimeMillis() advances
        Thread.sleep(5)
        RecordingController.requestToggle()
        val second = RecordingController.toggleRequest.value

        assertTrue("Second toggle ($second) should be >= first ($first)", second >= first)
    }
}
