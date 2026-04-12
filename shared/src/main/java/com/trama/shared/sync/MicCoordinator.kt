package com.trama.shared.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * Sends mic coordination messages via Wearable MessageClient.
 * Used by both phone and watch to coordinate microphone access.
 */
object MicCoordinator {

    private const val TAG = "MicCoordinator"
    const val MIC_PATH = "/trama/mic"
    const val WATCH_DEBUG_PATH = "/trama/watch-debug"
    const val CMD_PAUSE = "PAUSE"
    const val CMD_RESUME = "RESUME"
    const val CMD_START_KEYWORD = "START_KEYWORD"
    const val CMD_START_RECORDING = "START_RECORDING"

    /** Tell all connected nodes to pause their mic */
    suspend fun sendPause(context: Context) {
        sendCommand(context, CMD_PAUSE)
    }

    /** Tell all connected nodes they can resume their mic */
    suspend fun sendResume(context: Context) {
        sendCommand(context, CMD_RESUME)
    }

    /** Tell the other device to start keyword listening */
    suspend fun sendStartKeyword(context: Context) {
        sendCommand(context, CMD_START_KEYWORD)
    }

    /** Tell the other device to start continuous recording */
    suspend fun sendStartRecording(context: Context) {
        sendCommand(context, CMD_START_RECORDING)
    }

    /**
     * Send a watch debug event to the phone for display in the ASR diagnostic panel.
     * Format: "status|triggerText" (triggerText may be empty).
     * Fire-and-forget — errors are silently swallowed to avoid impacting capture.
     */
    suspend fun sendWatchDebug(context: Context, status: String, triggerText: String = "") {
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            if (nodes.isEmpty()) return
            val payload = "$status|$triggerText"
            for (node in nodes) {
                Wearable.getMessageClient(context)
                    .sendMessage(node.id, WATCH_DEBUG_PATH, payload.toByteArray())
                    .await()
            }
        } catch (e: Exception) {
            Log.d(TAG, "watch-debug send failed: ${e.message}")
        }
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
