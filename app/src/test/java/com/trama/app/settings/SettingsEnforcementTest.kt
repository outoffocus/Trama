package com.trama.app.settings

import com.trama.app.ui.SettingsDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests to ensure that critical settings have consistent defaults
 * and valid values across the codebase.
 *
 * These tests verify that settings defined in SettingsDataStore:
 * 1. Have sensible default values
 * 2. Have consistent constants that won't cause runtime errors
 * 3. Follow expected patterns for different recording modes
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsEnforcementTest {

    // ── Recording Duration (Manual Recording Limit in Minutes) ──

    @Test
    fun `recording duration default is 60 minutes`() {
        assertEquals(60, SettingsDataStore.DEFAULT_DURATION)
    }

    @Test
    fun `recording duration default is reasonable for manual recording`() {
        // Manual recording should allow at least 5 minutes for meetings/conversations
        assertTrue(SettingsDataStore.DEFAULT_DURATION >= 5)
        // But shouldn't be excessive (max 120 minutes = 2 hours is reasonable)
        assertTrue(SettingsDataStore.DEFAULT_DURATION <= 120)
    }

    // ── Context Pre/Post Roll (Continuous Listening in Seconds) ──

    @Test
    fun `context pre-roll default is in seconds`() {
        assertEquals(5, SettingsDataStore.DEFAULT_CONTEXT_PRE_ROLL)
    }

    @Test
    fun `context post-roll default is in seconds`() {
        assertEquals(10, SettingsDataStore.DEFAULT_CONTEXT_POST_ROLL)
    }

    // ── Settings Consistency ──

    @Test
    fun `all duration values are positive`() {
        assertTrue(SettingsDataStore.DEFAULT_DURATION > 0)
        assertTrue(SettingsDataStore.DEFAULT_CONTEXT_PRE_ROLL > 0)
        assertTrue(SettingsDataStore.DEFAULT_CONTEXT_POST_ROLL > 0)
    }

    @Test
    fun `all hour values are valid (0-23)`() {
        assertTrue(SettingsDataStore.DEFAULT_SUMMARY_HOUR in 0..23)
        assertTrue(SettingsDataStore.DEFAULT_BACKUP_HOUR in 0..23)
        assertTrue(SettingsDataStore.DEFAULT_AMBIENT_CONTEXT_START_HOUR in 0..23)
        assertTrue(SettingsDataStore.DEFAULT_AMBIENT_CONTEXT_END_HOUR in 0..23)
    }

    @Test
    fun `all location defaults are positive`() {
        assertTrue(SettingsDataStore.DEFAULT_LOCATION_INTERVAL_MINUTES > 0)
        assertTrue(SettingsDataStore.DEFAULT_LOCATION_DWELL_MINUTES > 0)
        assertTrue(SettingsDataStore.DEFAULT_LOCATION_ENTRY_RADIUS_METERS > 0)
        assertTrue(SettingsDataStore.DEFAULT_LOCATION_EXIT_RADIUS_METERS > 0)
    }

    @Test
    fun `location exit radius is larger than entry radius`() {
        // This prevents rapid entry/exit oscillation
        assertTrue(
            SettingsDataStore.DEFAULT_LOCATION_EXIT_RADIUS_METERS >
            SettingsDataStore.DEFAULT_LOCATION_ENTRY_RADIUS_METERS
        )
    }

    @Test
    fun `recording modes use different time units`() {
        // Manual recording duration: MINUTES (for long recordings)
        // Context roll: SECONDS (for short clips from continuous listening)
        // This test documents the intentional difference to prevent confusion
        assertTrue(SettingsDataStore.DEFAULT_DURATION >= 5) // minutes
        assertTrue(SettingsDataStore.DEFAULT_CONTEXT_PRE_ROLL <= 30) // seconds
        assertTrue(SettingsDataStore.DEFAULT_CONTEXT_POST_ROLL <= 30) // seconds
    }

    @Test
    fun `timeline color defaults are non-negative`() {
        assertTrue(SettingsDataStore.DEFAULT_TIMELINE_COLOR_PENDING >= 0)
        assertTrue(SettingsDataStore.DEFAULT_TIMELINE_COLOR_COMPLETED >= 0)
        assertTrue(SettingsDataStore.DEFAULT_TIMELINE_COLOR_RECORDING >= 0)
        assertTrue(SettingsDataStore.DEFAULT_TIMELINE_COLOR_PLACE >= 0)
        assertTrue(SettingsDataStore.DEFAULT_TIMELINE_COLOR_CALENDAR >= 0)
    }

    @Test
    fun `theme mode default is valid`() {
        // Theme modes: 0=system, 1=light, 2=dark
        assertTrue(SettingsDataStore.DEFAULT_THEME_MODE in 0..2)
    }

    @Test
    fun `advanced options are hidden by default`() {
        assertFalse(SettingsDataStore.DEFAULT_SHOW_ADVANCED_OPTIONS)
    }
}
