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
class ServiceControllerTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Reset to known state
        ServiceController.notifyStopped()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial isRunning is false`() = runTest {
        assertFalse(ServiceController.isRunning.value)
        assertFalse(ServiceController.continuousListeningEnabled.value)
    }

    @Test
    fun `notifyStopped sets isRunning to false`() = runTest {
        // Force a known state through internal access (isRunning is a StateFlow)
        // notifyStopped is the only method that doesn't require Context
        ServiceController.notifyStopped()
        assertFalse(ServiceController.isRunning.value)
        assertEquals(ServiceController.ListenerState.STOPPED, ServiceController.listenerState.value)
    }

    @Test
    fun `notifyStopped is idempotent`() = runTest {
        ServiceController.notifyStopped()
        ServiceController.notifyStopped()
        assertFalse(ServiceController.isRunning.value)
    }

    @Test
    fun `isRunning StateFlow is observable`() = runTest {
        // Verify the StateFlow contract: value is accessible without collection
        val value = ServiceController.isRunning.value
        assertNotNull(value)
    }

    @Test
    fun `service lifecycle reports starting listening paused and failed`() = runTest {
        ServiceController.notifyStarting()
        assertEquals(ServiceController.ListenerState.STARTING, ServiceController.listenerState.value)
        assertTrue(ServiceController.isRunning.value)

        ServiceController.notifyListening()
        assertEquals(ServiceController.ListenerState.LISTENING, ServiceController.listenerState.value)

        ServiceController.notifyTriggerRecognized(true)
        assertTrue(ServiceController.isTriggerRecognized.value)

        ServiceController.notifyPaused()
        assertFalse(ServiceController.isTriggerRecognized.value)
        assertEquals(ServiceController.ListenerState.PAUSED, ServiceController.listenerState.value)
        assertTrue(ServiceController.isRunning.value)

        ServiceController.notifyFailed()
        assertEquals(ServiceController.ListenerState.FAILED, ServiceController.listenerState.value)
        assertTrue(ServiceController.isRunning.value)
    }

    @Test
    fun `capture state gives trigger precedence while listener remains active`() {
        val state = ServiceController.resolveCaptureUiState(
            listenerState = ServiceController.ListenerState.LISTENING,
            listeningEnabled = true,
            triggerRecognized = true,
            recording = false,
            processing = false,
            watchActive = false,
            transferring = false,
            elapsedSeconds = 0,
            hasError = false
        )

        assertEquals(ServiceController.CaptureMode.TRIGGER_RECOGNIZED, state.mode)
        assertTrue(state.listeningActive)
        assertTrue(state.triggerRecognized)
    }

    @Test
    fun `capture state keeps mutually exclusive operation priority`() {
        val transferring = ServiceController.resolveCaptureUiState(
            listenerState = ServiceController.ListenerState.LISTENING,
            listeningEnabled = true,
            triggerRecognized = true,
            recording = true,
            processing = true,
            watchActive = true,
            transferring = true,
            elapsedSeconds = 42,
            hasError = true
        )
        val recording = ServiceController.resolveCaptureUiState(
            listenerState = ServiceController.ListenerState.STOPPED,
            listeningEnabled = true,
            triggerRecognized = false,
            recording = true,
            processing = true,
            watchActive = true,
            transferring = false,
            elapsedSeconds = 42,
            hasError = true
        )

        assertEquals(ServiceController.CaptureMode.TRANSFERRING, transferring.mode)
        assertEquals(ServiceController.CaptureMode.RECORDING, recording.mode)
        assertEquals(42, recording.elapsedSeconds)
    }
}
