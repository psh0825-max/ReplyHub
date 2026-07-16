package com.replyhub.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF146B4A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2F5E3),
    onPrimaryContainer = Color(0xFF073825),
    secondary = Color(0xFF475E54),
    secondaryContainer = Color(0xFFD8E7DF),
    tertiary = Color(0xFF6C5C1C),
    tertiaryContainer = Color(0xFFF4E7A8),
    error = Color(0xFFB3261E),
    background = Color(0xFFF7F8F4),
    surface = Color(0xFFF7F8F4),
    surfaceVariant = Color(0xFFE2E5DF),
    outline = Color(0xFF737973),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF78D5A5),
    secondary = Color(0xFFB8CCC0),
    tertiary = Color(0xFFD9C775),
)

@Composable
fun ReplyHubTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

