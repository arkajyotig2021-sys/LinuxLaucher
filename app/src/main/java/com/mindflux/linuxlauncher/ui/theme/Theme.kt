package com.mindflux.linuxlauncher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF315CBB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5FF),
    onPrimaryContainer = Color(0xFF071A49),
    secondary = Color(0xFF52617D),
    background = Color(0xFFF8F9FF),
    surface = Color(0xFFF8F9FF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4C5FF),
    onPrimary = Color(0xFF102D68),
    primaryContainer = Color(0xFF284486),
    onPrimaryContainer = Color(0xFFDCE5FF),
    secondary = Color(0xFFBAC6E2),
    background = Color(0xFF101318),
    surface = Color(0xFF101318)
)

@Composable
fun LinuxLauncherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
