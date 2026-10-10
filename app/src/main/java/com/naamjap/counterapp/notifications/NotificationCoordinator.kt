package com.naamjap.counterapp.notifications

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.naamjap.counterapp.domain.repository.AuthRepository
import com.naamjap.counterapp.domain.repository.AuthSessionState
import com.naamjap.counterapp.domain.repository.PracticeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Singleton
class NotificationCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: NotificationPreferencesRepository,
    private val auth: AuthRepository,
    private val practice: PracticeRepository,
    private val events: NotificationEvents,
    private val notificationCenter: NaamJapNotificationCenter
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var started = false
    private var serviceSessionId: String? = null

    @Synchronized
    fun start() {
        if (started) return
        started = true
        notificationCenter.createChannels()
        scope.launch {
            preferences.preferences
                .map { ReminderScheduleSettings(it.enabled, it.dailyReminder, it.reminderHour, it.reminderMinute) }
                .distinctUntilChanged()
                .collect { reminder ->
                if (reminder.enabled && reminder.dailyReminder) {
                    DailyReminderScheduler.schedule(
                        context,
                        NotificationPreferences(
                            enabled = reminder.enabled,
                            dailyReminder = reminder.dailyReminder,
                            reminderHour = reminder.hour,
                            reminderMinute = reminder.minute
                        )
                    )
                } else {
                    DailyReminderScheduler.cancel(context)
                    notificationCenter.cancelChannelNotifications(NaamJapNotificationCenter.CHANNEL_REMINDER)
                }
            }
        }
        scope.launch {
            preferences.preferences.collect { prefs ->
                if (!prefs.enabled) {
                    DailyReminderScheduler.cancel(context)
                    notificationCenter.cancelAllOptional()
                } else {
                    if (!prefs.sessions || !notificationCenter.canPost(NaamJapNotificationCenter.CHANNEL_SESSION)) {
                        notificationCenter.cancelChannelNotifications(NaamJapNotificationCenter.CHANNEL_SESSION)
                    }
                    if (!prefs.activity) notificationCenter.cancelChannelNotifications(NaamJapNotificationCenter.CHANNEL_ACTIVITY)
                    if (!prefs.account) notificationCenter.cancelChannelNotifications(NaamJapNotificationCenter.CHANNEL_ACCOUNT)
                }
                if (!prefs.enabled || !prefs.sessions || !notificationCenter.canPost(NaamJapNotificationCenter.CHANNEL_SESSION)) {
                    stopSessionService(removeNotification = true)
                }
            }
        }
        scope.launch {
            events.events.collect { event ->
                try {
                    val currentAuth = auth.sessionState.first { it !is AuthSessionState.Checking }
                    val prefs = preferences.preferences.first()
                    val categoryEnabled = when (event.kind) {
                        NotificationEventKind.SESSION_FINISHED -> prefs.enabled && prefs.sessions
                        NotificationEventKind.PASSWORD_CHANGED, NotificationEventKind.PROFILE_CHANGED -> prefs.enabled && prefs.account
                        else -> prefs.enabled && prefs.activity
                    }
                    if (categoryEnabled && (currentAuth as? AuthSessionState.SignedIn)?.account?.userId == event.userId) {
                        notificationCenter.notifyEvent(event)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Notification delivery is best-effort and never changes the confirmed data operation.
                }
            }
        }
        scope.launch {
            auth.sessionState.distinctUntilChanged().collectLatest { session ->
                notificationCenter.removeSession()
                serviceSessionId = null
                if (session !is AuthSessionState.SignedIn) {
                    stopSessionService(removeNotification = true)
                    return@collectLatest
                }
                try {
                    practice.refresh(ZoneId.systemDefault().id)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Continue observing; a later successful refresh can restore the live notification.
                }
                combine(practice.state, preferences.preferences) { state, prefs -> state to prefs }
                    .collect { (state, prefs) ->
                        reconcileSession(prefs, state.activeSession, state.dashboard.todayCount, state.error != null)
                    }
            }
        }
    }

    fun onForeground() {
        scope.launch {
            val prefs = preferences.preferences.first()
            if (!prefs.enabled || !prefs.sessions || !notificationCenter.canPost(NaamJapNotificationCenter.CHANNEL_SESSION)) {
                stopSessionService(removeNotification = true)
                return@launch
            }
            val currentAuth = auth.sessionState.first { it !is AuthSessionState.Checking }
            if (currentAuth !is AuthSessionState.SignedIn) return@launch
            val state = practice.state.value
            reconcileSession(prefs, state.activeSession, state.dashboard.todayCount, state.error != null)
        }
    }

    private fun reconcileSession(
        prefs: NotificationPreferences,
        session: com.naamjap.counterapp.domain.repository.ActiveJapSession?,
        todayCount: Long,
        syncUnavailable: Boolean = false
    ) {
        if (!prefs.enabled || !prefs.sessions || !notificationCenter.canPost(NaamJapNotificationCenter.CHANNEL_SESSION)) {
            stopSessionService(removeNotification = true)
            return
        }
        if (session == null) {
            stopSessionService(removeNotification = true)
            return
        }
        if (session.isPaused) {
            notificationCenter.notifySession(session, todayCount, syncUnavailable)
            // The running service observes the paused state, detaches its notification, and exits.
            serviceSessionId = null
            return
        }
        notificationCenter.notifySession(session, todayCount, syncUnavailable)
        if (serviceSessionId != session.id) {
            serviceSessionId = session.id
            try {
                ContextCompat.startForegroundService(context, Intent(context, NaamJapSessionService::class.java))
            } catch (_: Exception) {
                // Android may reject starts from a background process; the ordinary notification remains best-effort.
                serviceSessionId = null
            }
        }
    }

    private fun stopSessionService(removeNotification: Boolean) {
        context.stopService(Intent(context, NaamJapSessionService::class.java))
        serviceSessionId = null
        if (removeNotification) notificationCenter.removeSession()
    }

    private data class ReminderScheduleSettings(
        val enabled: Boolean,
        val dailyReminder: Boolean,
        val hour: Int,
        val minute: Int
    )
}
