package com.naamjap.app.data.remote.practice

import com.naamjap.app.data.remote.SupabaseProvider
import com.naamjap.app.domain.model.PracticeRecord
import com.naamjap.app.domain.repository.ActiveJapSession
import com.naamjap.app.domain.repository.NaamType
import com.naamjap.app.domain.repository.PracticeDashboard
import com.naamjap.app.domain.repository.PracticeDataState
import com.naamjap.app.domain.repository.PracticeHistoryItem
import com.naamjap.app.domain.repository.PracticeRepository
import com.naamjap.app.domain.repository.SessionAction
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.result.decodeList
import io.github.jan.supabase.postgrest.result.decodeSingle
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Singleton
class SupabasePracticeRepository @Inject constructor(
    private val provider: SupabaseProvider
) : PracticeRepository {
    private val _state = MutableStateFlow(PracticeDataState())
    override val state: StateFlow<PracticeDataState> = _state.asStateFlow()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loadMutex = Mutex()
    private val actionMutex = Mutex()
    private var activeUserId: String? = null
    private var pendingStart: PendingStart? = null
    private var pendingAction: PendingAction? = null

    init {
        provider.client?.auth?.sessionStatus?.onEach { session ->
            when (session) {
                is SessionStatus.Authenticated -> {
                    val userId = session.session.user?.id
                    if (userId != null && activeUserId != null && userId != activeUserId) {
                        clearForSignedOutUser()
                    }
                    activeUserId = userId
                }
                is SessionStatus.NotAuthenticated -> clearForSignedOutUser()
                else -> Unit
            }
        }?.launchIn(ioScope)
    }

    override suspend fun refresh(timeZoneId: String) = loadMutex.withLock {
        val userId = currentUserId()
        resetForAccountIfNeeded(userId)
        _state.value = _state.value.copy(isLoading = true, error = null, message = null)
        try {
            val zoneId = ZoneId.of(timeZoneId)
            val client = client()
            kotlinx.coroutines.coroutineScope {
            val names = async {
                client.from("naam_types").select {
                    filter { eq("user_id", userId) }
                    order("created_at", Order.ASCENDING)
                    limit(count = 500)
                }.decodeList<NaamTypeRow>()
            }
            val activeSession = async {
                client.postgrest.rpc("get_active_jap_session").decodeList<SessionRow>().firstOrNull()
            }
            val dashboard = async {
                client.postgrest.rpc("get_practice_dashboard", DashboardArgs(timeZoneId))
                    .decodeList<DashboardRow>().firstOrNull()
            }

            val nameRows = names.await()
            val naamById = nameRows.associate { it.id to it.name }
            val activeRow = activeSession.await()
            val statsRow = dashboard.await()
            val active = activeRow?.let { row ->
                sessionDurations(listOf(row.id))[row.id]?.let { duration -> row.toDomain(naamById, duration) }
                    ?: row.toDomain(naamById, SessionDuration(0, false))
            }
            _state.value = _state.value.copy(
                isLoading = false,
                hasLoaded = true,
                error = null,
                naamTypes = nameRows.map(NaamTypeRow::toDomain),
                activeSession = active,
                dashboard = statsRow?.toDomain() ?: PracticeDashboard()
            )
            }
        } catch (cancelled: CancellationException) {
            _state.value = _state.value.copy(isLoading = false)
            throw cancelled
        } catch (error: Exception) {
            _state.value = _state.value.copy(
                isLoading = false,
                error = "Couldn't sync practice data. Check your connection and try again."
            )
            throw error
        }
    }

    override suspend fun loadHistoryPage(offset: Int, limit: Int): List<PracticeHistoryItem> = withContext(Dispatchers.IO) {
        require(offset >= 0 && limit in 1..100) { "History page is invalid." }
        val userId = currentUserId()
        val page = offset.toLong()..(offset + limit - 1).toLong()
        val client = client()
        val nameRows = client.from("naam_types").select {
            filter { eq("user_id", userId) }
            limit(count = 500)
        }.decodeList<NaamTypeRow>()
        val naamById = nameRows.associate { it.id to it.name }
        val records = client.from("jap_records").select {
            filter { eq("user_id", userId) }
            order("created_at", Order.DESCENDING)
            order("id", Order.DESCENDING)
            range(page)
        }.decodeList<RecordRow>()
        val durations = sessionDurations(records.mapNotNull(RecordRow::sessionId))
        records.map { row ->
            PracticeHistoryItem(
                id = row.id,
                date = LocalDate.parse(row.recordDate),
                naamName = naamById[row.naamId] ?: "Naam",
                count = row.count,
                durationSeconds = row.sessionId?.let { durations[it]?.seconds },
                note = row.notes,
                isSession = row.sessionId != null,
                sortAt = row.createdAt.toInstantOrUtc()
            )
        }
            .sortedByDescending(PracticeHistoryItem::sortAt)
    }

    override suspend fun ensureDefaultNaamType(): NaamType = actionMutex.withLock {
        val row = client().postgrest.rpc("ensure_default_naam_type").decodeList<NaamTypeRow>().single()
        refresh(ZoneId.systemDefault().id)
        row.toDomain()
    }

    override suspend fun createNaamType(name: String): NaamType = actionMutex.withLock {
        val normalized = name.trim()
        require(normalized.length in 2..80) { "Enter a naam between 2 and 80 characters." }
        val request = CreateNaamArgs(UUID.randomUUID().toString(), normalized)
        val row = client().postgrest.rpc("create_naam_type", request).decodeList<NaamTypeRow>().single()
        refresh(ZoneId.systemDefault().id)
        row.toDomain()
    }

    override suspend fun setDefaultNaamType(id: String): NaamType = actionMutex.withLock {
        val row = client().postgrest.rpc("set_default_naam_type", NaamIdArgs(id)).decodeList<NaamTypeRow>().single()
        refresh(ZoneId.systemDefault().id)
        row.toDomain()
    }

    override suspend fun startSession(naamId: String): ActiveJapSession = actionMutex.withLock {
        val userId = currentUserId()
        resetForAccountIfNeeded(userId)
        val pending = pendingStart?.takeIf { it.naamId == naamId } ?: PendingStart(UUID.randomUUID().toString(), naamId).also { pendingStart = it }
        setSaving()
        try {
            val row = client().postgrest.rpc(
                "start_jap_session",
                StartSessionArgs(operationId = pending.operationId, naamId = pending.naamId)
            ).decodeList<SessionRow>().single()
            pendingStart = null
            runCatching { refresh(ZoneId.systemDefault().id) }
            _state.value = _state.value.copy(message = "Session saved to your account.", error = null)
            _state.value.activeSession ?: row.toDomain(_state.value.naamTypes.associate { it.id to it.name }, SessionDuration(0, false))
        } catch (cancelled: CancellationException) {
            _state.value = _state.value.copy(isSaving = false)
            throw cancelled
        } catch (error: Exception) {
            _state.value = _state.value.copy(isSaving = false, error = "Session wasn't confirmed. Tap start again to retry safely.")
            throw error
        }
    }

    override suspend fun applySessionAction(sessionId: String, action: SessionAction): ActiveJapSession = actionMutex.withLock {
        val pending = pendingAction?.takeIf { it.sessionId == sessionId && it.action == action }
            ?: PendingAction(UUID.randomUUID().toString(), sessionId, action).also { pendingAction = it }
        setSaving()
        try {
            val row = client().postgrest.rpc(
                "apply_jap_session_action",
                SessionActionArgs(sessionId, pending.operationId, action.name.lowercase(), ZoneId.systemDefault().id)
            ).decodeList<SessionRow>().single()
            pendingAction = null
            runCatching { refresh(ZoneId.systemDefault().id) }
            _state.value = _state.value.copy(message = if (action == SessionAction.FINISH) "Session saved to your account." else "Cloud session updated.", error = null)
            _state.value.activeSession ?: row.toDomain(_state.value.naamTypes.associate { it.id to it.name }, SessionDuration(0, false))
        } catch (cancelled: CancellationException) {
            _state.value = _state.value.copy(isSaving = false)
            throw cancelled
        } catch (error: Exception) {
            _state.value = _state.value.copy(isSaving = false, error = "Change wasn't confirmed. Tap the same action to retry safely.")
            throw error
        }
    }

    override suspend fun saveManualRecord(record: PracticeRecord, naamId: String, date: LocalDate) = actionMutex.withLock {
        require(record.count > 0) { "Enter a count greater than zero." }
        val pendingId = pendingRecordIds.getOrPut(record.id) { record.id }
        setSaving()
        try {
            client().postgrest.rpc(
                "create_jap_record",
                CreateRecordArgs(
                    id = pendingId,
                    naamId = naamId,
                    count = record.count,
                    recordDate = date.toString(),
                    notes = record.note
                )
            ).decodeList<RecordRow>().single()
            pendingRecordIds.remove(record.id)
            runCatching { refresh(ZoneId.systemDefault().id) }
            _state.value = _state.value.copy(message = "Record saved to your account.", error = null)
        } catch (cancelled: CancellationException) {
            _state.value = _state.value.copy(isSaving = false)
            throw cancelled
        } catch (error: Exception) {
            _state.value = _state.value.copy(isSaving = false, error = "Record wasn't confirmed. Retry the save to safely check it.")
            throw error
        }
    }

    override suspend fun saveDailyGoal(targetCount: Long) = actionMutex.withLock {
        require(targetCount in 1..1_000_000_000) { "Daily goal is out of range." }
        setSaving()
        try {
            client().postgrest.rpc("save_daily_goal", DailyGoalArgs(targetCount)).decodeList<DailyGoalRow>().single()
            runCatching { refresh(ZoneId.systemDefault().id) }
            _state.value = _state.value.copy(message = "Daily goal saved to your account.", error = null)
        } catch (cancelled: CancellationException) {
            _state.value = _state.value.copy(isSaving = false)
            throw cancelled
        } catch (error: Exception) {
            _state.value = _state.value.copy(isSaving = false, error = "Daily goal wasn't confirmed. Please retry.")
            throw error
        }
    }

    override fun clearForSignedOutUser() {
        activeUserId = null
        pendingStart = null
        pendingAction = null
        pendingRecordIds.clear()
        _state.value = PracticeDataState()
    }

    private suspend fun sessionDurations(sessionIds: List<String>): Map<String, SessionDuration> {
        if (sessionIds.isEmpty()) return emptyMap()
        return client().postgrest.rpc("get_jap_session_durations", SessionIdsArgs(sessionIds))
            .decodeList<SessionDurationRow>().associate { it.sessionId to SessionDuration(it.durationSeconds, it.isPaused) }
    }

    private fun setSaving() {
        _state.value = _state.value.copy(isSaving = true, error = null, message = null)
    }

    private fun resetForAccountIfNeeded(userId: String) {
        if (activeUserId != userId) {
            clearForSignedOutUser()
            activeUserId = userId
        }
    }

    private fun currentUserId(): String = requireNotNull(client().auth.currentUserOrNull()?.id) {
        "Authentication is required."
    }

    private fun client() = requireNotNull(provider.client) { "Supabase is not configured." }

    private data class PendingStart(val operationId: String, val naamId: String)
    private data class PendingAction(val operationId: String, val sessionId: String, val action: SessionAction)
    private val pendingRecordIds = mutableMapOf<String, String>()

    @Serializable private data class DashboardArgs(@SerialName("p_timezone") val timeZone: String)
    @Serializable private data class DashboardRow(
        @SerialName("today_count") val todayCount: Long,
        @SerialName("lifetime_count") val lifetimeCount: Long,
        @SerialName("sessions_today") val sessionsToday: Long,
        @SerialName("current_streak") val currentStreak: Int,
        @SerialName("daily_goal") val dailyGoal: Long
    ) { fun toDomain() = PracticeDashboard(todayCount, lifetimeCount, sessionsToday, currentStreak, dailyGoal) }
    @Serializable private data class NaamIdArgs(@SerialName("p_naam_id") val naamId: String)
    @Serializable private data class CreateNaamArgs(@SerialName("p_id") val id: String, @SerialName("p_name") val name: String)
    @Serializable private data class StartSessionArgs(
        @SerialName("p_operation_id") val operationId: String,
        @SerialName("p_naam_id") val naamId: String
    )
    @Serializable private data class SessionActionArgs(
        @SerialName("p_session_id") val sessionId: String,
        @SerialName("p_operation_id") val operationId: String,
        @SerialName("p_action") val action: String,
        @SerialName("p_timezone") val timeZone: String
    )
    @Serializable private data class CreateRecordArgs(
        @SerialName("p_id") val id: String,
        @SerialName("p_naam_id") val naamId: String,
        @SerialName("p_count") val count: Long,
        @SerialName("p_record_date") val recordDate: String,
        @SerialName("p_notes") val notes: String?
    )
    @Serializable private data class DailyGoalArgs(@SerialName("p_target_count") val targetCount: Long)
    @Serializable private data class SessionIdsArgs(@SerialName("p_session_ids") val sessionIds: List<String>)

    @Serializable private data class NaamTypeRow(
        val id: String,
        val name: String,
        @SerialName("is_default") val isDefault: Boolean? = false
    ) { fun toDomain() = NaamType(id, name, isDefault == true) }

    @Serializable private data class SessionRow(
        val id: String,
        @SerialName("naam_id") val naamId: String,
        val count: Long,
        @SerialName("started_at") val startedAt: String,
        @SerialName("ended_at") val endedAt: String? = null
    ) {
        fun toDomain(names: Map<String, String>, duration: SessionDuration) = ActiveJapSession(
            id, naamId, names[naamId] ?: "Naam", count, startedAt.toInstantOrUtc(),
            endedAt?.toInstantOrUtc(), duration.seconds, duration.paused
        )
    }

    @Serializable private data class SessionDurationRow(
        @SerialName("session_id") val sessionId: String,
        @SerialName("duration_seconds") val durationSeconds: Long,
        @SerialName("is_paused") val isPaused: Boolean
    )
    private data class SessionDuration(val seconds: Long, val paused: Boolean)

    @Serializable private data class RecordRow(
        val id: String,
        @SerialName("naam_id") val naamId: String,
        val count: Long,
    @SerialName("record_date") val recordDate: String,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("session_id") val sessionId: String? = null
)
    @Serializable private data class DailyGoalRow(
        val id: String,
        @SerialName("target_count") val targetCount: Long
    )
}

private fun String.toInstantOrUtc(): Instant = runCatching { Instant.parse(this) }
    .getOrElse { OffsetDateTime.parse(this).toInstant() }
