package com.naamjap.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

class NaamJapWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        NaamJapWidgetRenderer.update(context, manager, appWidgetIds)
        NaamJapWidgetWork.schedulePeriodicRefresh(context)
        NaamJapWidgetWork.enqueueRefresh(context)
    }

    override fun onEnabled(context: Context) {
        NaamJapWidgetWork.schedulePeriodicRefresh(context)
        NaamJapWidgetWork.enqueueRefresh(context)
    }

    override fun onDisabled(context: Context) {
        NaamJapWidgetWork.cancelPeriodicRefresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED,
            "android.intent.action.TIME_SET",
            Intent.ACTION_TIMEZONE_CHANGED -> {
                NaamJapWidgetRenderer.updateAll(context)
                NaamJapWidgetWork.enqueueRefresh(context)
            }
        }
    }
}
