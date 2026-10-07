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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
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
import com.naamjap.app.domain.repository.PracticeHistoryItem
import com.naamjap.app.ui.theme.JapSpacing
import com.naamjap.app.ui.theme.NaamJapTheme
import androidx.compose.ui.tooling.preview.Preview

data class HistoryUiState(
    val records: List<com.naamjap.app.domain.repository.PracticeHistoryItem> = emptyList(),
    val error: String? = null,
    val isLoading: Boolean = true,
    val deletingId: String? = null,
    val deletionError: String? = null,
    val lastDeletedId: String? = null
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: com.naamjap.app.domain.repository.PracticeRepository,
    private val auth: com.naamjap.app.domain.repository.AuthRepository,
    private val networkStatus: NetworkStatus
) : ViewModel() {
    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state
    init { refresh() }
    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                repository.refresh(java.time.ZoneId.systemDefault().id)
                _state.value = _state.value.copy(
                    records = loadAllHistory(),
                    isLoading = false,
                    error = null
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                _state.value = _state.value.copy(isLoading = false)
                throw cancelled
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = safeSupabaseError(
                        error,
                        networkStatus.hasValidatedInternet()
                    )
                )
            }
        }
    }

    fun deleteSession(item: PracticeHistoryItem, password: String) {
        val sessionId = item.sessionId
        if (sessionId == null) {
            _state.value = _state.value.copy(deletionError = "This history item is missing its session link. Refresh and try again.")
            return
        }
        deleteWithPassword(item.id, password) { repository.deleteSession(sessionId) }
    }
    fun deleteManualRecord(id: String, password: String) = deleteWithPassword(id, password) { repository.deleteManualRecord(id) }
    fun clearDeletionError() { _state.value = _state.value.copy(deletionError = null) }

    private fun deleteWithPassword(id: String, password: String, operation: suspend () -> Unit) = viewModelScope.launch {
        _state.value = _state.value.copy(deletingId = id, deletionError = null, lastDeletedId = null)
        try {
            auth.reauthenticate(password)
            operation()
            _state.value = _state.value.copy(
                records = _state.value.records.filterNot { it.id == id },
                deletingId = null,
                deletionError = null,
                lastDeletedId = id,
                error = null
            )
            try {
                _state.value = _state.value.copy(records = loadAllHistory())
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (refreshError: Exception) {
                // The RPC already confirmed deletion; report a history refresh issue separately.
                _state.value = _state.value.copy(error = safeSupabaseError(refreshError, networkStatus.hasValidatedInternet()))
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            _state.value = _state.value.copy(deletingId = null)
            throw cancelled
        } catch (error: Exception) {
            val rawMessage = error.message.orEmpty().lowercase()
            val message = if ("invalid login credentials" in rawMessage || "invalid credentials" in rawMessage) {
                "That password is incorrect. Try again."
            } else safeSupabaseError(error, networkStatus.hasValidatedInternet())
            _state.value = _state.value.copy(deletingId = null, deletionError = message)
        }
    }

    private suspend fun loadAllHistory(): List<com.naamjap.app.domain.repository.PracticeHistoryItem> {
        val all = mutableListOf<com.naamjap.app.domain.repository.PracticeHistoryItem>()
        var offset = 0
        while (true) {
            val page = repository.loadHistoryPage(offset, 100)
            all += page
            if (page.size < 100) return all
            offset += page.size
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HistoryScreenPreview() {
    NaamJapTheme { Text("History") }
}

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<com.naamjap.app.domain.repository.PracticeHistoryItem?>(null) }
    var deletePassword by remember { mutableStateOf("") }
    LaunchedEffect(state.lastDeletedId) {
        if (state.lastDeletedId != null && state.lastDeletedId == deleteTarget?.id) {
            deleteTarget = null
            deletePassword = ""
        }
    }
    val today = remember { LocalDate.now() }
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selectedDate by rememberSaveable { mutableStateOf(today.toString()) }
    var activityFilter by rememberSaveable { mutableStateOf("Daily") }
    val displayedMonth = YearMonth.parse(month)
    val formatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy") }
    val leadingBlanks = displayedMonth.atDay(1).dayOfWeek.value - 1
    val days = (1..displayedMonth.lengthOfMonth()).toList()
    val cells = List(leadingBlanks) { 0 } + days
    val weeks = cells.chunked(7).map { week -> week + List(7 - week.size) { 0 } }
    val selectedDay = LocalDate.parse(selectedDate)
    val visibleRecords = state.records.filter { record ->
        record.date == selectedDay && when (activityFilter) {
            "Sessions" -> record.isSession
            "Manual" -> !record.isSession
            else -> true
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = JapSpacing.lg, top = JapSpacing.md, end = JapSpacing.lg, bottom = 104.dp)) {
        Text("Your practice", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("History", style = MaterialTheme.typography.headlineLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(JapSpacing.xs)) {
            listOf("Daily", "Sessions", "Manual").forEach { filter ->
                FilterChip(
                    selected = activityFilter == filter,
                    onClick = { activityFilter = filter },
                    label = { Text(filter) }
                )
            }
        }
        if (state.isLoading && state.records.isEmpty()) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        }
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
            StatCard("Total Naam Jap", com.naamjap.app.ui.components.formatCount(visibleRecords.sumOf { it.count }), Modifier.weight(1f))
            StatCard("Sessions", visibleRecords.count { it.isSession }.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(JapSpacing.lg))
        SectionHeader("Activity")
        Spacer(Modifier.height(JapSpacing.xs))
        GlassSurface(Modifier.fillMaxWidth()) {
            val selectedRecords = visibleRecords
            if (selectedRecords.isEmpty()) {
                EmptyState("No records for this day", "When you add practice records, sessions and manual entries will appear here.")
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    itemsIndexed(selectedRecords) { index, record ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = JapSpacing.md, vertical = JapSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(JapSpacing.sm)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(if (record.isSession) "Live session" else "Daily entry", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(record.naamName, style = MaterialTheme.typography.titleSmall)
                                Text("${com.naamjap.app.ui.components.formatCount(record.count)} Naam Jap · ${record.sortAt.atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a"))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (record.durationSeconds != null) Text("Duration ${formatDuration(record.durationSeconds)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                record.note?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                            TextButton(onClick = { deleteTarget = record; deletePassword = ""; viewModel.clearDeletionError() }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        val isDeleting = state.deletingId == target.id
        AlertDialog(
            onDismissRequest = { if (!isDeleting) deleteTarget = null },
            title = { Text(if (target.isSession) "Delete this session?" else "Delete this daily entry?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(JapSpacing.sm)) {
                    Text("Confirm your account password to delete this item.")
                    OutlinedTextField(
                        value = deletePassword,
                        onValueChange = { deletePassword = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !isDeleting,
                        singleLine = true
                    )
                    state.deletionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (isDeleting) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    enabled = deletePassword.isNotBlank() && !isDeleting,
                    onClick = {
                        val password = deletePassword
                        deletePassword = ""
                        if (target.isSession) viewModel.deleteSession(target, password)
                        else viewModel.deleteManualRecord(target.id, password)
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(enabled = !isDeleting, onClick = { deleteTarget = null; deletePassword = ""; viewModel.clearDeletionError() }) { Text("Cancel") } }
        )
    }

}

private fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = safe % 3600 / 60
    val remainder = safe % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainder) else "%d:%02d".format(minutes, remainder)
}
