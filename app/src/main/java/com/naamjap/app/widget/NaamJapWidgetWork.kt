package com.naamjap.app.widget

import android.content.Context
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.CoroutineWorker
import androidx.work.Data
import com.naamjap.app.domain.repository.AuthRepository
import com.naamjap.app.domain.repository.AuthSessionState
import com.naamjap.app.domain.repository.PracticeRepository
import com.naamjap.app.domain.repository.SessionAction
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

internal object NaamJapWidgetWork {
    private const val SERIAL_QUEUE = "naam_jap_widget_serial_queue"
    private const val PERIODIC_REFRESH = "naam_jap_widget_periodic_refresh"
    private const val KEY_OPERATION = "operation"
    private const val KEY_OPERATION_ID = "operation_id"
    const val OP_REFRESH = "refresh"
    const val OP_INCREMENT = "increment"

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun enqueueRefresh(context: Context) {
        if (AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, NaamJapWidgetProvider::class.java)).isEmpty()
        ) return
        val request = OneTimeWorkRequestBuilder<NaamJapWidgetWorker>()
            .setInputData(Data.Builder().putString(KEY_OPERATION, OP_REFRESH).build())
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            SERIAL_QUEUE,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    fun enqueueIncrement(context: Context, accountKey: String, sessionId: String) {
        val operationId = UUID.randomUUID().toString()
        val request = OneTimeWorkRequestBuilder<NaamJapWidgetWorker>()
            .setInputData(
                Data.Builder()
                    .putString(KEY_OPERATION, OP_INCREMENT)
                    .putString(KEY_OPERATION_ID, operationId)
                    .putString("expected_account_key", accountKey)
                    .putString("expected_session_id", sessionId)
                    .build()
            )
            .setConstraints(networkConstraint)
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            SERIAL_QUEUE,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    fun schedulePeriodicRefresh(context: Context) {
        val request = PeriodicWorkRequestBuilder<NaamJapWidgetWorker>(6, TimeUnit.HOURS)
            .setInputData(Data.Builder().putString(KEY_OPERATION, OP_REFRESH).build())
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_REFRESH,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancelPeriodicRefresh(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_REFRESH)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface NaamJapWidgetDependencies {
    fun authRepository(): AuthRepository
    fun practiceRepository(): PracticeRepository
}

internal class NaamJapWidgetWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            NaamJapWidgetDependencies::class.java
        )
        val authRepository = dependencies.authRepository()
        val practiceRepository = dependencies.practiceRepository()

        val auth = try {
            withTimeout(30_000L) {
                authRepository.sessionState.first { it !is AuthSessionState.Checking }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            NaamJapWidgetRenderer.updateAll(applicationContext)
            return Result.retry()
        }

        val signedIn = auth as? AuthSessionState.SignedIn
        if (signedIn == null) {
            NaamJapWidgetSnapshotStore.clear(applicationContext)
            NaamJapWidgetRenderer.updateAll(applicationContext)
            return Result.success()
        }

        val userId = signedIn.account.userId
        val currentAccountKey = NaamJapWidgetSnapshotStore.accountKey(userId)
        NaamJapWidgetSnapshotStore.clearIfDifferentAccount(applicationContext, userId)
        NaamJapWidgetRenderer.updateAll(applicationContext)
        val expectedOperationId = inputData.getString("operation_id")
        val expectedAccountKey = inputData.getString("expected_account_key")
        val expectedSessionId = inputData.getString("expected_session_id")
        return try {
            practiceRepository.refresh(ZoneId.systemDefault().id)
            var state = practiceRepository.state.value
            NaamJapWidgetSnapshotStore.write(
                context = applicationContext,
                userId = userId,
                count = state.dashboard.todayCount,
                goal = state.dashboard.dailyGoal,
                activeSessionId = state.activeSession?.id,
                sessionPaused = state.activeSession?.isPaused == true
            )

            when (inputData.getString("operation")) {
                NaamJapWidgetWork.OP_INCREMENT -> {
                    if (expectedAccountKey != currentAccountKey) {
                            NaamJapWidgetSnapshotStore.setStatusForAccount(applicationContext, userId, "Account changed - synced")
                        NaamJapWidgetRenderer.updateAll(applicationContext)
                        return Result.success()
                    }
                    val session = state.activeSession
                    when {
                        session == null -> {
                            NaamJapWidgetSnapshotStore.setStatusForAccount(applicationContext, userId, "Open Jap to start")
                            NaamJapWidgetRenderer.updateAll(applicationContext)
                            return Result.success()
                        }
                        session.isPaused -> {
                            NaamJapWidgetSnapshotStore.setStatusForAccount(applicationContext, userId, "Open Jap to resume")
                            NaamJapWidgetRenderer.updateAll(applicationContext)
                            return Result.success()
                        }
                        session.id != expectedSessionId -> {
                            NaamJapWidgetSnapshotStore.setStatusForAccount(applicationContext, userId, "Session changed - open Jap")
                            NaamJapWidgetRenderer.updateAll(applicationContext)
                            return Result.success()
                        }
                        expectedOperationId.isNullOrBlank() -> {
                            NaamJapWidgetSnapshotStore.setStatusForAccount(applicationContext, userId, "Open app to count")
                            NaamJapWidgetRenderer.updateAll(applicationContext)
                            return Result.failure()
                        }
                    }
                    practiceRepository.applySessionAction(
                        sessionId = session.id,
                        action = SessionAction.INCREMENT,
                        operationId = expectedOperationId
                    )
                    practiceRepository.refresh(ZoneId.systemDefault().id)
                    state = practiceRepository.state.value
                }
                else -> Unit
            }

            NaamJapWidgetSnapshotStore.write(
                context = applicationContext,
                userId = userId,
                count = state.dashboard.todayCount,
                goal = state.dashboard.dailyGoal,
                activeSessionId = state.activeSession?.id,
                sessionPaused = state.activeSession?.isPaused == true,
                status = "Synced"
            )
            NaamJapWidgetRenderer.updateAll(applicationContext)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (error.isTransientNetworkFailure()) {
                NaamJapWidgetSnapshotStore.setStatusForAccount(applicationContext, userId, "Offline - last synced")
                NaamJapWidgetRenderer.updateAll(applicationContext)
                Result.retry()
            } else {
                NaamJapWidgetSnapshotStore.setStatusForAccount(applicationContext, userId, "Sync failed - open app")
                NaamJapWidgetRenderer.updateAll(applicationContext)
                Result.failure()
            }
        }
    }

    private fun Throwable.isTransientNetworkFailure(): Boolean =
        generateSequence(this) { it.cause }.take(8).any {
            it is IOException || it.javaClass.simpleName in setOf(
                "ConnectTimeoutException", "SocketTimeoutException", "UnknownHostException",
                "ConnectException", "HttpRequestTimeoutException"
            )
        }
}
