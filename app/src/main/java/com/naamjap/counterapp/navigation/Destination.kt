package com.naamjap.counterapp.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Destination(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Destination("home", "Home", Icons.Outlined.Home)
    data object Jap : Destination("jap", "Jap", Icons.Outlined.SelfImprovement)
    data object History : Destination("history", "History", Icons.Outlined.CalendarMonth)
    data object Insights : Destination("insights", "Insights", Icons.Outlined.BarChart)
    data object Settings : Destination("settings", "Settings", Icons.Outlined.Settings)
    data object ManualRecord : Destination("manual-record", "Manual record", Icons.Default.AddCircle)
    companion object { val primary = listOf(Home, Jap, History, Insights, Settings) }
}
