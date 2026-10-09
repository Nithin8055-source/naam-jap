package com.naamjap.app.feature.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.naamjap.app.R

@Composable
fun SplashWallpaperScreen() {
    Box(Modifier.fillMaxSize().background(Color(0xFFFFF9F1))) {
        Image(
            painter = painterResource(R.drawable.naam_jap_splash_wallpaper),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().blur(32.dp).alpha(.68f),
            contentScale = ContentScale.Crop
        )
        Image(
            painter = painterResource(R.drawable.naam_jap_splash_wallpaper),
            contentDescription = "Naam Jap — Chant, Count, Connect",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}
