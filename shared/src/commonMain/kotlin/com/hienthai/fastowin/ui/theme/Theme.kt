package com.hienthai.fastowin.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.data.preferences.AppThemeMode
import com.hienthai.fastowin.data.preferences.BoardStyle

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

private val HighContrastDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFD600),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF3D3400),
    onPrimaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF090909),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF202020),
    onSurfaceVariant = Color.White,
    outline = Color.White,
    outlineVariant = Color(0xFFBDBDBD)
)

private val HighContrastLightColorScheme = lightColorScheme(
    primary = Color(0xFF0036A3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE600),
    onPrimaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color.Black,
    outline = Color.Black,
    outlineVariant = Color(0xFF4A4A4A)
)

@Composable
fun FastToWinTheme(
    preferences: AppPreferences = AppPreferences(),
    content: @Composable () -> Unit
) {
    val darkTheme = when (preferences.themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val colorScheme = when {
        preferences.boardStyle == BoardStyle.HIGH_CONTRAST && darkTheme -> HighContrastDarkColorScheme
        preferences.boardStyle == BoardStyle.HIGH_CONTRAST -> HighContrastLightColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val currentDensity = LocalDensity.current
    val scaledDensity = Density(
        density = currentDensity.density,
        fontScale = currentDensity.fontScale * preferences.fontScale.multiplier
    )

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
