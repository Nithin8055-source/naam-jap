package com.naamjap.app.domain.repository

import com.naamjap.app.domain.model.PracticeRecord
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.StateFlow

data class NaamType(val id: String, val name: String, val isDefault: Boolean)

data class ActiveJapSession(
    val id: String,
    val naamId: String,
    val naamName: String,
    val count: Long,
    val startedAt: Instant,
    val endedAt: Instant?,
    val durationSeconds: Long,
    val isPaused: Boolean
)

data class PracticeHistoryItem(
    val id: String,
    val date: LocalDate,
    val naamName: String,
    val count: Long,
    val durationSeconds: Long?,
    val note: String?,
    val isSession: Boolean,
    val sortAt: Instant
)

data class PracticeDashboard(
    val todayCount: Long = 0,
    val lifetimeCount: Long = 0,
    val sessionsToday: Long = 0,
    val currentStreak: Int = 0,
    val dailyGoal: Long = 1000
)

data class PracticeDataState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val naamTypes: List<NaamType> = emptyList(),
    val activeSession: ActiveJapSession? = null,
    val dashboard: PracticeDashboard = PracticeDashboard()
)

enum class SessionAction { INCREMENT, UNDO, PAUSE, RESUME, FINISH }

interface PracticeRepository {
    val state: StateFlow<PracticeDataState>
    suspend fun refresh(timeZoneId: String)
    suspend fun loadHistoryPage(offset: Int, limit: Int = 50): List<PracticeHistoryItem>
    suspend fun ensureDefaultNaamType(): NaamType
    suspend fun createNaamType(name: String): NaamType
    suspend fun setDefaultNaamType(id: String): NaamType
    suspend fun startSession(naamId: String): ActiveJapSession
    suspend fun applySessionAction(sessionId: String, action: SessionAction): ActiveJapSession
    suspend fun saveManualRecord(record: PracticeRecord, naamId: String, date: LocalDate)
    suspend fun saveDailyGoal(targetCount: Long)
    fun clearForSignedOutUser()
}
