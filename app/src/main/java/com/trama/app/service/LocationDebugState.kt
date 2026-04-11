package com.trama.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object LocationDebugState {

    private val _status = MutableStateFlow("sin datos")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _lastSample = MutableStateFlow("sin muestras")
    val lastSample: StateFlow<String> = _lastSample.asStateFlow()

    private val _candidate = MutableStateFlow("sin candidato")
    val candidate: StateFlow<String> = _candidate.asStateFlow()

    private val _activeDwell = MutableStateFlow("sin dwell activo")
    val activeDwell: StateFlow<String> = _activeDwell.asStateFlow()

    internal fun updateStatus(value: String) {
        _status.value = value
    }

    internal fun updateLastSample(value: String) {
        _lastSample.value = value
    }

    internal fun updateCandidate(value: String) {
        _candidate.value = value
    }

    internal fun updateActiveDwell(value: String) {
        _activeDwell.value = value
    }
}
