package com.trama.app

/**
 * Centralized notification channel IDs and notification IDs for the phone app.
 *
 * All notification-related constants live here to avoid magic strings/numbers
 * scattered across services and workers.
 */
object NotificationConfig {

    // ── Channel IDs ──

    /** Foreground notification for the keyword listener service. */
    const val CHANNEL_LISTENER = "trama_listener"

    /** Notifications shown when a new diary entry is captured. */
    const val CHANNEL_NEW_ENTRY = "trama_new_entry"

    /** Foreground notification for the recording service. */
    const val CHANNEL_RECORDING = "trama_recording"

    /** Notification for daily summary results. */
    const val CHANNEL_DAILY_SUMMARY = "trama_daily_summary"

    // ── Notification IDs ──

    /** Foreground notification ID for KeywordListenerService. */
    const val ID_LISTENER = 1

    /** Foreground notification ID for RecordingService. */
    const val ID_RECORDING = 2

    /** Notification ID for DailySummaryWorker. */
    const val ID_DAILY_SUMMARY = 2000
}
