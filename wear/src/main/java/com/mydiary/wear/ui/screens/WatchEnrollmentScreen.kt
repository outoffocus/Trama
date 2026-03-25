package com.mydiary.wear.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.mydiary.wear.speech.WatchSpeakerEnrollment

@Composable
fun WatchEnrollmentScreen(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val enrollment = remember { WatchSpeakerEnrollment(context) }

    var currentSample by remember { mutableIntStateOf(enrollment.getEnrollmentCount()) }
    var isRecording by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    val isEnrolled = currentSample >= WatchSpeakerEnrollment.REQUIRED_SAMPLES

    val phraseIndex = currentSample.coerceAtMost(WatchSpeakerEnrollment.ENROLLMENT_PHRASES.size - 1)

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Registro de voz",
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (isEnrolled) {
            item {
                Text(
                    text = "Voz registrada correctamente",
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }

            item {
                Chip(
                    onClick = onComplete,
                    label = { Text("Listo") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Chip(
                    onClick = {
                        enrollment.resetEnrollment()
                        currentSample = 0
                        statusText = ""
                    },
                    label = { Text("Repetir registro") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            item {
                Text(
                    text = "Muestra ${currentSample + 1} de ${WatchSpeakerEnrollment.REQUIRED_SAMPLES}",
                    style = MaterialTheme.typography.caption1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }

            item {
                Text(
                    text = "Di en voz alta:",
                    style = MaterialTheme.typography.body2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }

            item {
                Text(
                    text = "\"${WatchSpeakerEnrollment.ENROLLMENT_PHRASES[phraseIndex]}\"",
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }

            item {
                Chip(
                    onClick = {
                        if (!isRecording) {
                            isRecording = true
                            statusText = "Escuchando..."
                            enrollment.recordSample { result ->
                                isRecording = false
                                when (result) {
                                    is WatchSpeakerEnrollment.EnrollmentResult.SampleRecorded -> {
                                        currentSample = result.current
                                        statusText = "Muestra grabada"
                                    }
                                    is WatchSpeakerEnrollment.EnrollmentResult.Complete -> {
                                        currentSample = result.totalSamples
                                        statusText = "Registro completo"
                                    }
                                    is WatchSpeakerEnrollment.EnrollmentResult.Error -> {
                                        statusText = result.message
                                    }
                                }
                            }
                        }
                    },
                    label = {
                        Text(if (isRecording) "Grabando..." else "Grabar")
                    },
                    enabled = !isRecording,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (statusText.isNotBlank()) {
                item {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
