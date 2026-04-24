package com.trama.wear.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.sync.MicCoordinator
import com.trama.wear.sync.WatchToPhoneSyncer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object WatchServiceController {

    private const val TAG = "WatchServiceController"
    private const val PREFS = "watch_sync_prefs"
    private const val KEY_USER_ENABLED = "user_enabled"
    private const val KEY_PHONE_ACTIVE = "phone_active"
    private const val START_COOLDOWN_MS = 15_000L
    private const val UNEXPECTED_STOP_RESTART_DELAY_MS = 30_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var lastStartAttemptMs = 0L
    @Volatile private var startInFlight = false
    @Volatile private var expectedStop = false
    private var restartJob: Job? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isPhoneActive = MutableStateFlow(false)
    val isPhoneActive: StateFlow<Boolean> = _isPhoneActive.asStateFlow()

    /**
     * Start keyword listening. Stops recording if active (modes are exclusive).
     * Sends PAUSE to phone to take over the mic.
     */
    fun start(context: Context) {
        expectedStop = false
        restartJob?.cancel()
        // Stop recording if active — modes are exclusive
        if (RecordingController.isRecording.value) {
            RecordingController.stopRecording(context)
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_USER_ENABLED, true)
            .putBoolean(KEY_PHONE_ACTIVE, false)
            .apply()

        startListenerService(context, reason = "user-start", bypassCooldown = true)
    }

    /**
     * Start recording. Stops keyword listener if active (modes are exclusive).
     */
    fun startRecording(context: Context) {
        // Stop keyword listener — modes are exclusive
        if (_isRunning.value) {
            expectedStop = true
            context.stopService(Intent(context, WatchKeywordListenerService::class.java))
            _isRunning.value = false
        }
        _isPhoneActive.value = false
        RecordingController.startRecording(context)
    }

    /**
     * Auto-resume after phone releases mic. Only starts if phone is not active.
     */
    fun resumeIfAllowed(context: Context) {
        if (isPhoneActive(context)) return
        if (!isUserEnabled(context)) return
        startListenerService(context, reason = "resume", bypassCooldown = true)
    }

    fun stop(context: Context) {
        expectedStop = true
        restartJob?.cancel()
        context.stopService(Intent(context, WatchKeywordListenerService::class.java))
        _isRunning.value = false
        startInFlight = false
    }

    /**
     * Stop keyword listener (user-initiated). Does NOT notify the phone —
     * use transferToPhone() to hand control to the phone explicitly.
     */
    fun stopByUser(context: Context) {
        stop(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_USER_ENABLED, false).apply()
    }

    /**
     * Stop everything because phone took over. Does NOT clear user_enabled
     * so watch can auto-resume when phone returns control.
     */
    fun stopByPhone(context: Context) {
        context.stopService(Intent(context, WatchKeywordListenerService::class.java))
        _isRunning.value = false
        startInFlight = false
        if (RecordingController.isRecording.value) {
            RecordingController.stopRecording(context)
        }
    }

    /**
     * Transfer active mode to phone. Stops everything locally.
     */
    fun transferToPhone(context: Context) {
        val wasRecording = RecordingController.isRecording.value

        // Stop everything locally
        if (_isRunning.value) {
            expectedStop = true
            context.stopService(Intent(context, WatchKeywordListenerService::class.java))
            _isRunning.value = false
        }
        if (wasRecording) {
            RecordingController.stopRecording(context)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_USER_ENABLED, false).apply()

        _isPhoneActive.value = true

        scope.launch {
            try {
                WatchToPhoneSyncer(
                    context = context.applicationContext,
                    repository = DatabaseProvider.getRepository(context.applicationContext)
                ).syncUnsentEntries()
            } catch (_: Exception) {
            }
            if (wasRecording) {
                MicCoordinator.sendStartRecording(context)
            } else {
                MicCoordinator.sendStartKeyword(context)
            }
        }
    }

    fun reclaimFromPhone(context: Context) {
        notifyPhoneInactive(context)
        start(context)
    }

    /** Called from service onDestroy to keep state in sync */
    fun notifyStopped() {
        _isRunning.value = false
        startInFlight = false
    }

    /**
     * Called when the listener service is destroyed. If this was not a known
     * user/phone transition, re-arm the listener while the user still wants it.
     */
    fun notifyServiceDestroyed(context: Context) {
        val shouldRestart = !expectedStop && isUserEnabled(context) && !isPhoneActive(context)
        notifyStopped()
        if (!shouldRestart) {
            expectedStop = false
            return
        }

        restartJob?.cancel()
        restartJob = scope.launch {
            Log.w(TAG, "Watch listener stopped unexpectedly; scheduling restart")
            MicCoordinator.sendWatchDebug(context.applicationContext, "escucha parada · reintentando")
            delay(UNEXPECTED_STOP_RESTART_DELAY_MS)
            if (isUserEnabled(context) && !isPhoneActive(context)) {
                if (isAppInForeground(context)) {
                    startListenerService(context, reason = "watchdog", bypassCooldown = true)
                } else {
                    Log.w(TAG, "Watchdog restart skipped: app is background/stopped")
                    MicCoordinator.sendWatchDebug(
                        context.applicationContext,
                        "escucha parada · app en segundo plano"
                    )
                    sendResumeToPhone(context.applicationContext)
                }
            }
        }
    }

    /** Called after WatchKeywordListenerService has successfully entered foreground. */
    fun notifyStarted() {
        expectedStop = false
        restartJob?.cancel()
        _isRunning.value = true
        _isPhoneActive.value = false
        startInFlight = false
    }

    fun isUserEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_USER_ENABLED, false)
    }

    fun isPhoneActive(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PHONE_ACTIVE, false)
    }

    /** Send RESUME to phone — used when watch stops involuntarily (battery, crash) */
    fun sendResumeToPhone(context: Context) {
        scope.launch { MicCoordinator.sendResume(context) }
    }

    fun notifyPhoneActive(context: Context) {
        expectedStop = true
        restartJob?.cancel()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PHONE_ACTIVE, true).apply()
        _isPhoneActive.value = true
    }

    fun notifyPhoneInactive(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PHONE_ACTIVE, false).apply()
        _isPhoneActive.value = false
    }

    private fun startListenerService(context: Context, reason: String, bypassCooldown: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (_isRunning.value || startInFlight) {
            Log.d(TAG, "Skipping listener start ($reason): already running/starting")
            return
        }
        if (!bypassCooldown && now - lastStartAttemptMs < START_COOLDOWN_MS) {
            Log.w(TAG, "Skipping listener start ($reason): cooldown")
            return
        }

        expectedStop = false
        restartJob?.cancel()
        lastStartAttemptMs = now
        startInFlight = true
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WatchKeywordListenerService::class.java)
            )
        } catch (e: Exception) {
            startInFlight = false
            _isRunning.value = false
            Log.w(TAG, "Failed to start watch listener ($reason)", e)
        }
    }

    private fun isAppInForeground(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        val packageName = context.packageName
        return manager.runningAppProcesses.orEmpty().any { process ->
            process.processName == packageName &&
                process.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
    }
}
