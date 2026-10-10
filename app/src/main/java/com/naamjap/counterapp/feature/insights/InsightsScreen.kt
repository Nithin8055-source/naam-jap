package com.naamjap.counterapp.feature.insights

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import com.naamjap.counterapp.domain.repository.PracticeHistoryItem
import com.naamjap.counterapp.domain.repository.PracticeRepository
import com.naamjap.counterapp.data.remote.NetworkStatus
import com.naamjap.counterapp.data.remote.safeSupabaseError
import com.naamjap.counterapp.ui.components.EmptyState
import com.naamjap.counterapp.ui.components.PremiumCard
import com.naamjap.counterapp.ui.components.StatCard
import com.naamjap.counterapp.ui.components.PremiumPullToRefreshBox
import com.naamjap.counterapp.ui.components.JapCircularProgressIndicator
import com.naamjap.counterapp.ui.components.formatCount
import com.naamjap.counterapp.ui.components.navigationContentBottomInset
import com.naamjap.counterapp.ui.theme.JapSpacing
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
    val error: String? = null,
    val isUserRefreshing: Boolean = false
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val repository: PracticeRepository,
    private val networkStatus: NetworkStatus
) : ViewModel() {
    private val _state = MutableStateFlow(InsightsUiState())
    val state = _state.asStateFlow()
    val practice = repository.state
    private var refreshJob: kotlinx.coroutines.Job? = null

    init {
        refresh()
    }

    fun refresh(userInitiated: Boolean = false) {
        if (refreshJob?.isActive == true) {
            if (userInitiated) _state.value = _state.value.copy(isUserRefreshing = true)
            return
        }
        refreshJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = _state.value.recentRecords.isEmpty(),
                error = null,
                isUserRefreshing = userInitiated
            )
            try {
                repository.refresh(ZoneId.systemDefault().id)
                val records = mutableListOf<PracticeHistoryItem>()
                val yearStart = LocalDate.now().withMonth(1).withDayOfMonth(1)
                val yearEnd = yearStart.withMonth(12).withDayOfMonth(31)
                var offset = 0
                while (true) {
                    val page = repository.loadHistoryPage(offset, 100, yearStart, yearEnd)
                    records += page
                    if (page.size < 100) break
                    offset += page.size
                }
                _state.value = _state.value.copy(
                    isLoading = false,
                    recentRecords = records,
                    error = null
                )
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(isLoading = false)
                throw cancelled
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = safeSupabaseError(error, networkStatus.hasValidatedInternet())
                )
            } finally {
                _state.value = _state.value.copy(isUserRefreshing = false)
            }
        }
    }
}

@Composable
fun InsightsScreen(viewModel: InsightsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val practice by viewModel.practice.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableStateOf(InsightRange.WEEK) }
    var selectedBar by rememberSaveable { mutableStateOf(-1) }
    val chart = buildInsightChart(state.recentRecords, range, LocalDate.now())
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    PremiumPullToRefreshBox(
        isRefreshing = state.isUserRefreshing,
        onRefresh = { viewModel.refresh(userInitiated = true) },
        modifier = Modifier.fillMaxSize()
    ) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = JapSpacing.lg, top = JapSpacing.md, end = JapSpacing.lg)
            .padding(bottom = navigationContentBottomInset()),
        verticalArrangement = Arrangement.spacedBy(JapSpacing.md)
    ) {
        Text("Your journey", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Insights", style = MaterialTheme.typography.headlineLarge)

        if (state.error != null) {
            PremiumCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(JapSpacing.md), verticalArrangement = Arrangement.spacedBy(JapSpacing.xs)) {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewModel.refresh() }, enabled = !state.isLoading) { Text("Try again") }
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

            PremiumCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(JapSpacing.md), verticalArrangement = Arrangement.spacedBy(JapSpacing.xs)) {
                    Text("Total Count", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        formatCount(practice.dashboard.lifetimeCount),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text("Naam Jap saved in your account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(JapSpacing.sm)) {
                StatCard("Current streak", "${practice.dashboard.currentStreak} days", Modifier.weight(1f))
                StatCard("Sessions today", practice.dashboard.sessionsToday.toString(), Modifier.weight(1f))
            }

            PremiumCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(JapSpacing.md), verticalArrangement = Arrangement.spacedBy(JapSpacing.md)) {
                    Text("Recorded practice", style = MaterialTheme.typography.titleMedium)
                    val periodTotal = chart.values.fold(0L) { total, value ->
                        if (Long.MAX_VALUE - total < value) Long.MAX_VALUE else total + value
                    }
                    Text(
                        "${range.periodDescription}: ${formatCount(periodTotal)} Naam Jap",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(JapSpacing.xs)) {
                        InsightRange.entries.forEach { item ->
                            TextButton(onClick = { range = item; selectedBar = -1 }, modifier = Modifier.weight(1f)) {
                                Text(
                                    item.label,
                                    color = if (range == item) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (chart.allCounts.none { it > 0L }) {
                        EmptyState("No practice in this period", "Completed sessions and manual records will appear here.")
                    } else {
                        ActivityBarChart(chart, range, selectedBar, onSelectBar = { selectedBar = it })
                        Text(
                            "Chart totals use your saved practice records.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val goal = practice.dashboard.dailyGoal.coerceAtLeast(1L)
            val progress = (practice.dashboard.todayCount.toDouble() / goal).coerceIn(0.0, 1.0)
            PremiumCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(JapSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(JapSpacing.md)
                ) {
                    JapCircularProgressIndicator(
                        progress = progress.toFloat(),
                        modifier = Modifier.size(84.dp),
                        strokeWidth = 7.dp
                    ) {
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(JapSpacing.xs)) {
                        Text("Progress", style = MaterialTheme.typography.titleMedium)
                        Text("Today's Goal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${formatCount(practice.dashboard.todayCount)} / ${formatCount(goal)}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (practice.dashboard.todayCount >= goal) "Goal reached · keep going" else "${formatCount((goal - practice.dashboard.todayCount).coerceAtLeast(0L))} remaining",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    }
}

private enum class InsightRange(val label: String) {
    WEEK("Week"), MONTH("Month"), YEAR("Year");

    val periodDescription: String
        get() = when (this) {
            WEEK -> "Last 7 days"
            MONTH -> "This month"
            YEAR -> LocalDate.now().year.toString()
        }
}

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
            .fold(0L) { total, item ->
                if (Long.MAX_VALUE - total < item.count) Long.MAX_VALUE else total + item.count
            }
    }
    return InsightChart(labels, values)
}

@Composable
private fun ActivityBarChart(
    chart: InsightChart,
    range: InsightRange,
    selectedIndex: Int,
    onSelectBar: (Int) -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxValue = chart.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val isYear = range == InsightRange.YEAR
    val monthWidth = 52.dp
    val yearGraphWidth = monthWidth * chart.values.size.toFloat()

    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(JapSpacing.xs)) {
            Column(Modifier.width(48.dp).height(148.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(compactCount(maxValue), style = MaterialTheme.typography.labelSmall, color = textColor, maxLines = 1)
                Text(compactCount(maxValue / 2), style = MaterialTheme.typography.labelSmall, color = textColor, maxLines = 1)
                Text("0", style = MaterialTheme.typography.labelSmall, color = textColor)
            }
            if (isYear) {
                Column(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    InsightBars(chart.values, selectedIndex, onSelectBar, Modifier.width(yearGraphWidth))
                    InsightBarLabels(chart.labels, selectedIndex, textColor, Modifier.width(yearGraphWidth))
                }
            } else {
                Column(Modifier.weight(1f)) {
                    InsightBars(chart.values, selectedIndex, onSelectBar, Modifier.fillMaxWidth())
                    InsightBarLabels(chart.labels, selectedIndex, textColor, Modifier.fillMaxWidth())
                }
            }
        }
        chart.labels.getOrNull(selectedIndex)?.let { label ->
            Text(
                "$label: ${formatCount(chart.values[selectedIndex])} Naam Jap",
                Modifier.fillMaxWidth().padding(top = JapSpacing.xs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun InsightBars(values: List<Long>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val barColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.primaryContainer
    )
    val primaryBarColor = MaterialTheme.colorScheme.primary
    val selectedBarColor = MaterialTheme.colorScheme.secondary
    val maxValue = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val animatedHeights = values.mapIndexed { index, value ->
        animateFloatAsState(
            targetValue = (value.toDouble() / maxValue).toFloat(),
            animationSpec = tween(durationMillis = 420),
            label = "insight bar $index"
        ).value
    }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier.height(148.dp).pointerInput(values.size) {
            detectTapGestures { position ->
                val slotWidth = size.width.toFloat() / values.size.coerceAtLeast(1)
                onSelect((position.x / slotWidth).toInt().coerceIn(values.indices))
            }
        }
    ) {
        val slot = size.width / values.size.coerceAtLeast(1)
        listOf(.04f, .5f, .96f).forEach { fraction ->
            val y = size.height * fraction
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        values.forEachIndexed { index, value ->
            val height = (size.height - 8.dp.toPx()) * animatedHeights[index]
            if (height > 0f) {
                val barColor = barColors[index % barColors.size]
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = if (index == selectedIndex) {
                            listOf(selectedBarColor, primaryBarColor)
                        } else {
                            listOf(barColor.copy(alpha = .78f), barColor.copy(alpha = .98f))
                        },
                        startY = size.height - height,
                        endY = size.height
                    ),
                    topLeft = Offset(index * slot + slot * .22f, size.height - height),
                    size = Size(slot * .56f, height.coerceAtLeast(3.dp.toPx())),
                    cornerRadius = CornerRadius(5.dp.toPx()),
                    style = Fill
                )
            }
        }
    }
}

@Composable
private fun InsightBarLabels(labels: List<String>, selectedIndex: Int, textColor: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly) {
        labels.forEachIndexed { index, label ->
            androidx.compose.foundation.layout.Box(
                Modifier.weight(1f).padding(top = JapSpacing.xs),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == selectedIndex) MaterialTheme.colorScheme.primary else textColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

private fun compactCount(value: Long): String = when {
    value >= 1_000_000_000 -> "${value / 1_000_000_000}B"
    value >= 1_000_000 -> "${value / 1_000_000}M"
    value >= 1_000 -> "${value / 1_000}K"
    else -> value.toString()
}
