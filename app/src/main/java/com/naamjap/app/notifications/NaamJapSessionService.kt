package com.naamjap.app.notifications

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import android.os.Build
import com.naamjap.app.domain.repository.AuthRepository
import com.naamjap.app.domain.repository.AuthSessionState
import com.naamjap.app.domain.repository.PracticeRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Job
import java.time.ZoneId

@AndroidEntryPoint
class NaamJapSessionService : Service() {
    @Inject lateinit var practice: PracticeRepository
    @Inject lateinit var auth: AuthRepository
    @Inject lateinit var preferences: NotificationPreferencesRepository
    @Inject lateinit var notificationCenter: NaamJapNotificationCenter

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var foreground = false
    private var monitorJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        notificationCenter.createChannels()
        val current = practice.state.value
        val initial = notificationCenter.sessionNotification(current.activeSession, current.dashboard.todayCount, current.error != null)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NaamJapNotificationCenter.SESSION_NOTIFICATION_ID,
                    initial,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(this, NaamJapNotificationCenter.SESSION_NOTIFICATION_ID, initial, 0)
            } else {
                startForeground(NaamJapNotificationCenter.SESSION_NOTIFICATION_ID, initial)
            }
        } catch (_: Exception) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        foreground = true
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            try {
                val authState = withTimeoutOrNull(15_000L) { auth.sessionState.first { it !is AuthSessionState.Checking } }
                if (authState == null) {
                    stopAndRemoveNotification()
                    return@launch
                }
                if (authState !is AuthSessionState.SignedIn) {
                    stopAndRemoveNotification()
                    return@launch
                }
                // A sticky service restart restores server state before resuming status updates.
                try {
                    practice.refresh(ZoneId.systemDefault().id)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Keep the last confirmed state; the service remains visible with its last count.
                }
                combine(practice.state, preferences.preferences) { state, prefs -> state to prefs }
                    .collect { (state, prefs) ->
                        val active = state.activeSession
                        if (!prefs.enabled || !prefs.sessions || active == null) {
                            stopAndRemoveNotification()
                            return@collect
                        }
                        notificationCenter.notifySession(active, state.dashboard.todayCount, state.error != null)
                        if (active.isPaused) {
                            if (foreground) {
                                ServiceCompat.stopForeground(this@NaamJapSessionService, ServiceCompat.STOP_FOREGROUND_DETACH)
                                foreground = false
                            }
                            stopSelf(startId)
                            return@collect
                        }
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                stopAndRemoveNotification()
            }
        }
        return START_STICKY
    }

    private fun stopAndRemoveNotification() {
        notificationCenter.removeSession()
        if (foreground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foreground = false
        }
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
