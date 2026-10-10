package com.naamjap.counterapp.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderWorkerDependencies {
    fun notificationPreferencesRepository(): NotificationPreferencesRepository
    fun notificationCenter(): NaamJapNotificationCenter
}

class NaamJapReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(applicationContext, ReminderWorkerDependencies::class.java)
        val preferences = try {
            dependencies.notificationPreferencesRepository().preferences.first()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return Result.retry()
        }
        if (preferences.enabled && preferences.dailyReminder) {
            dependencies.notificationCenter().notifyReminder()
            DailyReminderScheduler.schedule(applicationContext, preferences)
        }
        return Result.success()
    }
}
