package com.naamjap.app.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.naamjap.app.ui.components.*
import com.naamjap.app.ui.theme.JapSpacing
import com.naamjap.app.ui.theme.ThemeChoice
import com.naamjap.app.ui.theme.NaamJapTheme
import com.naamjap.app.domain.model.AccountIdentity
import androidx.compose.ui.tooling.preview.Preview

data class SettingsUiState(val displayName: String = "Personal practice", val subtitle: String = "Your private space", val message: String? = null, val error: String? = null, val dailyGoal: Long = 1000)

@HiltViewModel
class SettingsViewModel @Inject constructor(private val profiles: com.naamjap.app.domain.repository.ProfileRepository, private val practice: com.naamjap.app.domain.repository.PracticeRepository) : ViewModel() {
    val data = practice.state
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state
    init { viewModelScope.launch { runCatching { profiles.getCurrentProfile() }.onSuccess { p -> _state.value = _state.value.copy(displayName=p.displayName, subtitle=p.email.orEmpty()) }; runCatching { practice.refresh(java.time.ZoneId.systemDefault().id) } } }
    fun saveName(name: String) = viewModelScope.launch { runCatching { profiles.updateProfile(name, null) }.onSuccess { _state.value = _state.value.copy(displayName=name.trim(), message="Profile saved.", error=null) }.onFailure { _state.value = _state.value.copy(error="Profile could not be saved.", message=null) } }
    fun saveGoal(goal: String) = viewModelScope.launch { runCatching { practice.saveDailyGoal(goal.toLong()) }.onSuccess { _state.value = _state.value.copy(message="Daily goal saved.", error=null) }.onFailure { _state.value = _state.value.copy(error="Enter a valid goal and try again.", message=null) } }
    fun addNaam(name: String) = viewModelScope.launch { runCatching { practice.createNaamType(name) }.onSuccess { practice.refresh(java.time.ZoneId.systemDefault().id); _state.value = _state.value.copy(message="Naam added.", error=null) }.onFailure { _state.value = _state.value.copy(error="Naam could not be added.", message=null) } }
    fun setDefaultNaam(id: String) = viewModelScope.launch { runCatching { practice.setDefaultNaamType(id) }.onSuccess { practice.refresh(java.time.ZoneId.systemDefault().id) }.onFailure { _state.value = _state.value.copy(error="Default naam could not be changed.", message=null) } }
    fun deleteAccount() = viewModelScope.launch { runCatching { profiles.deleteCurrentAccount() }.onFailure { _state.value = _state.value.copy(error="Account deletion could not be completed.") } }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    NaamJapTheme { }
}

@Composable
fun SettingsScreen(
    themeChoice: ThemeChoice,
    onThemeChoice: (ThemeChoice) -> Unit,
    account: AccountIdentity? = null,
    onSignOut: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val practice by viewModel.data.collectAsStateWithLifecycle()
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var editName by rememberSaveable { mutableStateOf(false) }
    var nameDraft by rememberSaveable { mutableStateOf("") }
    var goalDraft by rememberSaveable { mutableStateOf("") }
    var naamDraft by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = JapSpacing.lg, top = JapSpacing.md, end = JapSpacing.lg, bottom = 104.dp), verticalArrangement = Arrangement.spacedBy(JapSpacing.md)) {
        Text("Make it yours", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Settings", style = MaterialTheme.typography.headlineLarge)
        GlassSurface(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(JapSpacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JapSpacing.md)) {
                Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(52.dp)) {
                    Box(contentAlignment = Alignment.Center) { LotusMark(Modifier.size(44.dp), "Naam Jap lotus logo") }
                }
                Column {
                    Text(state.displayName.ifBlank { account?.displayName ?: "Naam Jap account" }, style = MaterialTheme.typography.titleMedium)
                    Text(state.subtitle.ifBlank { account?.email.orEmpty() }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        SettingsSection("Appearance") {
            Row(horizontalArrangement = Arrangement.spacedBy(JapSpacing.xs)) {
                ThemePreview("Light", themeChoice == ThemeChoice.LIGHT, { onThemeChoice(ThemeChoice.LIGHT) }, { Icon(Icons.Default.LightMode, contentDescription = null) }, Modifier.weight(1f))
                ThemePreview("Dark", themeChoice == ThemeChoice.DARK, { onThemeChoice(ThemeChoice.DARK) }, { Icon(Icons.Default.DarkMode, contentDescription = null) }, Modifier.weight(1f))
                ThemePreview("System", themeChoice == ThemeChoice.SYSTEM, { onThemeChoice(ThemeChoice.SYSTEM) }, { Icon(Icons.Default.Settings, contentDescription = null) }, Modifier.weight(1f))
            }
        }

        SettingsSection("Practice preferences") {
            SettingsRow(Icons.Default.Notifications, "Notifications", "Coming soon")
            SettingsRow(Icons.Default.Vibration, "Haptic feedback", "Coming soon")
            SettingsRow(Icons.Default.VolumeUp, "Sound", "Coming soon")
            Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(value = goalDraft.ifBlank { practice.dashboard.dailyGoal.toString() }, onValueChange = { goalDraft = it.filter(Char::isDigit).take(10) }, label = { Text("Daily target") }, modifier = Modifier.weight(1f)); TextButton(onClick = { viewModel.saveGoal(goalDraft.ifBlank { practice.dashboard.dailyGoal.toString() }) }) { Text("Save") } }
            OutlinedTextField(naamDraft, { naamDraft = it.take(80) }, label = { Text("Add a naam type") }, modifier = Modifier.fillMaxWidth(), trailingIcon = { TextButton(onClick = { viewModel.addNaam(naamDraft); naamDraft = "" }, enabled = naamDraft.trim().length >= 2) { Text("Add") } })
            practice.naamTypes.forEach { naam -> Row(verticalAlignment = Alignment.CenterVertically) { Text(naam.name, Modifier.weight(1f)); Text(if (naam.isDefault) "Default" else ""); if (!naam.isDefault) TextButton(onClick = { viewModel.setDefaultNaam(naam.id) }) { Text("Set default") } } }
        }

        SettingsSection("Account & privacy") {
            if (account != null) {
                SettingsRow(Icons.Default.Person, state.displayName.ifBlank { account.displayName ?: "Naam Jap account" }, state.subtitle.ifBlank { account.email ?: "Signed in" })
                TextButton(onClick = { nameDraft = state.displayName; editName = true }, modifier = Modifier.fillMaxWidth()) { Text("Edit display name") }
                TextButton(onClick = { confirmSignOut = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Sign out") }
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete account and cloud data", color = MaterialTheme.colorScheme.error) }
            } else {
                SettingsRow(Icons.Default.Person, "Account", "No authenticated account")
            }
            SettingsRow(Icons.Default.Cloud, "Backup & sync", "Supabase account sync enabled")
            SettingsRow(Icons.Default.Lock, "Privacy", "Learn how your data is handled")
            SettingsRow(Icons.Default.Info, "About Naam Jap", "Phase 2 · UI preview")
        }
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("You'll need to sign in again to access your account.") },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; onSignOut() }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } }
        )
    }
    if (editName) {
        AlertDialog(
            onDismissRequest = { editName = false },
            title = { Text("Display name") },
            text = {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { updatedName: String -> nameDraft = updatedName },
                    label = { Text("Name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editName = false
                        viewModel.saveName(nameDraft)
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editName = false }) { Text("Cancel") }
            }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete account and data?") },
            text = { Text("This permanently removes your account and its cloud practice data.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteAccount()
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
    (state.error ?: state.message)?.let { Text(it, Modifier.padding(16.dp), color = if(state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(JapSpacing.xs)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        GlassSurface(Modifier.fillMaxWidth(), content)
    }
}

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = JapSpacing.md, vertical = JapSpacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JapSpacing.md)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
