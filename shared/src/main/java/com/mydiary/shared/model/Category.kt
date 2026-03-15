package com.mydiary.shared.model

enum class Category {
    TODO,
    REMINDER,
    HIGHLIGHT,
    NOTE;

    companion object {
        private val keywordMap = mapOf(
            "recordar" to TODO,
            "pendiente" to REMINDER,
            "destacar" to HIGHLIGHT,
            "nota" to NOTE
        )

        fun fromKeyword(keyword: String): Category {
            return keywordMap[keyword.lowercase()] ?: NOTE
        }
    }
}
