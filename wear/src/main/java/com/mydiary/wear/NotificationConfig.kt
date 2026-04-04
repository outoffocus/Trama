package com.mydiary.wear

/**
 * Centralized notification channel IDs and notification IDs for the wear app.
 *
 * All notification-related constants live here to avoid magic strings/numbers
 * scattered across services.
 */
object NotificationConfig {

    // ── Channel IDs ──

    /** Foreground notification for the watch keyword listener service. */
    const val CHANNEL_WATCH_LISTENER = "mydiary_watch_listener"

    /** Foreground notification for the watch recording service. */
    const val CHANNEL_WATCH_RECORDING = "mydiary_watch_recording"

    // ── Notification IDs ──

    /** Foreground notification ID for WatchKeywordListenerService. */
    const val ID_WATCH_LISTENER = 1

    /** Foreground notification ID for WatchRecordingService. */
    const val ID_WATCH_RECORDING = 3
}
