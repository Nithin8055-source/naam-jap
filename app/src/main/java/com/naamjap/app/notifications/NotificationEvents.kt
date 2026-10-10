package com.naamjap.app.notifications

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class NotificationEventKind { SESSION_FINISHED, RECORD_ADDED, RECORD_DELETED, DEFAULT_NAAM_CHANGED, DAILY_GOAL_CHANGED, PASSWORD_CHANGED, PROFILE_CHANGED }

data class NotificationEvent(
    val userId: String,
    val kind: NotificationEventKind,
    val deduplicationKey: String
)

@Singleton
class NotificationEvents @Inject constructor() {
    private val mutableEvents = MutableSharedFlow<NotificationEvent>(
        replay = 8,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events = mutableEvents.asSharedFlow()

    fun publish(userId: String, kind: NotificationEventKind, key: String) {
        mutableEvents.tryEmit(NotificationEvent(userId, kind, key))
    }
}
