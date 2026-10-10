package com.naamjap.counterapp.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.naamjap.counterapp.MainActivity
import com.naamjap.counterapp.R
import com.naamjap.counterapp.navigation.EXTRA_NOTIFICATION_OPEN_HOME
import com.naamjap.counterapp.navigation.EXTRA_NOTIFICATION_OPEN_JAP
import com.naamjap.counterapp.domain.repository.ActiveJapSession
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NaamJapNotificationCenter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile private var lastSessionSnapshot: SessionSnapshot? = null
    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_SESSION, "Jap session", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Status for an active Naam Jap session"
                    setShowBadge(false)
                },
                NotificationChannel(CHANNEL_REMINDER, "Daily reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Your local-time daily Naam Jap reminder"
                },
                NotificationChannel(CHANNEL_ACTIVITY, "Practice updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Confirmed Naam Jap record and goal updates"
                },
                NotificationChannel(CHANNEL_ACCOUNT, "Account updates", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Confirmed profile and account updates"
                }
            )
        )
    }

    fun canPost(channelId: String): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(channelId)
            if (channel == null || channel.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    fun sessionNotification(
        session: ActiveJapSession?,
        todayCount: Long = 0L,
        syncUnavailable: Boolean = false
    ): Notification = baseBuilder(CHANNEL_SESSION)
        .setContentTitle(when {
            session == null -> "Naam Jap session"
            session.isPaused -> "Naam Jap — Session paused"
            else -> "Naam Jap — Session in progress"
        })
        .setContentText(
            when {
                session == null -> "Opening your current session…"
                session.isPaused -> "Paused · ${formatCount(session.count)} Naam Jap"
                syncUnavailable -> "Sync unavailable · last confirmed ${formatCount(session.count)}"
                else -> "Counting · ${formatCount(session.count)} Naam Jap"
            }
        )
        .setSubText(session?.let { "Today's total: ${formatCount(todayCount)}" } ?: "Session status")
        .setOngoing(session != null && !session.isPaused)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setPublicVersion(
            baseBuilder(CHANNEL_SESSION)
                .setContentTitle(if (session?.isPaused == true) "Naam Jap session paused" else "Naam Jap session")
                .setContentText(if (session?.isPaused == true) "Your session is paused" else "A practice session is in progress")
                .build()
        )
        .setContentIntent(openJapPendingIntent())
        .build()

    fun notifySession(session: ActiveJapSession?, todayCount: Long = 0L, syncUnavailable: Boolean = false) {
        if (!canPost(CHANNEL_SESSION)) return
        val snapshot = SessionSnapshot(session?.id, session?.count ?: 0L, todayCount, session?.isPaused ?: false, syncUnavailable)
        if (lastSessionSnapshot == snapshot) return
        try {
            NotificationManagerCompat.from(context).notify(SESSION_NOTIFICATION_ID, sessionNotification(session, todayCount, syncUnavailable))
            lastSessionSnapshot = snapshot
        } catch (_: SecurityException) {
            lastSessionSnapshot = null
        }
    }

    fun removeSession() {
        lastSessionSnapshot = null
        try { NotificationManagerCompat.from(context).cancel(SESSION_NOTIFICATION_ID) } catch (_: SecurityException) { }
    }

    fun cancelAllOptional() {
        lastSessionSnapshot = null
        try { NotificationManagerCompat.from(context).cancelAll() } catch (_: SecurityException) { }
    }

    fun cancelChannelNotifications(channelId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            context.getSystemService(NotificationManager::class.java).activeNotifications
                .filter { it.notification.channelId == channelId }
                .forEach { NotificationManagerCompat.from(context).cancel(it.tag, it.id) }
            if (channelId == CHANNEL_SESSION) lastSessionSnapshot = null
        } catch (_: SecurityException) { }
    }

    fun notifyReminder() {
        if (!canPost(CHANNEL_REMINDER)) return
        try {
            NotificationManagerCompat.from(context).notify(
                REMINDER_NOTIFICATION_ID,
                baseBuilder(CHANNEL_REMINDER)
                .setContentTitle("Naam Jap reminder")
                .setContentText("Take a moment to record today's Naam Jap count.")
                .setContentIntent(openHomePendingIntent())
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .build()
            )
        } catch (_: SecurityException) { }
    }

    fun notifyEvent(event: NotificationEvent) {
        val (channel, title, message) = when (event.kind) {
            NotificationEventKind.SESSION_FINISHED -> Triple(CHANNEL_SESSION, "Jap session complete", "Your Jap session is complete.")
            NotificationEventKind.RECORD_ADDED -> Triple(CHANNEL_ACTIVITY, "Naam Jap updated", "Your Naam Jap record was added successfully.")
            NotificationEventKind.RECORD_DELETED -> Triple(CHANNEL_ACTIVITY, "Naam Jap updated", "Your Naam Jap record was deleted.")
            NotificationEventKind.DEFAULT_NAAM_CHANGED -> Triple(CHANNEL_ACTIVITY, "Preference updated", "Your default Naam preference was updated.")
            NotificationEventKind.DAILY_GOAL_CHANGED -> Triple(CHANNEL_ACTIVITY, "Daily goal updated", "Your daily Naam Jap goal was updated.")
            NotificationEventKind.PASSWORD_CHANGED -> Triple(CHANNEL_ACCOUNT, "Account updated", "Your account password was changed successfully.")
            NotificationEventKind.PROFILE_CHANGED -> Triple(CHANNEL_ACCOUNT, "Profile updated", "Your profile name was updated successfully.")
        }
        if (!canPost(channel)) return
        val tag = "naam_jap_${event.kind.name.lowercase()}_${sha256("${event.userId}:${event.deduplicationKey}").take(24)}"
        try {
            NotificationManagerCompat.from(context).notify(
                tag,
                EVENT_NOTIFICATION_ID,
                baseBuilder(channel)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(openHomePendingIntent())
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .build()
            )
        } catch (_: SecurityException) { }
    }

    private fun baseBuilder(channelId: String) = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_notification_mark)
        .setColor(ContextCompat.getColor(context, R.color.notification_gold))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)

    private fun openJapPendingIntent() = activityPendingIntent(EXTRA_NOTIFICATION_OPEN_JAP, REQUEST_OPEN_JAP)
    private fun openHomePendingIntent() = activityPendingIntent(EXTRA_NOTIFICATION_OPEN_HOME, REQUEST_OPEN_HOME)

    private fun activityPendingIntent(extra: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = "com.naamjap.counterapp.OPEN_NOTIFICATION.$requestCode"
            putExtra(extra, true)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun formatCount(value: Long) = java.text.NumberFormat.getIntegerInstance().format(value.coerceAtLeast(0L))

    private fun sha256(value: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private data class SessionSnapshot(
        val id: String?,
        val count: Long,
        val todayCount: Long,
        val paused: Boolean,
        val syncUnavailable: Boolean
    )

    companion object {
        const val CHANNEL_SESSION = "naam_jap_session"
        const val CHANNEL_REMINDER = "naam_jap_reminders"
        const val CHANNEL_ACTIVITY = "naam_jap_activity"
        const val CHANNEL_ACCOUNT = "naam_jap_account"
        const val SESSION_NOTIFICATION_ID = 7201
        private const val REMINDER_NOTIFICATION_ID = 7202
        private const val EVENT_NOTIFICATION_ID = 7203
        private const val REQUEST_OPEN_JAP = 7210
        private const val REQUEST_OPEN_HOME = 7211
    }
}
