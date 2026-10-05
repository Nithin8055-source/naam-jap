package com.naamjap.app.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import com.naamjap.app.ui.components.*
import com.naamjap.app.data.remote.NetworkStatus
import com.naamjap.app.data.remote.safeSupabaseError
import com.naamjap.app.ui.theme.JapSpacing
import com.naamjap.app.ui.theme.NaamJapTheme
import androidx.compose.ui.tooling.preview.Preview

data class HistoryUiState(val records: List<com.naamjap.app.domain.repository.PracticeHistoryItem> = emptyList(), val error: String? = null)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: com.naamjap.app.domain.repository.PracticeRepository,
    private val networkStatus: NetworkStatus
) : ViewModel() {
    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state
    init { refresh() }
    fun refresh() { viewModelScope.launch {
        try {
            repository.refresh(java.time.ZoneId.systemDefault().id)
            _state.value = HistoryUiState(repository.loadHistoryPage(0, 100))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _state.value = HistoryUiState(error = safeSupabaseError(error, networkStatus.hasValidatedInternet()))
        }
    } } }
}

@Preview(showBackground = true)
@Composable
private fun HistoryScreenPreview() {
    NaamJapTheme { Text("History") }
}

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selectedDate by rememberSaveable { mutableStateOf(today.toString()) }
    val displayedMonth = YearMonth.parse(month)
    val formatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy") }
    val leadingBlanks = displayedMonth.atDay(1).dayOfWeek.value - 1
    val days = (1..displayedMonth.lengthOfMonth()).toList()
    val cells = List(leadingBlanks) { 0 } + days
    val weeks = cells.chunked(7).map { week -> week + List(7 - week.size) { 0 } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = JapSpacing.lg, top = JapSpacing.md, end = JapSpacing.lg, bottom = 104.dp)) {
        Text("Your practice", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("History", style = MaterialTheme.typography.headlineLarge)
        if (state.error != null) {
            PremiumCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(JapSpacing.md), verticalArrangement = Arrangement.spacedBy(JapSpacing.xs)) {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::refresh) { Text("Retry") }
                }
            }
        }
        Spacer(Modifier.height(JapSpacing.lg))
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(JapSpacing.md), verticalArrangement = Arrangement.spacedBy(JapSpacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(displayedMonth.format(formatter), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { val next = displayedMonth.minusMonths(1); month = next.toString(); selectedDate = next.atDay(1).toString() }, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous month") }
                    IconButton(onClick = { val next = displayedMonth.plusMonths(1); month = next.toString(); selectedDate = next.atDay(1).toString() }, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next month") }
                }
                Row(Modifier.fillMaxWidth()) { listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, day -> Box(Modifier.weight(1f).height(34.dp), contentAlignment = Alignment.Center) { Text(day, modifier = Modifier.semantics { contentDescription = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")[index] }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
                weeks.forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            val date = if (day == 0) null else displayedMonth.atDay(day)
                            val isSelected = date?.toString() == selectedDate
                            val isToday = date == today
                            val foreground = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            Box(Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
                                if (date != null) Box(Modifier.size(48.dp).clickable(role = Role.Button) { selectedDate = date.toString() }.semantics { contentDescription = date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")); selected = isSelected }, contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(36.dp).background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, CircleShape))
                                    Text(day.toString(), color = if (isSelected) foreground else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
                                    if (isToday && !isSelected) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).size(4.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                }
                            }
                        }
                    }
                }
                Text(if (state.records.any { it.date.month == displayedMonth.month && it.date.year == displayedMonth.year }) "Activity is available for this month." else "No activity recorded for this month.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(JapSpacing.lg))
        Text(LocalDate.parse(selectedDate).format(DateTimeFormatter.ofPattern("MMMM d, yyyy")), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(JapSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(JapSpacing.sm)) {
            StatCard("Total Naam Jap", state.records.filter { it.date == LocalDate.parse(selectedDate) }.sumOf { it.count }.toString(), Modifier.weight(1f))
            StatCard("Sessions", state.records.count { it.date == LocalDate.parse(selectedDate) && it.isSession }.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(JapSpacing.lg))
        SectionHeader("Activity")
        Spacer(Modifier.height(JapSpacing.xs))
        GlassSurface(Modifier.fillMaxWidth()) {
            val selectedRecords = state.records.filter { it.date == LocalDate.parse(selectedDate) }
            if (selectedRecords.isEmpty()) {
                EmptyState("No records for this day", "When you add practice records, sessions and manual entries will appear here.")
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    itemsIndexed(selectedRecords) { index, record ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                        SessionRow(record.naamName, record.count.toString(), record.date.toString(), if (record.isSession) "Session" else "Manual")
                    }
                }
            }
        }
    }
}
