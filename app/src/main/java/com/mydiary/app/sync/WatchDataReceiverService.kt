package com.mydiary.app.sync

import android.util.Log
import androidx.room.Room
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.mydiary.shared.data.DiaryDatabase
import com.mydiary.shared.data.DiaryRepository
import com.mydiary.shared.model.SyncPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class WatchDataReceiverService : WearableListenerService() {

    companion object {
        private const val TAG = "WatchDataReceiver"
        private const val SYNC_PATH = "/mydiary/sync"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val db = Room.databaseBuilder(
            applicationContext,
            DiaryDatabase::class.java,
            "mydiary-database"
        ).build()
        val repository = DiaryRepository(db.diaryDao())

        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path ?: return@forEach
                if (path == SYNC_PATH) {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val json = dataMap.getString("payload") ?: return@forEach

                    scope.launch {
                        try {
                            val payload = Json.decodeFromString<SyncPayload>(json)
                            payload.entries.forEach { syncEntry ->
                                repository.insert(syncEntry.toDiaryEntry())
                            }
                            Log.i(TAG, "Received ${payload.entries.size} entries from watch")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to process sync data", e)
                        }
                    }
                }
            }
        }
    }
}
