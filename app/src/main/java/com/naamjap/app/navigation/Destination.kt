package com.naamjap.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Destination(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Destination("home", "Home", Icons.Default.Home)
    data object Jap : Destination("jap", "Jap", Icons.AutoMirrored.Filled.MenuBook)
    data object History : Destination("history", "History", Icons.Default.CalendarMonth)
    data object Insights : Destination("insights", "Insights", Icons.Default.BarChart)
    data object Settings : Destination("settings", "Settings", Icons.Default.Settings)
    data object ManualRecord : Destination("manual-record", "Manual record", Icons.Default.AddCircle)
    companion object { val primary = listOf(Home, Jap, History, Insights, Settings) }
}
