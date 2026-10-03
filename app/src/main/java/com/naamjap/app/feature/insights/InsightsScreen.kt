package com.naamjap.app.feature.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.naamjap.app.domain.repository.PracticeHistoryItem
import com.naamjap.app.domain.repository.PracticeRepository
import com.naamjap.app.ui.components.EmptyState
import com.naamjap.app.ui.components.PremiumCard
import com.naamjap.app.ui.components.StatCard
import com.naamjap.app.ui.theme.JapSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InsightsUiState(
    val isLoading: Boolean = true,
    val recentRecords: List<PracticeHistoryItem> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val repository: PracticeRepository
) : ViewModel() {
    private val _state = MutableStateFlow(InsightsUiState())
    val state = _state.asStateFlow()
    val practice = repository.state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                repository.refresh(ZoneId.systemDefault().id)
                _state.value = _state.value.copy(
                    isLoading = false,
                    recentRecords = repository.loadHistoryPage(offset = 0, limit = 100),
                    error = null
                )
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(isLoading = false)
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Practice insights couldn't be loaded. Check your connection and try again."
                )
            }
        }
    }
}

@Composable
fun InsightsScreen(viewModel: InsightsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val practice by viewModel.practice.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableStateOf(InsightRange.WEEK) }
    val chart = buildInsightChart(state.recentRecords, range, LocalDate.now())

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = JapSpacing.lg, top = JapSpacing.md, end = JapSpacing.lg, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(JapSpacing.md)
    ) {
        Text("Your journey", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Insights", style = MaterialTheme.typography.headlineLarge)

        if (state.error != null) {
            PremiumCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(JapSpacing.md), verticalArrangement = Arrangement.spacedBy(JapSpacing.xs)) {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::refresh, enabled = !state.isLoading) { Text("Try again") }
                }
            }
        }

        if (!practice.hasLoaded) {
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            } else if (state.error != null) {
                EmptyState("Practice data unavailable", "Retry when your account and network are available.")
            }
        } else {
            if (practice.error != null && state.error == null) {
                Text(
                    "Dashboard totals couldn't be refreshed. Showing the latest data available on this screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(JapSpacing.sm)) {
                StatCard("Total Naam Jap", practice.dashboard.lifetimeCount.toString(), Modifier.weight(1f))
                StatCard("Current streak", "${practice.dashboard.currentStreak} days", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(JapSpacing.sm)) {
                StatCard("Today", practice.dashboard.todayCount.toString(), Modifier.weight(1f))
                StatCard("Sessions today", practice.dashboard.sessionsToday.toString(), Modifier.weight(1f))
            }

            PremiumCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(JapSpacing.md), verticalArrangement = Arrangement.spacedBy(JapSpacing.md)) {
                    Text("Recorded practice", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(JapSpacing.xs)) {
                        InsightRange.entries.forEach { item ->
                            TextButton(onClick = { range = item }, modifier = Modifier.weight(1f)) {
                                Text(
                                    item.label,
                                    color = if (range == item) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (chart.allCounts.sum() == 0L) {
                        EmptyState("No practice in this period", "Completed sessions and manual records will appear here.")
                    } else {
                        ActivityBarChart(chart)
                        Text(
                            "Chart totals use your 100 most recent saved records.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private enum class InsightRange(val label: String) { WEEK("Week"), MONTH("Month"), YEAR("Year") }

private data class InsightChart(val labels: List<String>, val values: List<Long>) {
    val allCounts: List<Long> get() = values
}

private fun buildInsightChart(records: List<PracticeHistoryItem>, range: InsightRange, today: LocalDate): InsightChart {
    val labels: List<String>
    val periodStarts: List<LocalDate>
    val periodEnds: List<LocalDate>
    when (range) {
        InsightRange.WEEK -> {
            val dates = (6 downTo 0).map { today.minusDays(it.toLong()) }
            labels = dates.map { it.format(DateTimeFormatter.ofPattern("EE")) }
            periodStarts = dates
            periodEnds = dates
        }
        InsightRange.MONTH -> {
            val month = YearMonth.from(today)
            val starts = (1..month.lengthOfMonth() step 7).map { month.atDay(it) }
            labels = starts.map { it.dayOfMonth.toString() }
            periodStarts = starts
            periodEnds = starts.mapIndexed { index, start -> starts.getOrNull(index + 1)?.minusDays(1) ?: month.atEndOfMonth() }
        }
        InsightRange.YEAR -> {
            val starts = (1..12).map { today.withDayOfMonth(1).withMonth(it) }
            labels = starts.map { it.format(DateTimeFormatter.ofPattern("MMM")) }
            periodStarts = starts
            periodEnds = starts.map { it.withDayOfMonth(it.lengthOfMonth()) }
        }
    }
    val values = periodStarts.indices.map { index ->
        records.asSequence()
            .filter { it.date >= periodStarts[index] && it.date <= periodEnds[index] }
            .sumOf(PracticeHistoryItem::count)
    }
    return InsightChart(labels, values)
}

@Composable
private fun ActivityBarChart(chart: InsightChart) {
    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxValue = chart.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Column {
        Row(Modifier.fillMaxWidth().height(142.dp), horizontalArrangement = Arrangement.spacedBy(JapSpacing.xs)) {
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Text(maxValue.toString(), style = MaterialTheme.typography.labelSmall, color = textColor)
                Text((maxValue / 2).toString(), style = MaterialTheme.typography.labelSmall, color = textColor)
                Text("0", style = MaterialTheme.typography.labelSmall, color = textColor)
            }
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                listOf(.05f, .5f, .95f).forEach { fraction ->
                    val y = size.height * fraction
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                val slot = size.width / chart.values.size.coerceAtLeast(1)
                chart.values.forEachIndexed { index, value ->
                    val height = (size.height - 8.dp.toPx()) * (value.toDouble() / maxValue).toFloat()
                    if (height > 0f) {
                        drawRoundRect(
                            color = barColor.copy(alpha = if (index == chart.values.lastIndex) .92f else .68f),
                            topLeft = Offset(index * slot + slot * .2f, size.height - height),
                            size = Size(slot * .6f, height),
                            cornerRadius = CornerRadius(5.dp.toPx()),
                            style = Fill
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(JapSpacing.xs))
        Row(Modifier.fillMaxWidth().padding(start = 34.dp), horizontalArrangement = Arrangement.SpaceAround) {
            chart.labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = textColor, textAlign = TextAlign.Center) }
        }
    }
}
