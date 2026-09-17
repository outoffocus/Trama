package com.trama.app.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.trama.app.R
import com.trama.app.service.EntryProcessingState
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.util.DayRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class TramaActionWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        ActionFactory(applicationContext)

    private class ActionFactory(private val context: Context) : RemoteViewsFactory {
        private var items: List<WidgetTimelineItem> = emptyList()

        override fun onCreate() = Unit

        override fun onDataSetChanged() {
            val now = System.currentTimeMillis()
            val day = DayRange.of(now)
            items = runCatching {
                runBlocking(Dispatchers.IO) {
                    val repository = DatabaseProvider.getRepository(context)
                    TodayWidgetTimeline.build(
                        now = now,
                        pending = repository.getPending().first(),
                        completed = repository.getCompletedSince(day.startMs),
                        processingSourceIds = EntryProcessingState.processingIds.value,
                        maxItems = Int.MAX_VALUE
                    ).items
                }
            }.getOrDefault(emptyList())
        }

        override fun onDestroy() {
            items = emptyList()
        }

        override fun getCount(): Int = items.size

        override fun getViewAt(position: Int): RemoteViews? {
            val item = items.getOrNull(position) ?: return null
            return RemoteViews(context.packageName, R.layout.trama_widget_action_item).apply {
                setTextViewText(R.id.widget_item_time, item.timeLabel)
                setTextViewText(R.id.widget_item_title, item.title)
                setTextViewText(R.id.widget_item_label, item.label)
                val color = if (item.type == TodayWidgetTimeline.COMPLETED) {
                    R.color.widget_green
                } else {
                    R.color.widget_amber
                }
                setTextColor(R.id.widget_item_label, context.getColor(color))
                setInt(
                    R.id.widget_item_dot,
                    "setBackgroundResource",
                    if (item.type == TodayWidgetTimeline.COMPLETED) {
                        R.drawable.widget_dot_green
                    } else {
                        R.drawable.widget_dot_amber
                    }
                )
                setOnClickFillInIntent(R.id.widget_item_root, Intent())
            }
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long =
            items.getOrNull(position)?.let { item -> item.timestamp xor item.title.hashCode().toLong() }
                ?: position.toLong()

        override fun hasStableIds(): Boolean = true
    }
}
