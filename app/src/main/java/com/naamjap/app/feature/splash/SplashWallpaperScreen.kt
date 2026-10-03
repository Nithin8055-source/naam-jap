package com.naamjap.app.feature.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.naamjap.app.R

@Composable
fun SplashWallpaperScreen(darkTheme: Boolean) {
    Box(Modifier.fillMaxSize().background(if (darkTheme) Color(0xFF171512) else Color(0xFFFFF9F1))) {
        Image(
            painter = painterResource(R.drawable.bg_opening_wallpaper),
            contentDescription = "Naam Jap sunrise lotus",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
        if (darkTheme) Box(Modifier.fillMaxSize().background(Color(0xFF171512).copy(alpha = .20f)))
    }
}
