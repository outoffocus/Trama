package com.trama.wear

import android.app.Application
import com.trama.shared.data.DatabaseProvider

class TramaWearApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Pre-warm database on background thread so it's ready when UI needs it
        DatabaseProvider.preWarm(this)
    }
}
