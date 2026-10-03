package com.naamjap.app

import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.naamjap.app.navigation.NaamJapApp
import com.naamjap.app.feature.splash.SplashWallpaperScreen
import com.naamjap.app.feature.settings.ThemeViewModel
import com.naamjap.app.ui.theme.NaamJapTheme
import com.naamjap.app.ui.theme.ThemeChoice
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val themePreferenceLoaded = java.util.concurrent.atomic.AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !themePreferenceLoaded.get() }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeState by themeViewModel.uiState.collectAsStateWithLifecycle()
            var showOpeningWallpaper by rememberSaveable { mutableStateOf(savedInstanceState == null) }
            var openingStartedAt by rememberSaveable { mutableLongStateOf(0L) }
            val themeChoice = if (themeState.isLoaded) themeState.choice else ThemeChoice.SYSTEM
            val darkTheme = when (themeChoice) {
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK -> true
            }
            SideEffect {
                if (themeState.isLoaded) themePreferenceLoaded.set(true)
                window.statusBarColor = AndroidColor.TRANSPARENT
                window.navigationBarColor = AndroidColor.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isStatusBarContrastEnforced = false
                    window.isNavigationBarContrastEnforced = false
                }
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            LaunchedEffect(themeState.isLoaded, showOpeningWallpaper) {
                if (themeState.isLoaded && showOpeningWallpaper) {
                    if (openingStartedAt == 0L) openingStartedAt = SystemClock.elapsedRealtime()
                    val remaining = 2_000L - (SystemClock.elapsedRealtime() - openingStartedAt)
                    if (remaining > 0) delay(remaining)
                    showOpeningWallpaper = false
                }
            }
            NaamJapTheme(choice = themeChoice) {
                Crossfade(
                    targetState = showOpeningWallpaper,
                    animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
                    label = "opening wallpaper transition"
                ) { showSplash ->
                    if (showSplash) SplashWallpaperScreen(darkTheme)
                    else NaamJapApp(themeChoice = themeChoice, onThemeChoice = themeViewModel::selectTheme)
                }
            }
        }
    }
}
