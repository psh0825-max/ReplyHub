package com.replyhub.app.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
    onPrimary = Color(0xFF003823),
    primaryContainer = Color(0xFF0B5037),
    onPrimaryContainer = Color(0xFF9AF2C1),
    secondary = Color(0xFFB8CCC0),
    onSecondary = Color(0xFF23352D),
    secondaryContainer = Color(0xFF354B41),
    onSecondaryContainer = Color(0xFFD4E8DC),
    tertiary = Color(0xFFD9C775),
    onTertiary = Color(0xFF393000),
    tertiaryContainer = Color(0xFF514700),
    onTertiaryContainer = Color(0xFFF6E48E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101411),
    onBackground = Color(0xFFE0E4DF),
    surface = Color(0xFF101411),
    onSurface = Color(0xFFE0E4DF),
    surfaceVariant = Color(0xFF3F4943),
    onSurfaceVariant = Color(0xFFBFC9C2),
    outline = Color(0xFF89938D),
    outlineVariant = Color(0xFF3F4943),
    inverseSurface = Color(0xFFE0E4DF),
    inverseOnSurface = Color(0xFF2D322E),
    inversePrimary = Color(0xFF146B4A),
    surfaceTint = Color(0xFF78D5A5),
    scrim = Color.Black,
)

@Composable
fun ReplyHubTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
