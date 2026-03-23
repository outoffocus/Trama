package com.mydiary.wear.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * Sends mic coordination messages to the phone via Wearable MessageClient.
 * When watch is listening, phone should pause.
 */
object MicCoordinator {

    private const val TAG = "WatchMicCoordinator"
    const val MIC_PATH = "/mydiary/mic"
    const val CMD_PAUSE = "PAUSE"
    const val CMD_RESUME = "RESUME"

    suspend fun sendPause(context: Context) {
        sendCommand(context, CMD_PAUSE)
    }

    suspend fun sendResume(context: Context) {
        sendCommand(context, CMD_RESUME)
    }

    private suspend fun sendCommand(context: Context, command: String) {
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            if (nodes.isEmpty()) {
                Log.d(TAG, "No connected nodes, skipping $command")
                return
            }
            val data = command.toByteArray()
            for (node in nodes) {
                Wearable.getMessageClient(context)
                    .sendMessage(node.id, MIC_PATH, data)
                    .await()
                Log.i(TAG, "Sent $command to ${node.displayName}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send $command", e)
        }
    }
}
