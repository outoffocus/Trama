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
class WatchServiceControllerTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        WatchServiceController.notifyStopped()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial isRunning is false`() = runTest {
        assertFalse(WatchServiceController.isRunning.value)
    }

    @Test
    fun `notifyStopped sets isRunning to false`() = runTest {
        WatchServiceController.notifyStopped()
        assertFalse(WatchServiceController.isRunning.value)
    }

    @Test
    fun `notifyStopped is idempotent`() = runTest {
        WatchServiceController.notifyStopped()
        WatchServiceController.notifyStopped()
        assertFalse(WatchServiceController.isRunning.value)
    }

    @Test
    fun `isRunning StateFlow is observable without collection`() = runTest {
        val value = WatchServiceController.isRunning.value
        assertNotNull(value)
    }
}
