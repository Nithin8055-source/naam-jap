package com.naamjap.app.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.naamjap.app.ui.components.*
import com.naamjap.app.ui.theme.JapSpacing
import com.naamjap.app.ui.theme.NaamJapTheme
import com.naamjap.app.navigation.Destination
import com.naamjap.app.R
import androidx.compose.ui.tooling.preview.Preview

data class HomeUiState(val greeting: String = "Good morning", val todayCount: Int = 0)

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState
}

@Composable
fun HomeScreen(onNavigate: (String) -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.bg_sunrise), null, Modifier.fillMaxSize().alpha(.18f), contentScale = ContentScale.Crop)
        Image(painterResource(R.drawable.ic_mandala), null, Modifier.align(Alignment.TopEnd).offset(x = 42.dp, y = 112.dp).size(220.dp).alpha(.045f), contentScale = ContentScale.Fit)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = JapSpacing.lg, top = JapSpacing.md, end = JapSpacing.lg, bottom = 104.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NaamJapLogo(Modifier.size(76.dp).padding(end = 8.dp))
            Column(Modifier.weight(1f)) {
                Text(state.greeting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("A quieter moment", style = MaterialTheme.typography.headlineMedium)
                Text("Take a breath. Begin with one name.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PremiumIconButton("Open settings", onClick = { onNavigate(Destination.Settings.route) }) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(JapSpacing.xl))
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(JapSpacing.lg), verticalArrangement = Arrangement.spacedBy(JapSpacing.md)) {
                DailyCountDisplay(count = state.todayCount.toString(), supportingLabel = "Today's Naam Jap")
                GoalProgressCard(goal = "Daily goal not set", percent = 0, remaining = "Set a goal when you are ready.", progress = 0f, showPercent = false)
            }
        }
        Spacer(Modifier.height(JapSpacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(JapSpacing.sm)) {
            StatCard("Current streak", "—", Modifier.weight(1f))
            StatCard("Lifetime count", "—", Modifier.weight(1f))
            StatCard("Sessions today", "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(JapSpacing.xl))
        SectionHeader("Quick actions")
        Spacer(Modifier.height(JapSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(JapSpacing.sm)) {
            QuickActionItem("Start Jap", "Begin a session", onClick = { onNavigate(Destination.Jap.route) }, icon = { Icon(Icons.Default.SelfImprovement, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }, modifier = Modifier.weight(1f))
            QuickActionItem("Add record", "Manual entry", onClick = { onNavigate(Destination.ManualRecord.route) }, icon = { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(JapSpacing.sm))
        QuickActionItem("View history", "Browse daily practice", onClick = { onNavigate(Destination.History.route) }, icon = { Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary) })
        Spacer(Modifier.height(JapSpacing.xl))
        OrnamentalDivider(Modifier.fillMaxWidth().height(24.dp))
        Spacer(Modifier.height(JapSpacing.md))
        SectionHeader("Recent activity", action = "View all", onAction = { onNavigate(Destination.History.route) })
        Spacer(Modifier.height(JapSpacing.xs))
        GlassSurface(Modifier.fillMaxWidth()) { EmptyState("Your practice begins here", "Completed sessions will appear in this space.") }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    NaamJapTheme { HomeScreen(onNavigate = {}, viewModel = HomeViewModel()) }
}
