package com.trama.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trama.app.summary.DeletionFeedbackStore.Reason

private data class ReasonOption(val reason: Reason, val emoji: String, val label: String)

private val OPTIONS = listOf(
    ReasonOption(Reason.NOISE, "🚫", "No era una tarea / era ruido"),
    ReasonOption(Reason.NOT_FOR_ME, "👤", "No es para mí"),
    ReasonOption(Reason.BAD_TRANSCRIPTION, "✏️", "Texto mal transcrito"),
    ReasonOption(Reason.DUPLICATE_OR_DONE, "🔁", "Duplicada / ya hecha"),
    ReasonOption(Reason.NO_LONGER_APPLIES, "⏭️", "Ya no aplica"),
    ReasonOption(Reason.OTHER, "❓", "Otro motivo (no se usará para filtrar)"),
)

@Composable
fun DeleteReasonDialog(
    entryCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (Reason) -> Unit
) {
    var selected by remember { mutableStateOf<Reason?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (entryCount > 1) "Eliminar $entryCount elementos" else "Eliminar entrada"
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "¿Por qué la eliminas? Elige la causa real para ajustar futuras sugerencias.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OPTIONS.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected == option.reason,
                                onClick = { selected = option.reason }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == option.reason,
                            onClick = { selected = option.reason }
                        )
                        Text(
                            text = "${option.emoji}  ${option.label}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let(onConfirm) },
                enabled = selected != null,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Eliminar",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
