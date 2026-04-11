package com.trama.wear.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.Wearable
import com.trama.shared.data.DatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Composable
fun WatchSettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var syncStatus by remember { mutableStateOf("Automática") }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Ajustes",
                style = MaterialTheme.typography.title3
            )
        }

        item {
            Chip(
                onClick = {
                    syncStatus = "Sincronizando..."
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val db = DatabaseProvider.getDatabase(context)
                                db.clearAllTables()
                            }

                            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
                            if (nodes.isEmpty()) {
                                syncStatus = "Sin conexión"
                                return@launch
                            }
                            for (node in nodes) {
                                Wearable.getMessageClient(context)
                                    .sendMessage(node.id, "/trama/request-full-sync", byteArrayOf())
                                    .await()
                            }
                            syncStatus = "✓ Sincronizado"
                        } catch (e: Exception) {
                            syncStatus = "Error"
                        }
                    }
                },
                label = { Text("Forzar sincronización") },
                secondaryLabel = { Text(syncStatus) },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
