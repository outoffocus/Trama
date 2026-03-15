package com.mydiary.wear.sync

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

class PhoneToWatchReceiver : WearableListenerService() {

    companion object {
        private const val TAG = "PhoneToWatchReceiver"
        private const val SETTINGS_PATH = "/mydiary/settings"
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path ?: return@forEach
                if (path == SETTINGS_PATH) {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val duration = dataMap.getInt("recording_duration", 10)
                    val keywords = dataMap.getString("keywords", "recordar,nota,destacar,pendiente")
                    Log.i(TAG, "Settings received: duration=$duration, keywords=$keywords")
                    // TODO: Apply received settings to watch preferences
                }
            }
        }
    }
}
