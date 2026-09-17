package com.trama.app.widget

import com.trama.shared.model.DiaryEntry
import com.trama.shared.util.DayRange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class WidgetTimelineItem(
    val timestamp: Long,
    val timeLabel: String,
    val title: String,
    val label: String,
    val type: String
)

internal data class TodayWidgetSnapshot(
    val totalCount: Int,
    val items: List<WidgetTimelineItem>
)

internal object TodayWidgetTimeline {
    const val TASK = "TASK"
    const val COMPLETED = "COMPLETED"
    fun build(
        now: Long,
        pending: List<DiaryEntry>,
        completed: List<DiaryEntry>,
        processingSourceIds: Set<Long> = emptySet(),
        maxItems: Int = 5
    ): TodayWidgetSnapshot {
        val day = DayRange.of(now)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("d MMM", Locale("es"))
        val items = buildList {
            pending.forEach { entry ->
                if (entry.parentEntryId in processingSourceIds) return@forEach
                val dueDate = entry.dueDate
                val occurrence = dueDate ?: entry.createdAt
                add(
                    WidgetTimelineItem(
                        timestamp = occurrence,
                        timeLabel = when {
                            occurrence < day.startMs -> "Pend."
                            occurrence <= day.endInclusiveMs -> timeFormat.format(Date(occurrence))
                            else -> dateFormat.format(Date(occurrence))
                        },
                        title = entry.displayText,
                        label = "TAREA",
                        type = TASK
                    )
                )
            }
            completed.forEach { entry ->
                val completedAt = entry.completedAt ?: return@forEach
                if (completedAt !in day.startMs..day.endInclusiveMs) return@forEach
                add(
                    WidgetTimelineItem(
                        timestamp = completedAt,
                        timeLabel = timeFormat.format(Date(completedAt)),
                        title = entry.displayText,
                        label = "HECHO",
                        type = COMPLETED
                    )
                )
            }
        }.sortedWith(compareBy<WidgetTimelineItem> { it.timestamp }.thenBy { it.title })

        val visible = when {
            items.size <= maxItems -> items
            items.indexOfFirst { it.timestamp >= now } >= 0 -> {
                val nextIndex = items.indexOfFirst { it.timestamp >= now }
                items.drop((nextIndex - 2).coerceAtLeast(0)).take(maxItems)
            }
            else -> items.takeLast(maxItems)
        }
        return TodayWidgetSnapshot(totalCount = items.size, items = visible)
    }
}
