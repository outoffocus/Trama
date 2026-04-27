package com.trama.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared UI state for diary entries that are still being post-processed.
 */
object EntryProcessingState {

    enum class Backend { UNKNOWN, CLOUD, LOCAL }

    private val _processingBackends = MutableStateFlow<Map<Long, Backend>>(emptyMap())
    val processingBackends: StateFlow<Map<Long, Backend>> = _processingBackends.asStateFlow()
    private val _processingIds = MutableStateFlow<Set<Long>>(emptySet())
    val processingIds: StateFlow<Set<Long>> = _processingIds.asStateFlow()

    internal fun markProcessing(entryId: Long, backend: Backend = Backend.UNKNOWN) {
        _processingBackends.value = _processingBackends.value + (entryId to backend)
        _processingIds.value = _processingIds.value + entryId
    }

    internal fun updateBackend(entryId: Long, backend: Backend) {
        if (entryId in _processingBackends.value) {
            _processingBackends.value = _processingBackends.value + (entryId to backend)
        }
    }

    internal fun markFinished(entryId: Long) {
        _processingBackends.value = _processingBackends.value - entryId
        _processingIds.value = _processingIds.value - entryId
    }
}
