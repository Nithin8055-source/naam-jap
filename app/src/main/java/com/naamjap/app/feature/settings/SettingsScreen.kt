package com.naamjap.app.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import com.naamjap.app.data.remote.NetworkStatus
import com.naamjap.app.data.remote.safeSupabaseError
import com.naamjap.app.domain.model.AccountIdentity
import com.naamjap.app.domain.repository.NaamType
import com.naamjap.app.domain.repository.PracticeRepository
import com.naamjap.app.ui.components.GlassSurface
import com.naamjap.app.ui.components.PremiumPullToRefreshBox
import com.naamjap.app.ui.components.navigationContentBottomInset
import com.naamjap.app.ui.theme.JapSpacing
import com.naamjap.app.ui.theme.NaamJapTheme
import com.naamjap.app.ui.theme.ThemeChoice
import com.naamjap.app.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val displayName: String = "",
    val subtitle: String = "",
    val message: String? = null,
    val error: String? = null,
    val loadError: String? = null,
    val isSaving: Boolean = false,
    val isUserRefreshing: Boolean = false,
    val isPasswordUpdating: Boolean = false,
    val passwordUpdated: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profiles: com.naamjap.app.domain.repository.ProfileRepository,
    private val practice: PracticeRepository,
    private val auth: com.naamjap.app.domain.repository.AuthRepository,
    private val networkStatus: NetworkStatus
) : ViewModel() {
    val data = practice.state
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state
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
            _state.value = _state.value.copy(loadError = null, isUserRefreshing = userInitiated)
            try {
                var loadError: String? = null
                try {
                    val profile = profiles.getCurrentProfile()
                    _state.value = _state.value.copy(displayName = profile.displayName, subtitle = profile.email.orEmpty())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    loadError = safeSupabaseError(error, networkStatus.hasValidatedInternet())
                }
                try {
                    practice.refresh(java.time.ZoneId.systemDefault().id)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (loadError == null) loadError = safeSupabaseError(error, networkStatus.hasValidatedInternet())
                }
                _state.value = _state.value.copy(loadError = loadError)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                _state.value = _state.value.copy(isUserRefreshing = false)
            }
        }
    }

    fun saveName(name: String) = runAction("Profile could not be saved.", "Profile saved.") {
        profiles.updateProfile(name, null)
        _state.value = _state.value.copy(displayName = name.trim())
    }

    fun saveGoal(goal: String) = runAction("Daily target could not be saved.", "Daily target saved.") {
        val target = goal.toLongOrNull() ?: throw IllegalArgumentException("Enter a valid numerical target.")
        require(target in 1..1_000_000_000) { "Choose a target between 1 and 1,000,000,000." }
        practice.saveDailyGoal(target)
    }

    fun addNaam(name: String) = runAction("Naam type could not be added.", "Naam type added.") {
        practice.createNaamType(name)
    }

    fun setDefaultNaam(id: String) = runAction("Naam type could not be changed.", "Default Naam updated.") {
        practice.setDefaultNaamType(id)
    }

    fun deleteNaamType(id: String, password: String) = runAction("Naam type could not be deleted.", "Naam type deleted.") {
        require(password.isNotBlank()) { "Enter your password to confirm deletion." }
        auth.reauthenticate(password)
        practice.deleteNaamType(id)
    }

    fun changePassword(currentPassword: String, newPassword: String, confirmation: String) = viewModelScope.launch {
        _state.value = _state.value.copy(isPasswordUpdating = true, passwordUpdated = false, error = null, message = null)
        try {
            require(newPassword.length >= 8) { "Use at least 8 characters for the new password." }
            require(newPassword == confirmation) { "The new passwords do not match." }
            require(currentPassword.isNotBlank()) { "Enter your current password." }
            auth.reauthenticate(currentPassword)
            auth.updatePassword(newPassword)
            _state.value = _state.value.copy(isPasswordUpdating = false, passwordUpdated = true, message = "Password updated.", error = null)
        } catch (cancelled: CancellationException) {
            _state.value = _state.value.copy(isPasswordUpdating = false)
            throw cancelled
        } catch (error: Exception) {
            val rawMessage = error.message.orEmpty().lowercase()
            val message = when {
                "invalid login credentials" in rawMessage || "invalid credentials" in rawMessage -> "Your current password is incorrect."
                error is IllegalArgumentException -> error.message ?: "Password could not be changed."
                else -> safeSupabaseError(error, networkStatus.hasValidatedInternet())
            }
            _state.value = _state.value.copy(isPasswordUpdating = false, error = message, message = null)
        }
    }

    fun deleteAccount(password: String) = runAction("Account deletion could not be completed.", "Account deletion completed.") {
        require(password.isNotBlank()) { "Enter your password to confirm account deletion." }
        auth.reauthenticate(password)
        profiles.deleteCurrentAccount()
    }

    private fun runAction(errorMessage: String, successMessage: String, action: suspend () -> Unit) = viewModelScope.launch {
        if (_state.value.isSaving) return@launch
        _state.value = _state.value.copy(isSaving = true, error = null, message = null)
        try {
            action()
            _state.value = _state.value.copy(isSaving = false, message = successMessage, error = null)
        } catch (cancelled: CancellationException) {
            _state.value = _state.value.copy(isSaving = false)
            throw cancelled
        } catch (error: Exception) {
            val raw = error.message.orEmpty().lowercase()
            val message = when {
                "invalid login credentials" in raw || "invalid credentials" in raw -> "That password is incorrect. Try again."
                error is IllegalArgumentException -> error.message ?: errorMessage
                else -> safeSupabaseError(error, networkStatus.hasValidatedInternet())
            }
            _state.value = _state.value.copy(isSaving = false, error = message, message = null)
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
    var deleteNaamTarget by remember { mutableStateOf<NaamType?>(null) }
    var changePasswordOpen by rememberSaveable { mutableStateOf(false) }
    var editName by rememberSaveable { mutableStateOf(false) }
    var selectNaamOpen by rememberSaveable { mutableStateOf(false) }
    var editGoal by remember { mutableStateOf(false) }
    var themePickerOpen by rememberSaveable { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }
    var naamDeletePassword by remember { mutableStateOf("") }
    var accountDeletePassword by remember { mutableStateOf("") }
    var nameDraft by rememberSaveable { mutableStateOf("") }
    var goalDraft by remember { mutableStateOf("") }
    var naamDraft by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.message) {
        when (state.message) {
            "Daily target saved." -> { goalDraft = ""; editGoal = false }
            "Naam type added." -> naamDraft = ""
            "Default Naam updated." -> selectNaamOpen = false
            "Profile saved." -> editName = false
            "Naam type deleted." -> { deleteNaamTarget = null; naamDeletePassword = "" }
            "Account deletion completed." -> { confirmDelete = false; accountDeletePassword = "" }
        }
    }
    LaunchedEffect(state.passwordUpdated) {
        if (state.passwordUpdated) {
            changePasswordOpen = false
            currentPassword = ""
            newPassword = ""
            confirmNewPassword = ""
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    PremiumPullToRefreshBox(
        isRefreshing = state.isUserRefreshing,
        onRefresh = { viewModel.refresh(userInitiated = true) },
        modifier = Modifier.fillMaxSize()
    ) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = JapSpacing.lg, top = JapSpacing.md, end = JapSpacing.lg)
            .padding(bottom = navigationContentBottomInset()),
        verticalArrangement = Arrangement.spacedBy(JapSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JapSpacing.sm)) {
            Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary)
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }

        GlassSurface(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 86.dp)
                    .clickable(enabled = account != null && !state.isSaving) { nameDraft = state.displayName; editName = true }
                    .padding(horizontal = JapSpacing.md, vertical = JapSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(JapSpacing.md)
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .16f), modifier = Modifier.size(54.dp)) {
                    Image(
                        painter = painterResource(R.drawable.lotus_photo),
                        contentDescription = "Lotus flower",
                        modifier = Modifier.fillMaxSize().padding(3.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(state.displayName.ifBlank { account?.displayName?.takeIf(String::isNotBlank) ?: "Signed-in account" }, style = MaterialTheme.typography.titleMedium)
                    Text(state.subtitle.ifBlank { account?.email ?: "Email unavailable" }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        (state.error ?: state.loadError ?: state.message)?.let { message ->
            val messageColor = if (state.error != null || state.loadError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = messageColor.copy(alpha = .09f),
                border = BorderStroke(1.dp, messageColor.copy(alpha = .2f))
            ) {
                Text(message, Modifier.padding(JapSpacing.md), color = messageColor, style = MaterialTheme.typography.bodyMedium)
            }
        }

        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                SettingRow(
                    icon = Icons.Default.Spa,
                    title = "Default Naam",
                    value = practice.naamTypes.firstOrNull { it.isDefault }?.name ?: "Choose Naam",
                    onClick = { selectNaamOpen = true },
                    enabled = !state.isSaving,
                    showChevron = true
                )
                SettingDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = JapSpacing.md, vertical = JapSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(JapSpacing.xs)
                ) {
                    OutlinedTextField(
                        value = naamDraft,
                        onValueChange = { naamDraft = it.take(80) },
                        label = { Text("Add a Naam") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = !state.isSaving
                    )
                    TextButton(
                        onClick = { viewModel.addNaam(naamDraft) },
                        enabled = !state.isSaving && naamDraft.trim().length >= 2
                    ) {
                        if (state.isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Add")
                    }
                }
                SettingDivider()
            SettingRow(
                icon = Icons.Default.TrackChanges,
                title = "Daily Goal",
                value = formatDailyGoal(practice.dashboard.dailyGoal),
                onClick = { goalDraft = practice.dashboard.dailyGoal.toString(); editGoal = !editGoal },
                enabled = !state.isSaving,
                showChevron = true
            )
            if (editGoal) {
                Column(Modifier.fillMaxWidth().padding(horizontal = JapSpacing.md, vertical = JapSpacing.sm), verticalArrangement = Arrangement.spacedBy(JapSpacing.sm)) {
                    Text("A numerical goal. Counting continues after you reach it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JapSpacing.sm)) {
                        OutlinedTextField(
                            value = goalDraft,
                            onValueChange = { goalDraft = it.filter(Char::isDigit).take(10) },
                            label = { Text("Daily target") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = goalDraft.isNotEmpty() && (goalDraft.toLongOrNull() ?: 0L) !in 1..1_000_000_000
                        )
                        Button(
                            onClick = { viewModel.saveGoal(goalDraft) },
                            enabled = !state.isSaving && (goalDraft.toLongOrNull() ?: 0L) in 1..1_000_000_000,
                            shape = CircleShape
                        ) {
                            if (state.isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text("Save")
                        }
                    }
                }
            }
            SettingDivider()
            SettingRow(
                icon = Icons.Default.NotificationsNone,
                title = "Reminders & notifications",
                value = "Not configured",
                onClick = {},
                enabled = false
            )
            SettingDivider()
            SettingRow(
                icon = Icons.Default.LightMode,
                title = "Theme",
                value = themeChoice.label,
                onClick = { themePickerOpen = true },
                enabled = true,
                showChevron = true
            )
            }
        }

        GlassSurface(Modifier.fillMaxWidth()) {
            SettingRow(Icons.Default.Lock, "Security", "Change password", onClick = { changePasswordOpen = true }, enabled = account != null && !state.isPasswordUpdating, showChevron = true)
            SettingDivider()
            SettingRow(Icons.Default.Cloud, "Backup & Sync", "Supabase account sync", onClick = {}, enabled = false)
            SettingDivider()
            SettingRow(Icons.Default.Info, "About Naam Jap", "Version 1.0.0", onClick = {}, enabled = false)
        }

        GlassSurface(Modifier.fillMaxWidth()) {
            SettingRow(Icons.Default.Person, "Display name", "Edit profile", onClick = { nameDraft = state.displayName; editName = true }, enabled = account != null && !state.isSaving, showChevron = true)
            SettingDivider()
            SettingRow(Icons.Default.Logout, "Sign out", "Sign out of this account", onClick = { confirmSignOut = true }, enabled = account != null && !state.isSaving)
            SettingDivider()
            SettingRow(Icons.Default.DeleteOutline, "Delete account", "Remove account and cloud data", onClick = { confirmDelete = true }, enabled = account != null && !state.isSaving, destructive = true)
        }

    }
    }

    if (selectNaamOpen) {
        AlertDialog(
            onDismissRequest = { if (!state.isSaving) selectNaamOpen = false },
            title = { Text("Choose Naam / Mantra") },
            text = {
                Column(
                    Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(JapSpacing.xs)
                ) {
                    if (practice.naamTypes.isEmpty()) Text("Add a Naam type below to choose one.")
                    else {
                        practice.naamTypes.forEach { naam ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SettingRow(
                                    icon = if (naam.isDefault) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    title = naam.name,
                                    value = if (naam.isDefault) "Default · used for new sessions" else "Personal",
                                    onClick = { if (!naam.isDefault && !state.isSaving) viewModel.setDefaultNaam(naam.id) },
                                    enabled = !state.isSaving,
                                    showChevron = false,
                                    modifier = Modifier.weight(1f)
                                )
                                if (practice.naamTypes.size > 1) {
                                    IconButton(onClick = { deleteNaamTarget = naam; naamDeletePassword = "" }, enabled = !state.isSaving) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete ${naam.name}", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (state.isSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { selectNaamOpen = false }, enabled = !state.isSaving) { Text("Done") } }
        )
    }
    if (themePickerOpen) {
        AlertDialog(
            onDismissRequest = { themePickerOpen = false },
            title = { Text("Appearance") },
            text = {
                Column {
                    ThemeChoice.entries.forEach { choice ->
                        SettingRow(
                            icon = if (choice == themeChoice) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            title = choice.label,
                            value = if (choice == themeChoice) "Selected" else "",
                            onClick = { themePickerOpen = false; onThemeChoice(choice) },
                            showChevron = false
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { themePickerOpen = false }) { Text("Done") } }
        )
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
            onDismissRequest = { if (!state.isSaving) editName = false },
            title = { Text("Display name") },
            text = { OutlinedTextField(nameDraft, { nameDraft = it.take(80) }, label = { Text("Name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { viewModel.saveName(nameDraft) }, enabled = !state.isSaving && nameDraft.isNotBlank()) {
                    if (state.isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save")
                }
            },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("Cancel") } }
        )
    }
    if (changePasswordOpen) {
        AlertDialog(
            onDismissRequest = { if (!state.isPasswordUpdating) changePasswordOpen = false },
            title = { Text("Change password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(JapSpacing.xs)) {
                    OutlinedTextField(currentPassword, { currentPassword = it }, label = { Text("Current password") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, enabled = !state.isPasswordUpdating)
                    OutlinedTextField(newPassword, { newPassword = it }, label = { Text("New password") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, enabled = !state.isPasswordUpdating)
                    OutlinedTextField(confirmNewPassword, { confirmNewPassword = it }, label = { Text("Confirm new password") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, enabled = !state.isPasswordUpdating)
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (state.isPasswordUpdating) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    enabled = currentPassword.isNotBlank() && newPassword.length >= 8 && newPassword == confirmNewPassword && !state.isPasswordUpdating,
                    onClick = { viewModel.changePassword(currentPassword, newPassword, confirmNewPassword) }
                ) { Text("Update password") }
            },
            dismissButton = { TextButton(enabled = !state.isPasswordUpdating, onClick = { changePasswordOpen = false }) { Text("Cancel") } }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!state.isSaving) { confirmDelete = false; accountDeletePassword = "" } },
            title = { Text("Delete account and data?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(JapSpacing.sm)) {
                    Text("This permanently removes your account and its cloud practice data. Confirm your password to continue.")
                    OutlinedTextField(
                        value = accountDeletePassword,
                        onValueChange = { accountDeletePassword = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        enabled = !state.isSaving
                    )
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (state.isSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val password = accountDeletePassword
                    accountDeletePassword = ""
                    viewModel.deleteAccount(password)
                }, enabled = !state.isSaving && accountDeletePassword.isNotBlank()) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false; accountDeletePassword = "" }, enabled = !state.isSaving) { Text("Cancel") } }
        )
    }
    deleteNaamTarget?.let { naam ->
        AlertDialog(
            onDismissRequest = { if (!state.isSaving) { deleteNaamTarget = null; naamDeletePassword = "" } },
            title = { Text("Delete ${naam.name}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(JapSpacing.sm)) {
                    Text(
                        if (naam.isDefault) {
                            "Enter your password to confirm. Another Naam will become the default first. Saved practice using this Naam must be removed before deletion."
                        } else {
                            "Enter your password to confirm. Saved practice using this Naam must be removed before deletion."
                        }
                    )
                    OutlinedTextField(
                        value = naamDeletePassword,
                        onValueChange = { naamDeletePassword = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        enabled = !state.isSaving
                    )
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (state.isSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = !state.isSaving && naamDeletePassword.isNotBlank(), onClick = {
                    val password = naamDeletePassword
                    naamDeletePassword = ""
                    viewModel.deleteNaamType(naam.id, password)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(enabled = !state.isSaving, onClick = { deleteNaamTarget = null; naamDeletePassword = "" }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    showChevron: Boolean = false,
    destructive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier.fillMaxWidth().heightIn(min = 62.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = JapSpacing.md, vertical = JapSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JapSpacing.sm)
    ) {
        Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            value,
            Modifier.widthIn(max = 132.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        if (showChevron) Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingDivider() {
    HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
}

private val ThemeChoice.label: String
    get() = when (this) {
        ThemeChoice.LIGHT -> "Light"
        ThemeChoice.DARK -> "Dark"
        ThemeChoice.SYSTEM -> "System"
    }

private fun formatDailyGoal(value: Long): String = java.text.NumberFormat.getIntegerInstance().format(value)
