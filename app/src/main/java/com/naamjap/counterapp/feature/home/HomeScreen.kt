package com.naamjap.counterapp.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naamjap.counterapp.domain.repository.PracticeHistoryItem
import com.naamjap.counterapp.domain.repository.PracticeRepository
import com.naamjap.counterapp.ui.components.EmptyState
import com.naamjap.counterapp.ui.components.PremiumCard
import com.naamjap.counterapp.ui.components.PremiumPullToRefreshBox
import com.naamjap.counterapp.ui.components.SectionHeader
import com.naamjap.counterapp.ui.components.SessionRow
import com.naamjap.counterapp.ui.components.StatCard
import com.naamjap.counterapp.ui.components.formatCount
import com.naamjap.counterapp.ui.components.navigationContentBottomInset
import com.naamjap.counterapp.ui.theme.JapSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val recentRecords: List<PracticeHistoryItem> = emptyList(),
    val isLoading: Boolean = true,
    val historyError: String? = null,
    val isUserRefreshing: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: PracticeRepository
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()
    val data = repository.state
    private var refreshJob: kotlinx.coroutines.Job? = null

    fun refresh(userInitiated: Boolean = false) {
        if (refreshJob?.isActive == true) {
            if (userInitiated) _state.value = _state.value.copy(isUserRefreshing = true)
            return
        }
        refreshJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = _state.value.recentRecords.isEmpty(),
                historyError = null,
                isUserRefreshing = userInitiated
            )
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
            } finally {
                _state.value = _state.value.copy(isUserRefreshing = false)
            }
        }
    }
}

@Composable
fun HomeScreen(onNavigate: (String) -> Unit, displayName: String? = null, viewModel: HomeViewModel = hiltViewModel()) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    PremiumPullToRefreshBox(
        isRefreshing = state.isUserRefreshing,
        onRefresh = { viewModel.refresh(userInitiated = true) },
        modifier = Modifier.fillMaxSize()
    ) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = JapSpacing.lg, vertical = JapSpacing.md)
            .padding(bottom = navigationContentBottomInset()),
        verticalArrangement = Arrangement.spacedBy(JapSpacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val name = displayName?.takeIf(String::isNotBlank) ?: "there"
            val greeting = when (LocalTime.now().hour) {
                in 5..11 -> "Good morning"
                in 12..16 -> "Good afternoon"
                else -> "Good evening"
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(greeting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JapSpacing.xs)) {
                    Text(name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Icon(
                        imageVector = Icons.Outlined.WbSunny,
                        contentDescription = "Sun",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            IconButton(onClick = { onNavigate("settings") }) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Open settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text("Take a breath. Begin with one name.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (!data.hasLoaded && data.isLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        } else if (!data.hasLoaded) {
            PremiumCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(JapSpacing.md), verticalArrangement = Arrangement.spacedBy(JapSpacing.xs)) {
                    Text(data.error ?: "Your practice data isn't available yet.", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewModel.refresh() }) { Text("Retry") }
                }
            }
        } else {
            val goal = data.dashboard.dailyGoal.coerceAtLeast(1L)
            val progress = (data.dashboard.todayCount.toDouble() / goal).coerceIn(0.0, 1.0)
            val remaining = (goal - data.dashboard.todayCount).coerceAtLeast(0L)
            PremiumCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(JapSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(JapSpacing.sm)
                ) {
                    Text("Today's Total", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(formatCount(data.dashboard.todayCount), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Goal: ${formatCount(goal)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    LinearProgressIndicator(
                        progress = { progress.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        if (remaining == 0L) "Goal reached · keep going" else "${formatCount(remaining)} remaining",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                HomeQuickActionCard(
                    label = "Start Jap",
                    description = "Begin a live session",
                    onClick = { onNavigate("jap") },
                    icon = { Icon(Icons.Outlined.SelfImprovement, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp)) },
                    modifier = Modifier.weight(1f)
                )
                HomeQuickActionCard(
                    label = "Add record",
                    description = "Enter a daily count",
                    onClick = { onNavigate("manual-record") },
                    icon = { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }
            SectionHeader("Recent activity", action = "View all", onAction = { onNavigate("history") })
            when {
                state.isLoading && state.recentRecords.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                state.historyError != null -> PremiumCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(JapSpacing.md)) {
                        Text(state.historyError.orEmpty(), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { viewModel.refresh() }) { Text("Retry") }
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
}
@Composable
private fun HomeQuickActionCard(
    label: String,
    description: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(128.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f)),
        shadowElevation = 2.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(JapSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(JapSpacing.xs)
        ) {
            Surface(modifier = Modifier.size(44.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { icon() }
            }
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
