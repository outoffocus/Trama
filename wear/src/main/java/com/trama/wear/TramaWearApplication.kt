package com.trama.wear

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.trama.wear.service.WatchServiceController

class TramaWearApplication : Application() {
    private var startedActivities = 0
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val foregroundNotify = Runnable {
        WatchServiceController.notifyAppForeground(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities += 1
                if (startedActivities == 1) {
                    mainHandler.removeCallbacks(foregroundNotify)
                    mainHandler.postDelayed(foregroundNotify, STARTUP_AUTO_RESUME_DELAY_MS)
                }
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0) {
                    mainHandler.removeCallbacks(foregroundNotify)
                    WatchServiceController.notifyAppBackground()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private companion object {
        const val STARTUP_AUTO_RESUME_DELAY_MS = 2_000L
    }
}
