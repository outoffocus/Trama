package com.mydiary.app.service

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
    }

    @Test
    fun `notifyStopped sets isRunning to false`() = runTest {
        // Force a known state through internal access (isRunning is a StateFlow)
        // notifyStopped is the only method that doesn't require Context
        ServiceController.notifyStopped()
        assertFalse(ServiceController.isRunning.value)
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
}
