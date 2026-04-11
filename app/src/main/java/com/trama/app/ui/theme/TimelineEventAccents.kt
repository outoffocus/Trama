package com.trama.app.ui.theme

import androidx.compose.ui.graphics.Color

data class TimelineAccentOption(
    val name: String,
    val color: Color
)

data class TimelineAccentConfig(
    val pending: Color,
    val completed: Color,
    val recording: Color,
    val place: Color,
    val calendar: Color
)

val TimelineAccentPalette = listOf(
    TimelineAccentOption("Azul", CategoryColors[0]),
    TimelineAccentOption("Coral", CategoryColors[1]),
    TimelineAccentOption("Verde", CategoryColors[2]),
    TimelineAccentOption("Ambar", CategoryColors[3]),
    TimelineAccentOption("Lavanda", CategoryColors[4]),
    TimelineAccentOption("Turquesa", CategoryColors[5]),
    TimelineAccentOption("Rosa", CategoryColors[6]),
    TimelineAccentOption("Melocoton", CategoryColors[7]),
)

fun timelineAccentColor(index: Int): Color {
    val normalized = index.mod(TimelineAccentPalette.size)
    return TimelineAccentPalette[normalized].color
}
