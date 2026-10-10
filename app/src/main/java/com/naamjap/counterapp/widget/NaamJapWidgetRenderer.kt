package com.naamjap.counterapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.naamjap.counterapp.MainActivity
import com.naamjap.counterapp.R
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

internal object NaamJapWidgetRenderer {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, NaamJapWidgetProvider::class.java)
        update(context, manager, manager.getAppWidgetIds(provider))
    }

    fun update(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        widgetIds.forEach { id ->
            manager.updateAppWidget(id, buildViews(context, id))
        }
    }

    private fun buildViews(context: Context, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_naam_jap_2x2)
        val snapshot = NaamJapWidgetSnapshotStore.read(context)
        val today = java.time.LocalDate.now().toString()
        val isNewDay = snapshot != null && snapshot.savedDate != today
        val count = if (isNewDay) 0L else snapshot?.count
        val goal = snapshot?.goal ?: 0L
        val progress = if (goal > 0L && count != null) {
            ((count.toDouble() / goal.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        } else 0
        val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())

        views.setTextViewText(R.id.widget_date, dateFormat.format(Date()))
        views.setTextViewText(R.id.widget_count, count?.let(::formatCount) ?: "--")
        views.setTextViewText(R.id.widget_percent, if (goal > 0L && !isNewDay) "$progress%" else "--%")
        views.setProgressBar(R.id.widget_progress, 1000, progress * 10, false)
        views.setTextViewText(
            R.id.widget_status,
            when {
                snapshot == null -> "Sign in to sync"
                isNewDay -> "New day - syncing"
                System.currentTimeMillis() - snapshot.savedAtMillis > TimeUnit.MINUTES.toMillis(15) -> "Last synced - may be stale"
                snapshot.status.isBlank() -> "Last synced"
                else -> snapshot.status
            }
        )

        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_WIDGET_OPEN_HOME, true)
        }
        val openPending = PendingIntent.getActivity(
            context, widgetId * 2, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_open_app, openPending)
        views.setOnClickPendingIntent(R.id.widget_open_total, openPending)

        val countIntent = Intent(context, NaamJapWidgetActionReceiver::class.java).apply {
            action = NaamJapWidgetActionReceiver.ACTION_INCREMENT
            data = android.net.Uri.parse("naamjap://widget/increment/$widgetId")
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        val countPending = PendingIntent.getBroadcast(
            context, widgetId,
            countIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_count_action, countPending)
        return views
    }

    private fun formatCount(count: Long): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(count)
}
