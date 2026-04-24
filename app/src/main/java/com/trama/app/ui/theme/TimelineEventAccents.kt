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

// Reduced to the 5 Trama semantic accents.
val TimelineAccentPalette = listOf(
    TimelineAccentOption("Ambar", TramaAmber),
    TimelineAccentOption("Turquesa", TramaTeal),
    TimelineAccentOption("Rojo", TramaRed),
    TimelineAccentOption("Dorado", TramaWarn),
    TimelineAccentOption("Azul", TramaWatch),
)

fun timelineAccentColor(index: Int): Color {
    val normalized = index.mod(TimelineAccentPalette.size)
    return TimelineAccentPalette[normalized].color
}
