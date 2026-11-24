package com.hienthai.fastowin.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.data.preferences.AppPreferences

/** Bảng màu sáng A1 "Ngày nắng" — ứng dụng chỉ dùng giao diện sáng. */
private val A1LightColorScheme = lightColorScheme(
    primary = A1Palette.Blue2,
    onPrimary = Color.White,
    primaryContainer = A1Palette.SurfaceAlt,
    onPrimaryContainer = A1Palette.Ink,
    secondary = A1Palette.Purple2,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE5FF),
    onSecondaryContainer = A1Palette.PurpleDeep,
    tertiary = A1Palette.Yellow3,
    onTertiary = A1Palette.Ink,
    tertiaryContainer = A1Palette.Highlight,
    onTertiaryContainer = A1Palette.YellowBorder,
    error = A1Palette.RedText,
    onError = Color.White,
    errorContainer = Color(0xFFFFE1E3),
    onErrorContainer = A1Palette.RedDeep,
    background = A1Palette.Sky2,
    onBackground = A1Palette.Ink,
    surface = A1Palette.Surface,
    onSurface = A1Palette.Ink,
    surfaceVariant = A1Palette.SurfaceAlt,
    onSurfaceVariant = A1Palette.Muted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = A1Palette.SurfaceAlt,
    surfaceContainerHigh = A1Palette.SurfaceAlt2,
    surfaceContainerHighest = A1Palette.SurfaceAlt2,
    outline = A1Palette.Line,
    outlineVariant = A1Palette.SurfaceAlt2,
    scrim = A1Palette.Scrim
)

private val ArcadeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FastToWinTheme(
    preferences: AppPreferences = AppPreferences(),
    content: @Composable () -> Unit
) {
    val currentDensity = LocalDensity.current
    val scaledDensity = Density(
        density = currentDensity.density,
        fontScale = currentDensity.fontScale * preferences.fontScale.multiplier
    )

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = A1LightColorScheme,
            typography = Typography,
            shapes = ArcadeShapes,
            content = content
        )
    }
}
