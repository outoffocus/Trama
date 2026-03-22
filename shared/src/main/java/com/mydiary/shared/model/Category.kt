package com.mydiary.shared.model

data class CategoryInfo(
    val id: String,
    val label: String,
    val colorHex: String // ARGB hex string e.g. "FFE57373"
) {
    companion object {
        val PRESET_COLORS = listOf(
            "FFE57373", // Red
            "FFFF8A65", // Deep Orange
            "FFFFB74D", // Orange
            "FFFFD54F", // Yellow
            "FFDCE775", // Lime
            "FF81C784", // Green
            "FF4DB6AC", // Teal
            "FF4FC3F7", // Light Blue
            "FF7986CB", // Indigo
            "FFBA68C8", // Purple
            "FFF06292", // Pink
            "FF90A4AE", // Blue Grey
        )

        val DEFAULTS = listOf(
            CategoryInfo("TODO", "Por hacer", "FFE57373"),
            CategoryInfo("REMINDER", "Recordatorio", "FFFFB74D"),
            CategoryInfo("HIGHLIGHT", "Destacado", "FFFFD54F"),
            CategoryInfo("NOTE", "Nota", "FF81C784"),
        )

        fun defaultForId(id: String): CategoryInfo =
            DEFAULTS.find { it.id == id } ?: DEFAULTS.last()
    }
}
