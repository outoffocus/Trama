package com.trama.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (hasAudioPermission()) {
            startListenerService()
        } else {
            requestPermissions()
        }

        // Sync keywords to watch on every app open
        syncSettingsToWatch()

        setContent {
            TramaTheme {
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
