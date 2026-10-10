package com.naamjap.counterapp.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderRescheduleDependencies {
    fun notificationPreferencesRepository(): NotificationPreferencesRepository
}

class NaamJapTimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_BOOT_COMPLETED -> Unit
            else -> return
        }
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val dependencies = EntryPointAccessors.fromApplication(context, ReminderRescheduleDependencies::class.java)
                val prefs = dependencies.notificationPreferencesRepository().preferences.first()
                DailyReminderScheduler.schedule(context, prefs)
            } catch (_: Exception) {
                // WorkManager's persisted request remains available and the next app launch reconciles it.
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }
}
