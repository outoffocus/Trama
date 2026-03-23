package com.mydiary.wear

import android.app.Application
import com.mydiary.wear.ui.DatabaseProvider

class MyDiaryWearApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Pre-warm database on background thread so it's ready when UI needs it
        DatabaseProvider.preWarm(this)
    }
}
