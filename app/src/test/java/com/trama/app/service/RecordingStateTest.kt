package com.trama.app.service

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
class RecordingStateTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        RecordingState.reset()
        RecordingState.clearError()
        RecordingState.clearSaved()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Default values ──

    @Test
    fun `initial state has isRecording false`() = runTest {
        assertFalse(RecordingState.isRecording.value)
    }

    @Test
    fun `initial state has elapsedSeconds zero`() = runTest {
        assertEquals(0L, RecordingState.elapsedSeconds.value)
    }

    @Test
    fun `initial state has empty transcription`() = runTest {
        assertEquals("", RecordingState.transcription.value)
    }

    @Test
    fun `initial state has empty currentPartial`() = runTest {
        assertEquals("", RecordingState.currentPartial.value)
    }

    @Test
    fun `initial state has null savedRecordingId`() = runTest {
        assertNull(RecordingState.savedRecordingId.value)
    }

    @Test
    fun `initial state has null lastError`() = runTest {
        assertNull(RecordingState.lastError.value)
    }

    // ── update() ──

    @Test
    fun `update sets all four state fields`() = runTest {
        RecordingState.update(
            recording = true,
            elapsed = 42L,
            text = "hello world",
            partial = "hel"
        )

        assertTrue(RecordingState.isRecording.value)
        assertEquals(42L, RecordingState.elapsedSeconds.value)
        assertEquals("hello world", RecordingState.transcription.value)
        assertEquals("hel", RecordingState.currentPartial.value)
    }

    @Test
    fun `update can transition from recording to not recording`() = runTest {
        RecordingState.update(recording = true, elapsed = 10L, text = "a", partial = "b")
        assertTrue(RecordingState.isRecording.value)

        RecordingState.update(recording = false, elapsed = 15L, text = "a done", partial = "")
        assertFalse(RecordingState.isRecording.value)
        assertEquals(15L, RecordingState.elapsedSeconds.value)
        assertEquals("a done", RecordingState.transcription.value)
    }

    @Test
    fun `update overwrites previous values`() = runTest {
        RecordingState.update(recording = true, elapsed = 5L, text = "first", partial = "f")
        RecordingState.update(recording = true, elapsed = 10L, text = "second", partial = "s")

        assertEquals(10L, RecordingState.elapsedSeconds.value)
        assertEquals("second", RecordingState.transcription.value)
        assertEquals("s", RecordingState.currentPartial.value)
    }

    // ── reset() ──

    @Test
    fun `reset clears recording state to defaults`() = runTest {
        RecordingState.update(recording = true, elapsed = 99L, text = "text", partial = "par")
        RecordingState.reset()

        assertFalse(RecordingState.isRecording.value)
        assertEquals(0L, RecordingState.elapsedSeconds.value)
        assertEquals("", RecordingState.transcription.value)
        assertEquals("", RecordingState.currentPartial.value)
    }

    // ── notifySaved / clearSaved ──

    @Test
    fun `notifySaved sets savedRecordingId`() = runTest {
        RecordingState.notifySaved(123L)
        assertEquals(123L, RecordingState.savedRecordingId.value)
    }

    @Test
    fun `clearSaved resets savedRecordingId to null`() = runTest {
        RecordingState.notifySaved(456L)
        RecordingState.clearSaved()
        assertNull(RecordingState.savedRecordingId.value)
    }

    // ── notifyError / clearError ──

    @Test
    fun `notifyError sets lastError message`() = runTest {
        RecordingState.notifyError("mic failed")
        assertEquals("mic failed", RecordingState.lastError.value)
    }

    @Test
    fun `clearError resets lastError to null`() = runTest {
        RecordingState.notifyError("some error")
        RecordingState.clearError()
        assertNull(RecordingState.lastError.value)
    }

    @Test
    fun `notifyError overwrites previous error`() = runTest {
        RecordingState.notifyError("error 1")
        RecordingState.notifyError("error 2")
        assertEquals("error 2", RecordingState.lastError.value)
    }
}
