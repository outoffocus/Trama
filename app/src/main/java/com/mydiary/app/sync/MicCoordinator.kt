package com.mydiary.app.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * Sends mic coordination messages to the watch via Wearable MessageClient.
 * Phone has priority: when phone is listening, watch pauses.
 */
object MicCoordinator {

    private const val TAG = "MicCoordinator"
    const val MIC_PATH = "/mydiary/mic"
    const val CMD_PAUSE = "PAUSE"
    const val CMD_RESUME = "RESUME"

    /** Tell all connected watches to pause their mic */
    suspend fun sendPause(context: Context) {
        sendCommand(context, CMD_PAUSE)
    }

    /** Tell all connected watches they can resume their mic */
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
