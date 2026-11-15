package com.mozhuan.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = InkTeal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB6EDE5),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Bronze,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2E1D2),
    onSecondaryContainer = Color(0xFF2E1A0B),
    background = RicePaper,
    onBackground = Color(0xFF1B1C1A),
    surface = RicePaper,
    onSurface = Color(0xFF1B1C1A),
    surfaceVariant = Color(0xFFE9E2D9),
    onSurfaceVariant = Color(0xFF4A453F),
    outline = Color(0xFF7C766E),
)

private val DarkColors = darkColorScheme(
    primary = InkTealLight,
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF1A4B46),
    onPrimaryContainer = Color(0xFFB6EDE5),
    secondary = BronzeLight,
    onSecondary = Color(0xFF3F2C1B),
    secondaryContainer = Color(0xFF574331),
    onSecondaryContainer = Color(0xFFF2E1D2),
    background = NightInk,
    onBackground = Color(0xFFE5E2DC),
    surface = NightInk,
    onSurface = Color(0xFFE5E2DC),
    surfaceVariant = Color(0xFF403A34),
    onSurfaceVariant = Color(0xFFC9C3BB),
    outline = Color(0xFF938F87),
)

@Composable
fun MozhuanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MozhuanTypography,
        content = content,
    )
}
