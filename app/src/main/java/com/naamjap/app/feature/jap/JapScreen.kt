package com.naamjap.app.feature.jap

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import com.naamjap.app.ui.components.*
import com.naamjap.app.ui.theme.JapSpacing
import com.naamjap.app.ui.theme.NaamJapTheme
import com.naamjap.app.R
import androidx.compose.ui.tooling.preview.Preview

data class JapUiState(val title: String = "Naam Jap", val count: Int = 0)

@HiltViewModel
class JapViewModel @Inject constructor(private val repository: com.naamjap.app.domain.repository.PracticeRepository) : ViewModel() {
    val data = repository.state
    init { viewModelScope.launch { runCatching { repository.refresh(java.time.ZoneId.systemDefault().id) }; if (repository.state.value.naamTypes.isEmpty()) runCatching { repository.ensureDefaultNaamType() } } }
    fun start(id: String) = viewModelScope.launch { runCatching { repository.startSession(id) } }
    fun action(id: String, action: com.naamjap.app.domain.repository.SessionAction) = viewModelScope.launch { runCatching { repository.applySessionAction(id, action) } }
    suspend fun save(id: String, count: Long, note: String?, naamId: String, date: LocalDate) {
        repository.saveManualRecord(
            com.naamjap.app.domain.model.PracticeRecord(id, count, System.currentTimeMillis(), note),
            naamId,
            date
        )
    }
}

@Composable
fun JapScreen(viewModel: JapViewModel = hiltViewModel()) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val active = data.activeSession
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val countInteraction = remember { MutableInteractionSource() }
    val countPressed by countInteraction.collectIsPressedAsState()
    val countScale by androidx.compose.animation.core.animateFloatAsState(if (countPressed) .96f else 1f, label = "count button press")
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = JapSpacing.xl, top = JapSpacing.lg, end = JapSpacing.xl, bottom = 112.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Naam Jap", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(JapSpacing.xs))
            Text(if (active == null) "Choose a naam and begin" else if (active.isPaused) "Paused" else "Session in progress", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(JapSpacing.xxl))
            Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_sacred_halo), null, Modifier.fillMaxSize().alpha(.13f), contentScale = ContentScale.Fit)
                JapCircularProgressIndicator(progress = 0f, modifier = Modifier.size(272.dp), strokeWidth = 5.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OmSymbol(Modifier.size(28.dp), null)
                        AnimatedContent(targetState = active?.count ?: 0L, label = "session count") { count -> Text(count.toString().padStart(3, '0'), style = MaterialTheme.typography.displayLarge) }
                        Text(active?.naamName ?: "Select a naam below", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(JapSpacing.sm))
            Text("A calm space for your practice", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(JapSpacing.xxl))
            Button(onClick = { active?.let { viewModel.action(it.id, com.naamjap.app.domain.repository.SessionAction.INCREMENT) } }, enabled = active != null && !active.isPaused && !data.isSaving, interactionSource = countInteraction, modifier = Modifier.size(84.dp).scale(countScale), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)) {
                LotusMark(Modifier.size(34.dp), "Add one repetition")
            }
            Spacer(Modifier.height(JapSpacing.xs))
            Text(if (active == null) "Start a session to count" else "Tap to add one repetition", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(JapSpacing.lg))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { active?.let { viewModel.action(it.id, com.naamjap.app.domain.repository.SessionAction.UNDO) } }, enabled = active != null && active.count > 0 && !data.isSaving) { Icon(Icons.Default.Replay, null); Text("Undo") }
                Spacer(Modifier.width(JapSpacing.xxxl))
                TextButton(onClick = { active?.let { viewModel.action(it.id, if (it.isPaused) com.naamjap.app.domain.repository.SessionAction.RESUME else com.naamjap.app.domain.repository.SessionAction.PAUSE) } }, enabled = active != null && !data.isSaving) { Icon(Icons.Default.Pause, null); Text(if (active?.isPaused == true) "Resume" else "Pause") }
            }
            Spacer(Modifier.height(JapSpacing.xl))
            GlassSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(JapSpacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JapSpacing.md)) {
                        Image(painterResource(R.drawable.ic_mala_beads), "Mala beads", Modifier.size(48.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        Column {
                            Text(active?.naamName ?: "Start a session", style = MaterialTheme.typography.titleMedium)
                            Text(if (active == null) "No active session" else "${active.durationSeconds / 60} min · ${active.count} repetitions", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (active == null) data.naamTypes.forEach { naam -> TextButton(onClick = { viewModel.start(naam.id) }) { Text("Start ${naam.name}") } }
                            else TextButton(onClick = { viewModel.action(active.id, com.naamjap.app.domain.repository.SessionAction.FINISH) }) { Text("Finish session") }
                        }
                    }
                }
            }
        }
        data.error?.let { Text(it, Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp, start = 20.dp, end = 20.dp), color = MaterialTheme.colorScheme.error) }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun PreviewControl(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = {}, enabled = false, modifier = Modifier.size(48.dp)) { Icon(icon, contentDescription = null) }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ManualRecordScreen(onBack: () -> Unit) {
    val viewModel: JapViewModel = hiltViewModel()
    val data by viewModel.data.collectAsStateWithLifecycle()
    var count by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var mantraId by rememberSaveable { mutableStateOf("") }
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showMantraMenu by remember { mutableStateOf(false) }
    var saveAttempted by remember { mutableStateOf(false) }
    var pendingRecordId by rememberSaveable { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = LocalDate.parse(selectedDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = JapSpacing.lg, vertical = JapSpacing.md), verticalArrangement = Arrangement.spacedBy(JapSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PremiumIconButton("Go back", onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                Text("Add manual record", Modifier.padding(start = JapSpacing.sm), style = MaterialTheme.typography.headlineMedium)
            }
            Text("Add a practice record", style = MaterialTheme.typography.titleMedium)
            Box {
                OutlinedTextField(value = data.naamTypes.firstOrNull { it.id == mantraId }?.name.orEmpty(), onValueChange = {}, readOnly = true, label = { Text("Naam / Mantra") }, trailingIcon = { TextButton(onClick = { showMantraMenu = true }) { Text("Choose") } }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                DropdownMenu(expanded = showMantraMenu, onDismissRequest = { showMantraMenu = false }) {
                    data.naamTypes.forEach { naam -> DropdownMenuItem(text = { Text(naam.name) }, onClick = { mantraId = naam.id; showMantraMenu = false }) }
                }
            }
            PremiumTextField(value = count, onValueChange = { count = it.filter { character -> character.isDigit() }.take(9) }, label = "Count", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = saveAttempted && count.isBlank(), supportingText = if (saveAttempted && count.isBlank()) "Enter a count to continue" else null)
            OutlinedTextField(value = LocalDate.parse(selectedDate).format(dateFormatter), onValueChange = {}, readOnly = true, label = { Text("Date") }, trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.CalendarMonth, contentDescription = "Choose date") } }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
            PremiumTextField(value = note, onValueChange = { note = it.take(400) }, label = "Notes (optional)", singleLine = false, supportingText = "${note.length}/400")
            data.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            PrimaryActionButton("Save record", onClick = {
                saveAttempted = true
                scope.launch {
                    val amount = count.toLongOrNull()
                    val naamId = mantraId.ifBlank { data.naamTypes.firstOrNull { it.isDefault }?.id.orEmpty() }
                    if (amount == null || amount < 1) snackbarHostState.showSnackbar("Enter a count greater than zero.")
                    else if (naamId.isBlank()) snackbarHostState.showSnackbar("Choose a naam first.")
                    else try {
                        viewModel.save(pendingRecordId, amount, note, naamId, LocalDate.parse(selectedDate))
                        snackbarHostState.showSnackbar("Record saved to your account.")
                        onBack()
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        snackbarHostState.showSnackbar(data.error ?: "Record could not be confirmed. Retry the save safely.")
                    }
                }
            })
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
    if (showDatePicker) {
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { millis -> selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                showDatePicker = false
            }) { Text("Apply") }
        }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }) { DatePicker(state = datePickerState) }
    }
}

@Preview(showBackground = true)
@Composable
private fun JapScreenPreview() {
    NaamJapTheme { Text("Naam Jap") }
}

@Preview(showBackground = true)
@Composable
private fun ManualRecordScreenPreview() {
    NaamJapTheme { ManualRecordScreen(onBack = {}) }
}
