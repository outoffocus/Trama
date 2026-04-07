package com.trama.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared UI state for diary entries that are still being post-processed.
 */
object EntryProcessingState {

    private val _processingIds = MutableStateFlow<Set<Long>>(emptySet())
    val processingIds: StateFlow<Set<Long>> = _processingIds.asStateFlow()

    internal fun markProcessing(entryId: Long) {
        _processingIds.value = _processingIds.value + entryId
    }

    internal fun markFinished(entryId: Long) {
        _processingIds.value = _processingIds.value - entryId
    }
}
