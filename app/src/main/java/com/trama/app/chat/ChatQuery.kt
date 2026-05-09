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
    GENERAL_FACT_SEARCH,
    PLACE_LIST,
    LIKED_PLACES,
    PLACE_PRESENCE,
    PLACE_DURATION,
    PLACE_ORDER,
    FIRST_PLACE,
    LAST_PLACE,
    PLACE_AFTER,
    UNKNOWN
}

enum class ChatPlaceCategory {
    RESTAURANT,
    ANY
}

enum class ChatAnswerMode {
    GENERAL,
    DATE_LIST
}

data class ChatQuery(
    val rawQuestion: String,
    val intent: ChatIntent,
    val dateRange: ChatDateRange? = null,
    val placeTerms: List<String> = emptyList(),
    val placeCategory: ChatPlaceCategory = ChatPlaceCategory.ANY,
    val likedOnly: Boolean = false,
    val searchTerms: List<String> = emptyList(),
    val actionTypeFilter: String? = null,
    val answerMode: ChatAnswerMode = ChatAnswerMode.GENERAL
)
