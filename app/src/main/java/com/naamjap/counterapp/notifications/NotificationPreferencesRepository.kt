package com.naamjap.counterapp.notifications

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch

data class NotificationPreferences(
    val enabled: Boolean = false,
    val sessions: Boolean = true,
    val dailyReminder: Boolean = false,
    val activity: Boolean = true,
    val account: Boolean = true,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val permissionPromptAttempted: Boolean = false
)

private val Context.notificationPreferencesDataStore by preferencesDataStore(name = "notification_preferences")

@Singleton
class NotificationPreferencesRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val store = context.notificationPreferencesDataStore

    val preferences: Flow<NotificationPreferences> = store.data
        .catch { emit(emptyPreferences()) }
        .map { values ->
        NotificationPreferences(
            enabled = values[ENABLED] ?: false,
            sessions = values[SESSIONS] ?: true,
            dailyReminder = values[DAILY_REMINDER] ?: false,
            activity = values[ACTIVITY] ?: true,
            account = values[ACCOUNT] ?: true,
            reminderHour = (values[REMINDER_HOUR] ?: 20).coerceIn(0, 23),
            reminderMinute = (values[REMINDER_MINUTE] ?: 0).coerceIn(0, 59),
            permissionPromptAttempted = values[PERMISSION_PROMPT_ATTEMPTED] ?: false
        )
    }

    suspend fun setEnabled(value: Boolean) = store.edit { it[ENABLED] = value }
    suspend fun setSessions(value: Boolean) = store.edit { it[SESSIONS] = value }
    suspend fun setDailyReminder(value: Boolean) = store.edit { it[DAILY_REMINDER] = value }
    suspend fun setActivity(value: Boolean) = store.edit { it[ACTIVITY] = value }
    suspend fun setAccount(value: Boolean) = store.edit { it[ACCOUNT] = value }
    suspend fun setReminderTime(hour: Int, minute: Int) {
        require(hour in 0..23 && minute in 0..59)
        store.edit {
            it[REMINDER_HOUR] = hour
            it[REMINDER_MINUTE] = minute
        }
    }
    suspend fun markPermissionPromptAttempted() = store.edit { it[PERMISSION_PROMPT_ATTEMPTED] = true }

    private companion object {
        val ENABLED = booleanPreferencesKey("notifications_enabled")
        val SESSIONS = booleanPreferencesKey("notifications_sessions")
        val DAILY_REMINDER = booleanPreferencesKey("notifications_daily_reminder")
        val ACTIVITY = booleanPreferencesKey("notifications_activity")
        val ACCOUNT = booleanPreferencesKey("notifications_account")
        val REMINDER_HOUR = intPreferencesKey("notifications_reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("notifications_reminder_minute")
        val PERMISSION_PROMPT_ATTEMPTED = booleanPreferencesKey("notifications_permission_prompt_attempted")
    }
}
