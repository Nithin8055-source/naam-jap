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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.naamjap.app.data.remote.NetworkStatus
import com.naamjap.app.data.remote.safeSupabaseError
import androidx.compose.ui.tooling.preview.Preview

data class SettingsUiState(val displayName: String = "", val subtitle: String = "", val message: String? = null, val error: String? = null, val dailyGoal: Long = 1000)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profiles: com.naamjap.app.domain.repository.ProfileRepository,
    private val practice: com.naamjap.app.domain.repository.PracticeRepository,
    private val networkStatus: NetworkStatus
) : ViewModel() {
    val data = practice.state
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state
    init {
        viewModelScope.launch {
            try {
                val profile = profiles.getCurrentProfile()
                _state.value = _state.value.copy(displayName = profile.displayName, subtitle = profile.email.orEmpty())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Profile details are optional here; the authenticated account remains visible as a fallback.
            }
            try {
                practice.refresh(java.time.ZoneId.systemDefault().id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // PracticeRepository exposes the sync error through its state.
            }
        }
    }

    fun saveName(name: String) = runAction("Profile could not be saved.") {
        profiles.updateProfile(name, null)
        _state.value = _state.value.copy(displayName = name.trim(), message = "Profile saved.", error = null)
    }

    fun saveGoal(goal: String) = runAction("Enter a valid goal and try again.") {
        practice.saveDailyGoal(goal.toLong())
        _state.value = _state.value.copy(message = "Daily goal saved.", error = null)
    }

    fun addNaam(name: String) = runAction("Naam could not be added.") {
        practice.createNaamType(name)
        _state.value = _state.value.copy(message = "Naam added.", error = null)
    }

    fun setDefaultNaam(id: String) = runAction("Default naam could not be changed.") {
        practice.setDefaultNaamType(id)
    }

    fun deleteAccount() = runAction("Account deletion could not be completed.") {
        profiles.deleteCurrentAccount()
    }

    private fun runAction(errorMessage: String, action: suspend () -> Unit) = viewModelScope.launch {
        try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val message = if (error is IllegalArgumentException) {
                error.message ?: errorMessage
            } else {
                safeSupabaseError(error, networkStatus.hasValidatedInternet())
            }
            _state.value = _state.value.copy(error = message, message = null)
        }
    }
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
                    Text(state.displayName.ifBlank { account?.displayName?.takeIf(String::isNotBlank) ?: "Signed-in account" }, style = MaterialTheme.typography.titleMedium)
                    Text(state.subtitle.ifBlank { account?.email ?: "Email unavailable" }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
            OutlinedTextField(
                value = naamDraft,
                onValueChange = { updatedNaam: String -> naamDraft = updatedNaam.take(80) },
                label = { Text("Add a naam type") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(
                        onClick = {
                            viewModel.addNaam(naamDraft)
                            naamDraft = ""
                        },
                        enabled = naamDraft.trim().length >= 2
                    ) {
                        Text("Add")
                    }
                }
            )
            for (naam in practice.naamTypes) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(naam.name, Modifier.weight(1f))
                    Text(if (naam.isDefault) "Default" else "")
                    if (!naam.isDefault) {
                        TextButton(onClick = { viewModel.setDefaultNaam(naam.id) }) { Text("Set default") }
                    }
                }
            }
        }

        SettingsSection("Account & privacy") {
            if (account != null) {
                SettingsRow(Icons.Default.Person, state.displayName.ifBlank { account.displayName?.takeIf(String::isNotBlank) ?: "Signed-in account" }, state.subtitle.ifBlank { account.email ?: "Email unavailable" })
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
    val statusMessage = state.error ?: state.message
    if (statusMessage != null) {
        Text(
            text = statusMessage,
            modifier = Modifier.padding(16.dp),
            color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
    }
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
