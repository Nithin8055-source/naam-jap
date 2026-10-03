package com.naamjap.app.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.naamjap.app.feature.home.HomeScreen
import com.naamjap.app.feature.jap.JapScreen
import com.naamjap.app.feature.history.HistoryScreen
import com.naamjap.app.feature.insights.InsightsScreen
import com.naamjap.app.feature.settings.SettingsScreen
import com.naamjap.app.feature.jap.ManualRecordScreen
import com.naamjap.app.ui.components.GlassBottomBar
import com.naamjap.app.ui.components.PremiumScaffold
import com.naamjap.app.ui.theme.ThemeChoice
import com.naamjap.app.domain.model.AccountIdentity

@Composable
fun NaamJapApp(
    themeChoice: ThemeChoice,
    onThemeChoice: (ThemeChoice) -> Unit,
    account: AccountIdentity,
    onSignOut: () -> Unit
) {
    val navController = rememberNavController()
    val hazeState = remember { HazeState() }
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 64.dp.toPx() }
    val primaryIndex = Destination.primary.indexOfFirst { it.route == currentRoute }
    val isPrimaryRoute = primaryIndex >= 0

    fun navigateToPrimary(destination: Destination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    PremiumScaffold(bottomBar = {
        if (currentRoute != Destination.ManualRecord.route) Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        GlassBottomBar(selectedRoute = currentRoute, hazeState = hazeState, onSelect = { destination ->
            navigateToPrimary(destination)
        })
        }
    }) { innerPadding ->
        var horizontalDrag by remember(currentRoute) { mutableFloatStateOf(0f) }
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            enterTransition = { slideInHorizontally(animationSpec = tween(380, easing = FastOutSlowInEasing)) { width -> width / 6 } + fadeIn(tween(260)) },
            exitTransition = { slideOutHorizontally(animationSpec = tween(380, easing = FastOutSlowInEasing)) { width -> -width / 6 } + fadeOut(tween(220)) },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(380, easing = FastOutSlowInEasing)) { width -> -width / 6 } + fadeIn(tween(260)) },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(380, easing = FastOutSlowInEasing)) { width -> width / 6 } + fadeOut(tween(220)) },
            modifier = Modifier
                .padding(innerPadding)
                .hazeSource(hazeState)
                .pointerInput(currentRoute, isPrimaryRoute) {
                    if (isPrimaryRoute) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, amount -> horizontalDrag += amount },
                            onDragEnd = {
                                if (horizontalDrag <= -swipeThreshold && primaryIndex < Destination.primary.lastIndex) {
                                    navigateToPrimary(Destination.primary[primaryIndex + 1])
                                } else if (horizontalDrag >= swipeThreshold && primaryIndex > 0) {
                                    navigateToPrimary(Destination.primary[primaryIndex - 1])
                                }
                                horizontalDrag = 0f
                            },
                            onDragCancel = { horizontalDrag = 0f }
                        )
                    }
                }
        ) {
            composable(Destination.Home.route) { HomeScreen(onNavigate = { route ->
                if (route == Destination.ManualRecord.route) navController.navigate(route) { launchSingleTop = true }
                else Destination.primary.firstOrNull { it.route == route }?.let(::navigateToPrimary)
            }) }
            composable(Destination.Jap.route) { JapScreen() }
            composable(Destination.History.route) { HistoryScreen() }
            composable(Destination.Insights.route) { InsightsScreen() }
            composable(Destination.Settings.route) { SettingsScreen(themeChoice = themeChoice, onThemeChoice = onThemeChoice, account = account, onSignOut = onSignOut) }
            composable(Destination.ManualRecord.route) { ManualRecordScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
