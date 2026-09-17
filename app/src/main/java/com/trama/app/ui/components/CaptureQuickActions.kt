package com.trama.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.trama.app.service.RecordingState
import com.trama.app.service.ServiceController
import com.trama.app.ui.theme.LocalTramaColors
import kotlinx.coroutines.launch

/** Persistent capture controls shared by the primary Acciones and Recuerdos destinations. */
@Composable
fun CaptureQuickActions() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by ServiceController.captureState.collectAsState()
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) ServiceController.start(context)
        else Toast.makeText(context, "Necesito permiso de micrófono para escuchar", Toast.LENGTH_SHORT).show()
    }
    val recordingPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) ServiceController.startRecording(context)
        else Toast.makeText(context, "Necesito permiso de micrófono para grabar", Toast.LENGTH_SHORT).show()
    }

    fun hasMicPermission() = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    val recordingBlocked = state.processing || state.transferring || state.watchActive
    val listeningBlocked = state.recording || state.processing || state.transferring || state.watchActive
    val deviceEnabled = (!state.recording && !state.processing && !state.transferring) ||
        (state.watchActive && !state.transferring)
    val elapsed = "%d:%02d".format(state.elapsedSeconds / 60, state.elapsedSeconds % 60)
    val colors = LocalTramaColors.current

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CaptureFab(
            label = if (state.watchActive) "Recuperar escucha en el teléfono" else "Pasar escucha al reloj",
            icon = Icons.Default.Watch,
            enabled = deviceEnabled,
            selected = state.watchActive,
            loading = state.transferring,
            accent = colors.watch,
            onClick = {
                if (state.watchActive) {
                    ServiceController.reclaimFromWatch(context)
                } else {
                    ServiceController.transferToWatch(context) { success ->
                        if (!success) scope.launch {
                            Toast.makeText(context, "No se ha podido conectar con el reloj", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
        CaptureFab(
            label = when {
                state.recording -> "Detener reunión · $elapsed"
                state.processing -> "Procesando reunión"
                else -> "Grabar reunión"
            },
            icon = if (state.recording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
            enabled = state.recording || !recordingBlocked,
            selected = state.recording,
            loading = state.processing,
            accent = colors.red,
            onClick = {
                if (state.recording) RecordingState.stopRecording(context)
                else if (hasMicPermission()) ServiceController.startRecording(context)
                else recordingPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        )
        CaptureFab(
            label = if (state.listeningActive) "Pausar escucha continua" else "Activar escucha continua",
            icon = if (state.listeningActive) Icons.Default.Mic else Icons.Default.MicOff,
            enabled = !listeningBlocked,
            selected = state.listeningActive && !state.watchActive && !state.recording,
            accent = colors.amber,
            onClick = {
                if (state.listeningActive) ServiceController.stop(context, reason = "primary_navigation")
                else if (hasMicPermission()) ServiceController.start(context)
                else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureFab(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    loading: Boolean = false
) {
    val colors = LocalTramaColors.current
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState()
    ) {
        Surface(
            modifier = Modifier.size(54.dp).semantics {
                contentDescription = label
                stateDescription = when {
                    loading -> "En curso"
                    !enabled -> "No disponible durante la operación actual"
                    selected -> "Activo"
                    else -> "Inactivo"
                }
            },
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            color = if (selected) accent else colors.surface2,
            shadowElevation = 10.dp,
            border = BorderStroke(
                width = if (selected) 0.dp else 1.dp,
                color = if (selected) Color.Transparent else accent.copy(alpha = 0.4f)
            )
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(23.dp),
                        color = accent,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = when {
                            selected -> Color.White
                            enabled -> accent
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
