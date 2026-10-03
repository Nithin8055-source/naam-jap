package com.naamjap.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.naamjap.app.navigation.Destination
import com.naamjap.app.ui.theme.NaamJapTheme

@Preview(showBackground = true)
@Composable
private fun PremiumComponentPreview() {
    NaamJapTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DailyCountDisplay("12,508", "Today's Naam Jap", preview = true)
                GoalProgressCard("Goal · 15,000", 83, "2,492 to go", .83f, Modifier.fillMaxWidth())
                PremiumCard(Modifier.fillMaxWidth()) { EmptyState("Your practice begins here", "This is where your sessions will appear.") }
                GlassBottomBar(Destination.Home.route, onSelect = {})
            }
        }
    }
}
