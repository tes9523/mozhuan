package com.mozhuan.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
    surfaceVariant = Color(0xFFF1EBE1),
    onSurfaceVariant = Color(0xFF6E6A63),
    surfaceContainerLowest = RicePaper,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF6F1E8),
    surfaceContainerHighest = Color(0xFFF6F1E8),
    outline = Color(0xFFE3DCD1),
    outlineVariant = Color(0xFFE3DCD1),
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
    surfaceVariant = Color(0xFF242724),
    onSurfaceVariant = Color(0xFFA8AFA9),
    surfaceContainerLowest = NightInk,
    surfaceContainerLow = Color(0xFF1B1E1C),
    surfaceContainer = Color(0xFF1B1E1C),
    surfaceContainerHigh = Color(0xFF262A27),
    surfaceContainerHighest = Color(0xFF262A27),
    outline = Color(0xFF3A3E3B),
    outlineVariant = Color(0xFF3A3E3B),
)

/** 全局圆角体系：与 OpenMinis 的 12 / 20 / 24 / 28 保持一致。 */
private val MozhuanShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * 全局主题。
 *
 * @param fontScale 字号缩放系数（仅影响 sp 字号，不改变 dp 布局）
 */
@Composable
fun MozhuanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = 1f,
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

    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = density.density,
            fontScale = (density.fontScale * fontScale).coerceIn(0.5f, 3f),
        ),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            shapes = MozhuanShapes,
            typography = MozhuanTypography,
            content = content,
        )
    }
}
