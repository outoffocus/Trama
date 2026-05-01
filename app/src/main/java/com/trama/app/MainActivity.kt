package com.trama.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.trama.app.service.ServiceController
import com.trama.app.ui.MainViewModel
import com.trama.app.ui.NavGraph
import com.trama.app.ui.Routes
import com.trama.app.ui.theme.TramaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

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
        enableEdgeToEdge()
        val mainViewModel = viewModel

        val shouldStartMicro = ServiceController.shouldBeRunning(this)
        if (hasAudioPermission()) {
            if (shouldStartMicro) {
                startListenerService()
            }
        } else if (shouldStartMicro) {
            requestPermissions()
        }
        maybeStartLocationService(mainViewModel)

        // Sync keywords to watch on every app open
        syncSettingsToWatch(mainViewModel)

        setContent {
            val themeMode by mainViewModel.themeMode.collectAsState(initial = 0)
            val darkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            val startDestination = when (intent?.getStringExtra("navigate_to")) {
                Routes.SUMMARY -> Routes.SUMMARY
                Routes.CHAT -> Routes.CHAT
                "calendar", Routes.CALENDAR -> Routes.CALENDAR
                else -> Routes.HOME
            }
            TramaTheme(darkTheme = darkTheme) {
                NavGraph(startDestination = startDestination)
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

    private fun maybeStartLocationService(mainViewModel: MainViewModel = viewModel) {
        CoroutineScope(Dispatchers.IO).launch {
            val enabled = mainViewModel.isLocationEnabled()
            val hasPermission = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (enabled && hasPermission && !ServiceController.isLocationRunning.value) {
                ServiceController.startLocationTracking(applicationContext)
            }
        }
    }

    private fun syncSettingsToWatch(mainViewModel: MainViewModel = viewModel) {
        mainViewModel.syncSettingsToWatch()
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
