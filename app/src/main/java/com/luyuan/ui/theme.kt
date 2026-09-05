package com.luyuan.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7F77DD),
    secondary = Color(0xFF5DCAA5),
    background = Color(0xFF0E1116),
    surface = Color(0xFF161B22),
    surfaceVariant = Color(0xFF21262D),
    onBackground = Color(0xFFE6EDF3),
    onSurface = Color(0xFFE6EDF3),
    onSurfaceVariant = Color(0xFF8B949E),
    onPrimary = Color(0xFF0E1116)
)

@Composable
fun LuyuanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
