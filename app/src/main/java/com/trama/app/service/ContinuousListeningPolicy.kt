package com.trama.app.service

/** Product contract for features that must remain independent from continuous audio. */
object ContinuousListeningPolicy {

    data class Availability(
        val continuousAudio: Boolean,
        val ambientContext: Boolean,
        val manualRecording: Boolean = true,
        val calendarSync: Boolean = true,
        val locationTrace: Boolean = true
    )

    fun availability(
        continuousListeningEnabled: Boolean,
        ambientContextConfigured: Boolean
    ): Availability = Availability(
        continuousAudio = continuousListeningEnabled,
        ambientContext = continuousListeningEnabled && ambientContextConfigured
    )

    fun shouldRequestBootReactivation(
        continuousListeningEnabled: Boolean,
        reminderEnabled: Boolean
    ): Boolean = continuousListeningEnabled && reminderEnabled
}
