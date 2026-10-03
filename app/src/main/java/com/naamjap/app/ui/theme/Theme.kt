package com.naamjap.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ThemeChoice {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun fromPreference(value: String?): ThemeChoice =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

private val LightColors = lightColorScheme(primary = Saffron, onPrimary = Charcoal, primaryContainer = SaffronSoft, onPrimaryContainer = Charcoal, secondary = Gold, background = Ivory, surface = Cream, onBackground = Charcoal, onSurface = Charcoal, surfaceVariant = Color(0xFFF3EEE5), onSurfaceVariant = Muted, outline = Border, outlineVariant = Border)
private val DarkColors = darkColorScheme(primary = SaffronDark, onPrimary = Color(0xFF35200B), primaryContainer = Color(0xFF49371F), onPrimaryContainer = DarkText, secondary = Color(0xFFD6B477), background = DarkBackground, surface = DarkSurface, onBackground = DarkText, onSurface = DarkText, surfaceVariant = DarkElevated, onSurfaceVariant = DarkMuted, outline = DarkBorder, outlineVariant = DarkBorder)

@Composable
fun NaamJapTheme(choice: ThemeChoice = ThemeChoice.SYSTEM, content: @Composable () -> Unit) {
    val darkTheme = when (choice) { ThemeChoice.SYSTEM -> isSystemInDarkTheme(); ThemeChoice.LIGHT -> false; ThemeChoice.DARK -> true }
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = NaamJapTypography, shapes = NaamJapShapes, content = content)
}
