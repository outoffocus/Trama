package com.trama.app.ui.screens

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trama.shared.data.DiaryRepository
import com.trama.shared.model.EntryContentKind
import com.trama.app.service.EntryProcessingState
import com.trama.app.speech.PersonalDictionary
import com.trama.app.summary.ActionItemProcessor
import com.trama.app.summary.GemmaClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class EntryEditorViewModel @Inject constructor(
    private val repository: DiaryRepository,
    private val savedState: SavedStateHandle,
    private val dictionary: PersonalDictionary,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {
    val editing = savedState.getStateFlow("entryEditing", false)
    val draft = savedState.getStateFlow("entryDraft", "")
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun start(text: String) {
        savedState["entryDraft"] = text
        savedState["entryEditing"] = true
        _error.value = null
    }
    fun change(text: String) {
        if (!_saving.value) savedState["entryDraft"] = text
        _error.value = null
    }
    fun cancel() {
        if (!_saving.value) savedState["entryEditing"] = false
    }
    fun save(id: Long, requireLocalModel: Boolean = false) {
        if (_saving.value || draft.value.isBlank()) return
        if (requireLocalModel && !GemmaClient.isModelAvailable(appContext)) {
            _error.value = "El modelo local no está disponible. Instálalo o selecciónalo en Ajustes."
            return
        }
        val text = draft.value.trim()
        _saving.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val entryBefore = checkNotNull(repository.getByIdOnce(id)) { "Entry no longer exists" }
                val reanalyzingExistingAction = requireLocalModel &&
                    entryBefore.contentKind != EntryContentKind.MEMORY
                val original = entryBefore.text
                if (!reanalyzingExistingAction) {
                    repository.updateText(id, text)
                    runCatching { dictionary.learnFromEdit(original, text) }
                }
                val contentKind = entryBefore.contentKind
                // A normal edit of an action is authoritative. Explicit local
                // reanalysis is allowed and updates that same row only on success.
                if (contentKind != EntryContentKind.MEMORY && !requireLocalModel) {
                    savedState["entryEditing"] = false
                    return@launch
                }
                EntryProcessingState.markProcessing(id)
                if (requireLocalModel) {
                    try {
                        val created = ActionItemProcessor(appContext)
                            .processWithLocalModelOnly(id, text, repository)
                        if (created) {
                            savedState["entryEditing"] = false
                        } else {
                            _error.value = "El modelo local no ha encontrado una tarea clara. Ajusta la explicación y vuelve a intentarlo."
                        }
                    } finally {
                        EntryProcessingState.markFinished(id)
                    }
                    return@launch
                }
                savedState["entryEditing"] = false
                viewModelScope.launch {
                    try {
                        ActionItemProcessor(appContext).process(id, text, repository)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // The corrected source text remains durable and editable.
                    } finally {
                        EntryProcessingState.markFinished(id)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _error.value = "No se ha podido guardar. Tu edición sigue aquí."
            } finally {
                _saving.value = false
            }
        }
    }
}
