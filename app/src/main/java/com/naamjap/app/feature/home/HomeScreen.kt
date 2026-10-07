package com.naamjap.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naamjap.app.domain.repository.PracticeHistoryItem
import com.naamjap.app.domain.repository.PracticeRepository
import com.naamjap.app.ui.components.DailyCountDisplay
import com.naamjap.app.ui.components.EmptyState
import com.naamjap.app.ui.components.GlassSurface
import com.naamjap.app.ui.components.GoalProgressCard
import com.naamjap.app.ui.components.PremiumCard
import com.naamjap.app.ui.components.QuickActionItem
import com.naamjap.app.ui.components.SectionHeader
import com.naamjap.app.ui.components.SessionRow
import com.naamjap.app.ui.components.StatCard
import com.naamjap.app.ui.components.formatCount
import com.naamjap.app.ui.theme.JapSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val recentRecords: List<PracticeHistoryItem> = emptyList(),
    val isLoading: Boolean = true,
    val historyError: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: PracticeRepository
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()
    val data = repository.state

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, historyError = null)
            try {
                repository.refresh(ZoneId.systemDefault().id)
                _state.value = HomeUiState(recentRecords = repository.loadHistoryPage(0, 5), isLoading = false)
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(isLoading = false)
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    historyError = repository.state.value.error ?: "Recent activity couldn't be loaded. Try again."
                )
            }
        }
    }
}

@Composable
fun HomeScreen(onNavigate: (String) -> Unit, displayName: String? = null, viewModel: HomeViewModel = hiltViewModel()) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = JapSpacing.lg, vertical = JapSpacing.md),
        verticalArrangement = Arrangement.spacedBy(JapSpacing.md)
    ) {
        Text("Good morning", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(displayName?.takeIf(String::isNotBlank) ?: "Welcome back", style = MaterialTheme.typography.headlineMedium)
        Text("Take a breath. Begin with one name.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (!data.hasLoaded && data.isLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        } else if (!data.hasLoaded) {
            PremiumCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(JapSpacing.md), verticalArrangement = Arrangement.spacedBy(JapSpacing.xs)) {
                    Text(data.error ?: "Your practice data isn't available yet.", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::refresh) { Text("Retry") }
                }
            }
        } else {
            GlassSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(JapSpacing.md)) {
                    DailyCountDisplay(formatCount(data.dashboard.todayCount), "Today's Naam Jap")
                    Spacer(Modifier.height(JapSpacing.sm))
                    val goal = data.dashboard.dailyGoal.coerceAtLeast(1)
                    val progress = (data.dashboard.todayCount.toDouble() / goal).coerceIn(0.0, 1.0)
                    GoalProgressCard(
                        "${data.dashboard.dailyGoal} repetitions",
                        (progress * 100).toInt(),
                        "${(goal - data.dashboard.todayCount).coerceAtLeast(0)} remaining",
                        progress.toFloat()
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(JapSpacing.xs)) {
                StatCard("Current streak", "${data.dashboard.currentStreak} days", Modifier.weight(1f))
                StatCard("Lifetime count", formatCount(data.dashboard.lifetimeCount), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(JapSpacing.xs)) {
                StatCard("Sessions today", data.dashboard.sessionsToday.toString(), Modifier.weight(1f))
                StatCard("Naam types", data.naamTypes.size.toString(), Modifier.weight(1f))
            }

            SectionHeader("Quick actions")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(JapSpacing.sm)) {
                QuickActionItem(
                    label = "Start Jap",
                    description = "Begin a live session",
                    onClick = { onNavigate("jap") },
                    icon = { androidx.compose.material3.Icon(Icons.Outlined.SelfImprovement, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.weight(1f)
                )
                QuickActionItem(
                    label = "Add record",
                    description = "Enter a daily count",
                    onClick = { onNavigate("manual-record") },
                    icon = { androidx.compose.material3.Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.weight(1f)
                )
            }

            SectionHeader("Recent activity", action = "View all", onAction = { onNavigate("history") })
            when {
                state.isLoading && state.recentRecords.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                state.historyError != null -> PremiumCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(JapSpacing.md)) {
                        Text(state.historyError.orEmpty(), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = viewModel::refresh) { Text("Retry") }
                    }
                }
                state.recentRecords.isEmpty() -> EmptyState("Your practice begins here", "Completed sessions and manual records appear here.")
                else -> state.recentRecords.forEach {
                    SessionRow(it.naamName, formatCount(it.count), it.date.toString(), if (it.isSession) "Session" else "Manual")
                }
            }
        }
    }
}
