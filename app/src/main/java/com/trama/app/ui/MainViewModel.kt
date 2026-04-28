package com.trama.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trama.app.sync.SettingsSyncer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {

    val themeMode: Flow<Int> = settings.themeMode

    suspend fun isLocationEnabled(): Boolean = withContext(Dispatchers.IO) {
        settings.locationEnabled.first()
    }

    fun syncSettingsToWatch() {
        viewModelScope.launch(Dispatchers.IO) {
            val customKeywords = settings.customKeywords.first()
            val intentPatterns = settings.intentPatterns.first()
            SettingsSyncer(appContext).syncPatterns(intentPatterns, customKeywords)
        }
    }
}
