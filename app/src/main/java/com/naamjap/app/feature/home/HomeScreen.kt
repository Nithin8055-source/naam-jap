package com.naamjap.app.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.naamjap.app.domain.repository.PracticeRepository
import com.naamjap.app.ui.components.*
import com.naamjap.app.ui.theme.JapSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(private val repository: PracticeRepository) : ViewModel() {
    private val _history = MutableStateFlow(emptyList<com.naamjap.app.domain.repository.PracticeHistoryItem>())
    val history = _history.asStateFlow()
    val data = repository.state
    init { viewModelScope.launch { runCatching { repository.refresh(java.time.ZoneId.systemDefault().id) }; runCatching { _history.value = repository.loadHistoryPage(0, 5) } } }
}

@Composable
fun HomeScreen(onNavigate: (String) -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val recent by viewModel.history.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("A quieter moment", style = MaterialTheme.typography.headlineMedium)
        Text("Take a breath. Begin with one name.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        GlassSurface(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) {
            DailyCountDisplay(data.dashboard.todayCount.toString(), "Today's Naam Jap")
            Spacer(Modifier.height(12.dp))
            val goal = data.dashboard.dailyGoal.coerceAtLeast(1)
            val progress = (data.dashboard.todayCount.toDouble() / goal).coerceIn(0.0, 1.0)
            GoalProgressCard("${data.dashboard.dailyGoal} repetitions", (progress * 100).toInt(), "${(goal-data.dashboard.todayCount).coerceAtLeast(0)} remaining", progress.toFloat())
        } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Current streak", "${data.dashboard.currentStreak} days", Modifier.weight(1f))
            StatCard("Lifetime count", data.dashboard.lifetimeCount.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Sessions today", data.dashboard.sessionsToday.toString(), Modifier.weight(1f))
            StatCard("Naam types", data.naamTypes.size.toString(), Modifier.weight(1f))
        }
        SectionHeader("Recent activity", action = "View all", onAction = { onNavigate("history") })
        if (recent.isEmpty()) EmptyState("Your practice begins here", "Completed sessions and manual records appear here.")
        else recent.forEach { SessionRow(it.naamName, it.count.toString(), it.date.toString(), if (it.isSession) "Session" else "Manual") }
        data.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
