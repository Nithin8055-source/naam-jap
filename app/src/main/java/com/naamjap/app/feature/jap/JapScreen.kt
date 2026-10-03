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
class JapViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(JapUiState())
    val state: StateFlow<JapUiState> = _state
}

@Composable
fun JapScreen(viewModel: JapViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val countInteraction = remember { MutableInteractionSource() }
    val countPressed by countInteraction.collectIsPressedAsState()
    val countScale by androidx.compose.animation.core.animateFloatAsState(if (countPressed) .96f else 1f, label = "count button press")
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = JapSpacing.xl, top = JapSpacing.lg, end = JapSpacing.xl, bottom = 112.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(state.title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(JapSpacing.xs))
            Text("Ready · UI preview", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(JapSpacing.xxl))
            Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_sacred_halo), null, Modifier.fillMaxSize().alpha(.13f), contentScale = ContentScale.Fit)
                JapCircularProgressIndicator(progress = 0f, modifier = Modifier.size(272.dp), strokeWidth = 5.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OmSymbol(Modifier.size(28.dp), null)
                        AnimatedContent(targetState = state.count, label = "preview count") { count -> Text(count.toString().padStart(3, '0'), style = MaterialTheme.typography.displayLarge) }
                        Text("of 108 · mala preview", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(JapSpacing.sm))
            Text("A calm space for your practice", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(JapSpacing.xxl))
            Button(onClick = { scope.launch { snackbarHostState.showSnackbar("Counting will be available in a future phase. No count was added.") } }, interactionSource = countInteraction, modifier = Modifier.size(84.dp).scale(countScale), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)) {
                LotusMark(Modifier.size(34.dp), "Preview count action; does not add or save a count")
            }
            Spacer(Modifier.height(JapSpacing.xs))
            Text("Tap to count · preview only", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(JapSpacing.lg))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                PreviewControl("Undo", Icons.Default.Replay)
                Spacer(Modifier.width(JapSpacing.xxxl))
                PreviewControl("Pause", Icons.Default.Pause)
            }
            Spacer(Modifier.height(JapSpacing.xl))
            GlassSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(JapSpacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JapSpacing.md)) {
                        Image(painterResource(R.drawable.ic_mala_beads), "Mala beads", Modifier.size(48.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        Column {
                            Text("Session preview", style = MaterialTheme.typography.titleMedium)
                            Text("No active session", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Elapsed time and session totals will appear here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
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
    var count by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var mantra by rememberSaveable { mutableStateOf("Om Namah Shivaya") }
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showMantraMenu by remember { mutableStateOf(false) }
    var saveAttempted by remember { mutableStateOf(false) }
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
                OutlinedTextField(value = mantra, onValueChange = {}, readOnly = true, label = { Text("Naam / Mantra") }, trailingIcon = { TextButton(onClick = { showMantraMenu = true }) { Text("Choose") } }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                DropdownMenu(expanded = showMantraMenu, onDismissRequest = { showMantraMenu = false }) {
                    listOf("Om Namah Shivaya", "Waheguru", "Hare Krishna").forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = { mantra = name; showMantraMenu = false }) }
                }
            }
            PremiumTextField(value = count, onValueChange = { count = it.filter { character -> character.isDigit() }.take(9) }, label = "Count", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = saveAttempted && count.isBlank(), supportingText = if (saveAttempted && count.isBlank()) "Enter a count to continue" else null)
            OutlinedTextField(value = LocalDate.parse(selectedDate).format(dateFormatter), onValueChange = {}, readOnly = true, label = { Text("Date") }, trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.CalendarMonth, contentDescription = "Choose date") } }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
            PremiumTextField(value = note, onValueChange = { note = it.take(400) }, label = "Notes (optional)", singleLine = false, supportingText = "${note.length}/400")
            Text("Preview only. Saving records will be added in a future phase.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PrimaryActionButton("Save record", onClick = {
                saveAttempted = true
                scope.launch { snackbarHostState.showSnackbar(if (count.isBlank()) "Enter a count first." else "Preview only. No record was saved.") }
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
    NaamJapTheme { JapScreen(viewModel = JapViewModel()) }
}

@Preview(showBackground = true)
@Composable
private fun ManualRecordScreenPreview() {
    NaamJapTheme { ManualRecordScreen(onBack = {}) }
}
