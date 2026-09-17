package com.trama.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trama.app.capture.CalendarEntryActions
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

@HiltViewModel
class CalendarActionsViewModel @Inject constructor(
    private val actions: CalendarEntryActions
) : ViewModel() {
    /** Finish a submitted mutation even if the composable stops waiting for its result. */
    suspend fun <T> run(block: suspend CalendarEntryActions.() -> T): T =
        viewModelScope.async(Dispatchers.IO) { actions.block() }.await()
}
