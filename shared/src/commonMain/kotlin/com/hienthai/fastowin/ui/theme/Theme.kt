package com.hienthai.fastowin.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.data.preferences.AppPreferences

private val ArcadeDarkColorScheme = darkColorScheme(
    primary = ArcadePalette.Blue300,
    onPrimary = ArcadePalette.Navy950,
    primaryContainer = ArcadePalette.Blue900,
    onPrimaryContainer = Color.White,
    secondary = ArcadePalette.Violet400,
    onSecondary = ArcadePalette.Navy950,
    secondaryContainer = ArcadePalette.Violet900,
    onSecondaryContainer = Color.White,
    tertiary = ArcadePalette.Coral400,
    onTertiary = ArcadePalette.Navy950,
    tertiaryContainer = Color(0xFF66172B),
    onTertiaryContainer = Color.White,
    error = ArcadePalette.Coral400,
    onError = ArcadePalette.Navy950,
    errorContainer = Color(0xFF68162A),
    onErrorContainer = Color.White,
    background = ArcadePalette.Navy950,
    onBackground = Color(0xFFF2F6FF),
    surface = ArcadePalette.Navy900,
    onSurface = Color(0xFFF2F6FF),
    surfaceVariant = ArcadePalette.Navy800,
    onSurfaceVariant = Color(0xFFC5D5F3),
    surfaceContainerLowest = ArcadePalette.Navy950,
    surfaceContainerLow = ArcadePalette.Navy900,
    surfaceContainer = ArcadePalette.Navy800,
    surfaceContainerHigh = ArcadePalette.Navy700,
    surfaceContainerHighest = Color(0xFF1C4488),
    outline = ArcadePalette.OutlineDark,
    outlineVariant = Color(0xFF314D83)
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
            colorScheme = ArcadeDarkColorScheme,
            typography = Typography,
            shapes = ArcadeShapes,
            content = content
        )
    }
}
