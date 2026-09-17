package com.trama.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.trama.app.MainActivity
import com.trama.app.R
import com.trama.app.service.RecordingState
import com.trama.app.service.EntryProcessingState
import com.trama.app.service.ServiceController
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.util.DayRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TramaWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE_LISTENING -> {
                val state = ServiceController.captureSnapshot(context)
                if (state.recording || state.processing || state.watchActive || state.transferring) return
                if (ServiceController.shouldBeRunning(context)) {
                    ServiceController.stop(context, reason = "widget")
                } else {
                    ServiceController.start(context)
                }
                requestRefresh(context)
            }
            ACTION_TOGGLE_RECORDING -> {
                val state = ServiceController.captureSnapshot(context)
                if (state.processing || state.watchActive || state.transferring) return
                if (state.recording) {
                    RecordingState.stopRecording(context)
                } else {
                    ServiceController.startRecording(context)
                }
                requestRefresh(context)
            }
            ACTION_TOGGLE_DEVICE -> {
                val state = ServiceController.captureSnapshot(context)
                if (state.recording || state.processing || state.transferring) return
                if (state.watchActive) {
                    ServiceController.reclaimFromWatch(context) { requestRefresh(context) }
                } else {
                    ServiceController.transferToWatch(context) { requestRefresh(context) }
                }
                requestRefresh(context)
            }
            ACTION_REFRESH -> requestRefresh(context)
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                val day = DayRange.of(now)
                val snapshot = runCatching {
                    val repository = DatabaseProvider.getRepository(context)
                    TodayWidgetTimeline.build(
                        now = now,
                        pending = repository.getPending().first(),
                        completed = repository.getCompletedSince(day.startMs),
                        processingSourceIds = EntryProcessingState.processingIds.value,
                        maxItems = Int.MAX_VALUE
                    )
                }.getOrDefault(TodayWidgetSnapshot(0, emptyList()))
                appWidgetIds.forEach { widgetId ->
                    appWidgetManager.updateAppWidget(
                        widgetId,
                        buildViews(context, widgetId, snapshot, now)
                    )
                    appWidgetManager.notifyAppWidgetViewDataChanged(
                        widgetId,
                        R.id.widget_action_list
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun buildViews(
        context: Context,
        widgetId: Int,
        snapshot: TodayWidgetSnapshot,
        now: Long
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.trama_widget)
        val captureState = ServiceController.captureSnapshot(context)
        val listening = captureState.listeningActive
        val recording = captureState.recording
        val processing = captureState.processing
        val recordingElapsed = captureState.elapsedSeconds
        val watchActive = captureState.watchActive

        views.setTextViewText(
            R.id.widget_date,
            SimpleDateFormat("EEEE, d MMMM", Locale("es"))
                .format(Date(now))
                .replaceFirstChar { it.uppercase() }
        )
        views.setTextViewText(
            R.id.widget_count,
            context.resources.getQuantityString(
                R.plurals.widget_action_count,
                snapshot.totalCount,
                snapshot.totalCount
            )
        )
        configureStatus(
            context = context,
            views = views,
            recording = recording,
            processing = processing,
            recordingElapsed = recordingElapsed,
            watchActive = watchActive,
            captureMode = captureState.mode
        )

        val adapterIntent = Intent(context, TramaActionWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            data = Uri.parse("trama://widget/actions/$widgetId")
        }
        views.setRemoteAdapter(R.id.widget_action_list, adapterIntent)
        views.setEmptyView(R.id.widget_action_list, R.id.widget_empty)
        views.setPendingIntentTemplate(R.id.widget_action_list, openAppIntent(context))

        configureFab(
            context = context,
            views = views,
            viewId = R.id.widget_device_action,
            icon = R.drawable.ic_widget_watch,
            accent = R.color.widget_watch,
            selectedBackground = R.drawable.widget_fab_watch_selected,
            idleBackground = R.drawable.widget_fab_watch_idle,
            selected = watchActive,
            enabled = ((!recording && !processing) || watchActive) && !captureState.transferring,
            description = if (watchActive) "Recuperar escucha en el teléfono" else "Pasar escucha al reloj"
        )
        configureFab(
            context = context,
            views = views,
            viewId = R.id.widget_record_action,
            icon = if (recording) R.drawable.ic_widget_stop else R.drawable.ic_widget_record,
            accent = R.color.widget_red,
            selectedBackground = R.drawable.widget_fab_record_selected,
            idleBackground = R.drawable.widget_fab_record_idle,
            selected = recording,
            enabled = !processing && !watchActive && !captureState.transferring,
            description = when {
                recording -> "Detener grabación"
                processing -> "Procesando reunión"
                else -> "Grabar reunión"
            }
        )
        configureFab(
            context = context,
            views = views,
            viewId = R.id.widget_listen_action,
            icon = if (listening) R.drawable.ic_widget_mic else R.drawable.ic_widget_mic_off,
            accent = R.color.widget_amber,
            selectedBackground = R.drawable.widget_fab_listen_selected,
            idleBackground = R.drawable.widget_fab_listen_idle,
            selected = listening && !watchActive && !recording,
            enabled = !recording && !processing && !watchActive && !captureState.transferring,
            description = if (listening) "Pausar escucha continua" else "Activar escucha continua"
        )

        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
        views.setOnClickPendingIntent(
            R.id.widget_device_action,
            broadcastIntent(context, ACTION_TOGGLE_DEVICE, 101)
        )
        views.setOnClickPendingIntent(
            R.id.widget_record_action,
            broadcastIntent(context, ACTION_TOGGLE_RECORDING, 102)
        )
        views.setOnClickPendingIntent(
            R.id.widget_listen_action,
            broadcastIntent(context, ACTION_TOGGLE_LISTENING, 103)
        )
        views.setOnClickPendingIntent(
            R.id.widget_refresh,
            broadcastIntent(context, ACTION_REFRESH, 104)
        )
        return views
    }

    private fun configureStatus(
        context: Context,
        views: RemoteViews,
        recording: Boolean,
        processing: Boolean,
        recordingElapsed: Long,
        watchActive: Boolean,
        captureMode: ServiceController.CaptureMode
    ) {
        if (recording) {
            val base = SystemClock.elapsedRealtime() - recordingElapsed.coerceAtLeast(0L) * 1_000L
            views.setViewVisibility(R.id.widget_status, View.GONE)
            views.setViewVisibility(R.id.widget_recording_status, View.VISIBLE)
            views.setChronometer(
                R.id.widget_recording_status,
                base,
                context.getString(R.string.widget_status_recording),
                true
            )
            return
        }

        views.setChronometer(
            R.id.widget_recording_status,
            SystemClock.elapsedRealtime(),
            null,
            false
        )
        views.setViewVisibility(R.id.widget_recording_status, View.GONE)
        views.setViewVisibility(R.id.widget_status, View.VISIBLE)
        val (text, color) = when {
            captureMode == ServiceController.CaptureMode.TRANSFERRING ->
                R.string.widget_status_starting to R.color.widget_watch
            captureMode == ServiceController.CaptureMode.PROCESSING ->
                R.string.widget_status_processing to R.color.widget_red
            captureMode == ServiceController.CaptureMode.WATCH ->
                R.string.widget_status_watch to R.color.widget_watch
            captureMode == ServiceController.CaptureMode.TRIGGER_RECOGNIZED ->
                R.string.widget_status_trigger to R.color.widget_teal
            captureMode == ServiceController.CaptureMode.LISTENING ->
                R.string.widget_status_listening to R.color.widget_amber
            captureMode == ServiceController.CaptureMode.STARTING ->
                R.string.widget_status_starting to R.color.widget_amber
            captureMode == ServiceController.CaptureMode.PAUSED ->
                R.string.widget_status_paused to R.color.widget_muted
            captureMode == ServiceController.CaptureMode.ERROR ->
                R.string.widget_status_failed to R.color.widget_red
            else -> R.string.widget_status_idle to R.color.widget_muted
        }
        views.setTextViewText(R.id.widget_status, context.getString(text))
        views.setTextColor(R.id.widget_status, context.getColor(color))
    }

    private fun configureFab(
        context: Context,
        views: RemoteViews,
        viewId: Int,
        icon: Int,
        accent: Int,
        selectedBackground: Int,
        idleBackground: Int,
        selected: Boolean,
        enabled: Boolean,
        description: String
    ) {
        views.setImageViewResource(viewId, icon)
        views.setInt(viewId, "setBackgroundResource", if (selected) selectedBackground else idleBackground)
        views.setInt(
            viewId,
            "setColorFilter",
            context.getColor(if (selected) R.color.widget_fab_selected_icon else accent)
        )
        views.setBoolean(viewId, "setEnabled", enabled)
        views.setFloat(viewId, "setAlpha", if (enabled) 1f else 0.38f)
        views.setContentDescription(viewId, description)
    }

    companion object {
        private const val ACTION_TOGGLE_LISTENING = "com.trama.app.widget.TOGGLE_LISTENING"
        private const val ACTION_TOGGLE_RECORDING = "com.trama.app.widget.TOGGLE_RECORDING"
        private const val ACTION_TOGGLE_DEVICE = "com.trama.app.widget.TOGGLE_DEVICE"
        private const val ACTION_REFRESH = "com.trama.app.widget.REFRESH"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun requestRefresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TramaWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            context.sendBroadcast(Intent(context, TramaWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            })
        }

        private fun openAppIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                100,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        }

        private fun broadcastIntent(
            context: Context,
            action: String,
            requestCode: Int
        ): PendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, TramaWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
