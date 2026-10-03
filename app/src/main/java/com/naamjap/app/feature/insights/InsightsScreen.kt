package com.naamjap.app.feature.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.naamjap.app.ui.components.*
import com.naamjap.app.ui.theme.JapSpacing
import com.naamjap.app.ui.theme.NaamJapTheme
import androidx.compose.ui.tooling.preview.Preview

data class InsightsUiState(val hasPracticeData: Boolean = false)

@HiltViewModel
class InsightsViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(InsightsUiState())
    val state: StateFlow<InsightsUiState> = _state
}

@Preview(showBackground = true)
@Composable
private fun InsightsScreenPreview() {
    NaamJapTheme { InsightsScreen(viewModel = InsightsViewModel()) }
}

@Composable
fun InsightsScreen(viewModel: InsightsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableStateOf("Week") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = JapSpacing.lg, top = JapSpacing.md, end = JapSpacing.lg, bottom = 104.dp)) {
        Text("Your journey", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Insights", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(JapSpacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(JapSpacing.xs)) {
            listOf("Week", "Month", "Year").forEach { item ->
                val selected = range == item
                Surface(onClick = { range = item }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { this.selected = selected }, shape = CircleShape, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {
                    Text(item, Modifier.fillMaxWidth().heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(JapSpacing.md))
        if (!state.hasPracticeData) {
            PremiumCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(JapSpacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JapSpacing.xs)) {
                        Text("Weekly count", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) { Text("SAMPLE PREVIEW", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall) }
                    }
                    Text("Illustrative values only · no practice recorded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(JapSpacing.md))
                    PreviewBarChart(range)
                }
            }
            Spacer(Modifier.height(JapSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(JapSpacing.sm)) {
                StatCard("Total Naam Jap", "—", Modifier.weight(1f))
                StatCard("Current streak", "—", Modifier.weight(1f))
            }
            Spacer(Modifier.height(JapSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(JapSpacing.sm)) {
                StatCard("Longest streak", "—", Modifier.weight(1f))
                StatCard("Daily average", "—", Modifier.weight(1f))
            }
            Spacer(Modifier.height(JapSpacing.md))
            EmptyState("Your insights start with your practice", "Once you record practice, this view will summarize your own counts and streaks.")
        } else {
            EmptyState("No insights yet", "Insights will appear after your practice records are available.")
        }
    }
}

@Composable
private fun PreviewBarChart(range: String) {
    val labels = when (range) { "Month" -> listOf("W1", "W2", "W3", "W4"); "Year" -> listOf("Jan", "Mar", "May", "Jul", "Sep", "Nov"); else -> listOf("M", "T", "W", "T", "F", "S", "S") }
    val sampleValues = when (range) { "Month" -> listOf(.36f, .68f, .52f, .88f); "Year" -> listOf(.31f, .45f, .56f, .42f, .72f, .82f); else -> listOf(.34f, .72f, .50f, .81f, .68f, .92f, .75f) }
    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(JapSpacing.xs)) {
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                listOf("5k", "2.5k", "0").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = textColor) }
            }
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                val baseline = size.height
                listOf(.05f, .5f, .95f).forEach { fraction ->
                    val y = size.height * fraction
                    drawLine(gridColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                val slot = size.width / sampleValues.size
                sampleValues.forEachIndexed { index, value ->
                    val barHeight = (size.height - 8.dp.toPx()) * value
                    drawRoundRect(color = barColor.copy(alpha = if (index == sampleValues.lastIndex) .92f else .68f), topLeft = androidx.compose.ui.geometry.Offset(index * slot + slot * .22f, baseline - barHeight), size = androidx.compose.ui.geometry.Size(slot * .54f, barHeight), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Fill)
                }
            }
        }
        Spacer(Modifier.height(JapSpacing.xs))
        Row(Modifier.fillMaxWidth().padding(start = 26.dp), horizontalArrangement = Arrangement.SpaceAround) { labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = textColor) } }
        Spacer(Modifier.height(JapSpacing.sm))
        Text("Sample scale shown for design preview", style = MaterialTheme.typography.labelSmall, color = textColor)
    }
}
