package com.naamjap.counterapp.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object DailyReminderScheduler {
    const val UNIQUE_WORK_NAME = "naam_jap_daily_reminder"

    fun schedule(context: Context, preferences: NotificationPreferences) {
        if (!preferences.enabled || !preferences.dailyReminder) {
            cancel(context)
            return
        }
        val now = ZonedDateTime.now()
        var next = now.toLocalDate().atTime(LocalTime.of(preferences.reminderHour, preferences.reminderMinute)).atZone(now.zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now.toInstant(), next.toInstant()).toMillis().coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<NaamJapReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
