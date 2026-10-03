package com.naamjap.app.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.naamjap.app.ui.components.*
import com.naamjap.app.ui.theme.JapSpacing
import com.naamjap.app.ui.theme.ThemeChoice
import com.naamjap.app.ui.theme.NaamJapTheme
import androidx.compose.ui.tooling.preview.Preview

data class SettingsUiState(val displayName: String = "Personal practice", val subtitle: String = "Your private space")

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    NaamJapTheme { SettingsScreen(themeChoice = ThemeChoice.SYSTEM, onThemeChoice = {}, viewModel = SettingsViewModel()) }
}

@Composable
fun SettingsScreen(themeChoice: ThemeChoice, onThemeChoice: (ThemeChoice) -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = JapSpacing.lg, top = JapSpacing.md, end = JapSpacing.lg, bottom = 104.dp), verticalArrangement = Arrangement.spacedBy(JapSpacing.md)) {
        Text("Make it yours", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Settings", style = MaterialTheme.typography.headlineLarge)
        GlassSurface(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(JapSpacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JapSpacing.md)) {
                Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(52.dp)) {
                    Box(contentAlignment = Alignment.Center) { LotusMark(Modifier.size(44.dp), "Naam Jap lotus logo") }
                }
                Column { Text(state.displayName, style = MaterialTheme.typography.titleMedium); Text(state.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
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
            SettingsRow(Icons.Default.Flag, "Daily goal", "Not set")
            SettingsRow(Icons.Default.Spa, "Naam management", "Coming soon")
        }

        SettingsSection("Account & privacy") {
            SettingsRow(Icons.Default.Person, "Sign in", "Optional · coming soon")
            SettingsRow(Icons.Default.Cloud, "Backup & sync", "Not connected")
            SettingsRow(Icons.Default.Lock, "Privacy", "Learn how your data is handled")
            SettingsRow(Icons.Default.Info, "About Naam Jap", "Phase 2 · UI preview")
        }
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
