package com.mydiary.wear.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.mydiary.wear.service.RecordingController
import com.mydiary.wear.service.WatchServiceController

class WatchMainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission handled */ }

    private var backDownTime = 0L
    private var longPressHandled = false
    private val longPressThresholdMs = 800L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestMicPermission()
        setContent {
            WatchTheme {
                WatchNavGraph()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event?.repeatCount == 0) {
                // First down event
                backDownTime = System.currentTimeMillis()
                longPressHandled = false
            } else if (!longPressHandled &&
                System.currentTimeMillis() - backDownTime >= longPressThresholdMs
            ) {
                // Long press detected
                longPressHandled = true
                onLongPressBack()
            }
            return true // consume all back key downs
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!longPressHandled) {
                // Short press — normal back
                super.onBackPressed()
            }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun onLongPressBack() {
        val vibrator = getSystemService(Vibrator::class.java)
        val wasRecording = RecordingController.isRecording.value

        if (wasRecording) {
            vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            RecordingController.stopRecording(this)
        } else {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 50, 80, 50, 80, 150), -1))
            if (!WatchServiceController.isRunning.value) WatchServiceController.start(this)
            RecordingController.startRecording(this)
        }

        RecordingController.requestToggle()
    }

    private fun requestMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
