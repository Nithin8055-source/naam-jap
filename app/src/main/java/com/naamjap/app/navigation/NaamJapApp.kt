package com.naamjap.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

@Composable
fun NaamJapApp(themeChoice: ThemeChoice, onThemeChoice: (ThemeChoice) -> Unit) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    PremiumScaffold(bottomBar = {
        if (currentRoute != Destination.ManualRecord.route) Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        GlassBottomBar(selectedRoute = currentRoute, onSelect = { destination ->
            navController.navigate(destination.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        })
        }
    }) { innerPadding ->
        NavHost(navController = navController, startDestination = Destination.Home.route, modifier = Modifier.padding(innerPadding)) {
            composable(Destination.Home.route) { HomeScreen(onNavigate = { navController.navigate(it) { launchSingleTop = true } }) }
            composable(Destination.Jap.route) { JapScreen() }
            composable(Destination.History.route) { HistoryScreen() }
            composable(Destination.Insights.route) { InsightsScreen() }
            composable(Destination.Settings.route) { SettingsScreen(themeChoice = themeChoice, onThemeChoice = onThemeChoice) }
            composable(Destination.ManualRecord.route) { ManualRecordScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
