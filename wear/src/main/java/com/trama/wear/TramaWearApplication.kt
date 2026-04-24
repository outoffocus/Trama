package com.trama.wear

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.trama.shared.data.DatabaseProvider
import com.trama.wear.service.WatchServiceController

class TramaWearApplication : Application() {
    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
        // Pre-warm database on background thread so it's ready when UI needs it
        DatabaseProvider.preWarm(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities += 1
                if (startedActivities == 1) {
                    WatchServiceController.notifyAppForeground(applicationContext)
                }
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0) {
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
}
