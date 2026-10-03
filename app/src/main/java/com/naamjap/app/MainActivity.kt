package com.naamjap.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.SideEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.naamjap.app.navigation.NaamJapApp
import com.naamjap.app.feature.splash.SplashWallpaperScreen
import com.naamjap.app.ui.theme.NaamJapTheme
import com.naamjap.app.ui.theme.ThemeChoice
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var themeChoice by rememberSaveable { mutableStateOf(ThemeChoice.SYSTEM) }
            var showBrandSplash by rememberSaveable { mutableStateOf(true) }
            val darkTheme = when (themeChoice) {
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK -> true
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            LaunchedEffect(Unit) {
                withFrameNanos { }
                showBrandSplash = false
            }
            NaamJapTheme(choice = themeChoice) {
                Crossfade(targetState = showBrandSplash, label = "brand splash transition") { showSplash ->
                    if (showSplash) SplashWallpaperScreen(darkTheme)
                    else NaamJapApp(themeChoice = themeChoice, onThemeChoice = { themeChoice = it })
                }
            }
        }
    }
}
