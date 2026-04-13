package com.trama.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.trama.app.service.ServiceController
import com.trama.app.sync.SettingsSyncer
import com.trama.app.ui.NavGraph
import com.trama.app.ui.SettingsDataStore
import com.trama.app.ui.theme.TramaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Start service once RECORD_AUDIO is granted
        if (results[Manifest.permission.RECORD_AUDIO] == true) {
            startListenerService()
        }
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            maybeStartLocationService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val shouldStartMicro = ServiceController.shouldBeRunning(this)
        if (hasAudioPermission()) {
            if (shouldStartMicro) {
                startListenerService()
            }
        } else if (shouldStartMicro) {
            requestPermissions()
        }
        maybeStartLocationService()

        // Sync keywords to watch on every app open
        syncSettingsToWatch()

        setContent {
            val settings = remember { SettingsDataStore(applicationContext) }
            val themeMode by settings.themeMode.collectAsState(initial = 0)
            val darkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            TramaTheme(darkTheme = darkTheme) {
                NavGraph()
            }
        }
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startListenerService() {
        if (!ServiceController.isRunning.value) {
            ServiceController.start(this)
        }
    }

    private fun maybeStartLocationService() {
        CoroutineScope(Dispatchers.IO).launch {
            val settings = SettingsDataStore(applicationContext)
            val enabled = settings.locationEnabled.first()
            val hasPermission = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (enabled && hasPermission && !ServiceController.isLocationRunning.value) {
                ServiceController.startLocationTracking(applicationContext)
            }
        }
    }

    private fun syncSettingsToWatch() {
        CoroutineScope(Dispatchers.IO).launch {
            val settings = SettingsDataStore(applicationContext)
            val kwList = settings.customKeywords.first()
            SettingsSyncer(applicationContext).syncSettings(kwList)
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}
