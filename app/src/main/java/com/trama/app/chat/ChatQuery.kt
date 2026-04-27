package com.trama.app.chat

data class ChatDateRange(
    val startMillis: Long,
    val endMillis: Long,
    val label: String
)

enum class ChatIntent {
    DAY_SUMMARY,
    DAY_PLACES,
    COMPLETED_TASKS,
    PLACE_PRESENCE,
    PLACE_DURATION,
    PLACE_ORDER,
    FIRST_PLACE,
    LAST_PLACE,
    PLACE_AFTER,
    UNKNOWN
}

data class ChatQuery(
    val rawQuestion: String,
    val intent: ChatIntent,
    val dateRange: ChatDateRange? = null,
    val placeTerms: List<String> = emptyList()
)
