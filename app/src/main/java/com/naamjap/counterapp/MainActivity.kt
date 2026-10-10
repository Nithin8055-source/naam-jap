package com.naamjap.counterapp

import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.naamjap.counterapp.navigation.NaamJapApp
import com.naamjap.counterapp.feature.auth.AuthScreen
import com.naamjap.counterapp.feature.auth.AuthViewModel
import com.naamjap.counterapp.feature.splash.SplashWallpaperScreen
import com.naamjap.counterapp.feature.settings.ThemeViewModel
import com.naamjap.counterapp.data.remote.SupabaseProvider
import com.naamjap.counterapp.domain.repository.AuthSessionState
import com.naamjap.counterapp.ui.theme.NaamJapTheme
import com.naamjap.counterapp.ui.theme.ThemeChoice
import javax.inject.Inject
import io.github.jan.supabase.auth.handleDeeplinks
import dagger.hilt.android.AndroidEntryPoint
import com.naamjap.counterapp.widget.EXTRA_WIDGET_OPEN_JAP
import com.naamjap.counterapp.widget.EXTRA_WIDGET_OPEN_HOME
import com.naamjap.counterapp.widget.NaamJapWidgetRenderer
import com.naamjap.counterapp.widget.NaamJapWidgetSnapshotStore
import com.naamjap.counterapp.widget.NaamJapWidgetWork
import com.naamjap.counterapp.navigation.EXTRA_NOTIFICATION_OPEN_HOME
import com.naamjap.counterapp.navigation.EXTRA_NOTIFICATION_OPEN_JAP
import com.naamjap.counterapp.notifications.NotificationCoordinator

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val themePreferenceLoaded = java.util.concurrent.atomic.AtomicBoolean(false)
    private val authDeepLink = mutableStateOf<Uri?>(null)
    private var widgetJapRequest by mutableIntStateOf(0)
    private var widgetHomeRequest by mutableIntStateOf(0)
    private var widgetSessionReady = false

    @Inject lateinit var supabaseProvider: SupabaseProvider
    @Inject lateinit var notificationCoordinator: NotificationCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !themePreferenceLoaded.get() }
        super.onCreate(savedInstanceState)
        authDeepLink.value = intent?.data
        consumeWidgetIntent(intent)
        intent?.let { supabaseProvider.client?.handleDeeplinks(it) }
        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeState by themeViewModel.uiState.collectAsStateWithLifecycle()
            val authViewModel: AuthViewModel = hiltViewModel()
            val authSession by authViewModel.sessionState.collectAsStateWithLifecycle()
            LaunchedEffect(authSession) {
                when (val session = authSession) {
                    is AuthSessionState.SignedIn -> {
                        widgetSessionReady = true
                        NaamJapWidgetSnapshotStore.clearIfDifferentAccount(this@MainActivity, session.account.userId)
                        NaamJapWidgetRenderer.updateAll(this@MainActivity)
                        NaamJapWidgetWork.schedulePeriodicRefresh(this@MainActivity)
                        NaamJapWidgetWork.enqueueRefresh(this@MainActivity)
                    }
                    AuthSessionState.SignedOut,
                    AuthSessionState.SessionUnavailable,
                    AuthSessionState.ConfigurationMissing -> {
                        widgetSessionReady = false
                        NaamJapWidgetSnapshotStore.clear(this@MainActivity)
                        NaamJapWidgetRenderer.updateAll(this@MainActivity)
                    }
                    AuthSessionState.Checking -> Unit
                }
            }
            var showOpeningWallpaper by rememberSaveable { mutableStateOf(savedInstanceState == null) }
            var openingStartedAt by rememberSaveable { mutableLongStateOf(0L) }
            val themeChoice = if (themeState.isLoaded) themeState.choice else ThemeChoice.SYSTEM
            val darkTheme = when (themeChoice) {
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK -> true
            }
            SideEffect {
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (themeState.isLoaded) themePreferenceLoaded.set(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isStatusBarContrastEnforced = false
                    window.isNavigationBarContrastEnforced = false
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
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
                    if (showSplash) SplashWallpaperScreen()
                    else {
                        val isPasswordRecovery = authDeepLink.value?.getQueryParameter("type") == "recovery"
                        when {
                            isPasswordRecovery -> AuthScreen(
                                viewModel = authViewModel,
                                passwordRecovery = true,
                                onPasswordRecoveryComplete = { authDeepLink.value = null }
                            )
                            authSession is AuthSessionState.Checking -> SplashWallpaperScreen()
                            authSession is AuthSessionState.SignedIn -> NaamJapApp(
                                themeChoice = themeChoice,
                                onThemeChoice = themeViewModel::selectTheme,
                                account = (authSession as AuthSessionState.SignedIn).account,
                                onSignOut = authViewModel::signOut,
                                openJapRequest = widgetJapRequest,
                                openHomeRequest = widgetHomeRequest
                            )
                            else -> AuthScreen(viewModel = authViewModel)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        authDeepLink.value = intent.data
        consumeWidgetIntent(intent)
        supabaseProvider.client?.handleDeeplinks(intent)
    }

    override fun onResume() {
        super.onResume()
        notificationCoordinator.onForeground()
        if (widgetSessionReady) NaamJapWidgetWork.enqueueRefresh(this)
    }

    private fun consumeWidgetIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_WIDGET_OPEN_JAP, false) == true) {
            widgetJapRequest++
            intent.removeExtra(EXTRA_WIDGET_OPEN_JAP)
        }
        if (intent?.getBooleanExtra(EXTRA_WIDGET_OPEN_HOME, false) == true) {
            widgetHomeRequest++
            intent.removeExtra(EXTRA_WIDGET_OPEN_HOME)
        }
        if (intent?.getBooleanExtra(EXTRA_NOTIFICATION_OPEN_JAP, false) == true) {
            widgetJapRequest++
            intent.removeExtra(EXTRA_NOTIFICATION_OPEN_JAP)
        }
        if (intent?.getBooleanExtra(EXTRA_NOTIFICATION_OPEN_HOME, false) == true) {
            widgetHomeRequest++
            intent.removeExtra(EXTRA_NOTIFICATION_OPEN_HOME)
        }
    }
}
