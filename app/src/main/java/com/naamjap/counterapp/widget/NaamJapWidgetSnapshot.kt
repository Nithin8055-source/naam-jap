package com.naamjap.counterapp.widget

import android.content.Context
import java.security.MessageDigest
import java.time.LocalDate

internal data class NaamJapWidgetSnapshot(
    val accountKey: String,
    val count: Long,
    val goal: Long,
    val savedDate: String,
    val savedAtMillis: Long,
    val status: String,
    val activeSessionId: String?,
    val sessionPaused: Boolean
)

/** Stores only a user-scoped, last-known dashboard snapshot; credentials and tokens never enter widget storage. */
internal object NaamJapWidgetSnapshotStore {
    private const val PREFS = "naam_jap_widget_private_snapshot"
    private const val KEY_ACCOUNT = "account_key"
    private const val KEY_COUNT = "today_count"
    private const val KEY_GOAL = "daily_goal"
    private const val KEY_DATE = "saved_date"
    private const val KEY_SAVED_AT = "saved_at_millis"
    private const val KEY_STATUS = "status"
    private const val KEY_SESSION_ID = "active_session_id"
    private const val KEY_SESSION_PAUSED = "session_paused"

    fun accountKey(userId: String): String = MessageDigest.getInstance("SHA-256")
        .digest(userId.toByteArray(Charsets.UTF_8))
        .take(16)
        .joinToString("") { "%02x".format(it) }

    fun read(context: Context): NaamJapWidgetSnapshot? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val account = prefs.getString(KEY_ACCOUNT, null) ?: return null
        return NaamJapWidgetSnapshot(
            accountKey = account,
            count = prefs.getLong(KEY_COUNT, 0L).coerceAtLeast(0L),
            goal = prefs.getLong(KEY_GOAL, 0L).coerceAtLeast(0L),
            savedDate = prefs.getString(KEY_DATE, "").orEmpty(),
            savedAtMillis = prefs.getLong(KEY_SAVED_AT, 0L),
            status = prefs.getString(KEY_STATUS, "").orEmpty(),
            activeSessionId = prefs.getString(KEY_SESSION_ID, null),
            sessionPaused = prefs.getBoolean(KEY_SESSION_PAUSED, false)
        )
    }

    fun write(
        context: Context,
        userId: String,
        count: Long,
        goal: Long,
        activeSessionId: String? = null,
        sessionPaused: Boolean = false,
        status: String = "Synced"
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ACCOUNT, accountKey(userId))
            .putLong(KEY_COUNT, count.coerceAtLeast(0L))
            .putLong(KEY_GOAL, goal.coerceAtLeast(0L))
            .putString(KEY_DATE, LocalDate.now().toString())
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .putString(KEY_STATUS, status)
            .putString(KEY_SESSION_ID, activeSessionId)
            .putBoolean(KEY_SESSION_PAUSED, sessionPaused)
            .apply()
    }

    fun setStatusForAccount(context: Context, userId: String, status: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_ACCOUNT, null) == accountKey(userId)) {
            prefs.edit().putString(KEY_STATUS, status).apply()
        }
    }

    fun setStatusForSnapshot(context: Context, snapshot: NaamJapWidgetSnapshot, status: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_ACCOUNT, null) == snapshot.accountKey) {
            prefs.edit().putString(KEY_STATUS, status).apply()
        }
    }

    fun clearIfDifferentAccount(context: Context, userId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_ACCOUNT, null)
        if (stored != null && stored != accountKey(userId)) prefs.edit().clear().apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun shouldOpenAppForAction(context: Context): Boolean {
        val snapshot = read(context) ?: return true
        if (snapshot.activeSessionId.isNullOrBlank() || snapshot.sessionPaused) return true
        return when (snapshot.status) {
            "Sign in to sync", "Open Jap to start", "Open Jap to resume", "Open app to count",
            "Configuration unavailable" -> true
            else -> snapshot.status.startsWith("Sync failed") ||
                snapshot.status.startsWith("Needs sync") || snapshot.status.startsWith("Session changed")
        }
    }

    fun actionShouldOpenJap(context: Context): Boolean {
        val snapshot = read(context)
        return snapshot == null || snapshot.activeSessionId.isNullOrBlank() || snapshot.sessionPaused ||
            snapshot.status == "Open Jap to start" || snapshot.status == "Open Jap to resume" ||
            snapshot.status.startsWith("Session changed")
    }
}
