package com.hienthai.fastowin.screenshot

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.data.preferences.AppThemeMode
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.ui.theme.FastToWinTheme

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
@Preview(name = "small_phone", widthDp = 320, heightDp = 568, showSystemUi = false)
@Preview(name = "large_phone", widthDp = 430, heightDp = 932, showSystemUi = false)
@Preview(name = "tablet", widthDp = 840, heightDp = 1180, showSystemUi = false)
annotation class ArcadeScreenshotDevices

@Composable
fun ArcadeScreenshotFrame(content: @Composable () -> Unit) {
    ArcadeScreenshotFixtures.requireValid()
    ProvideLocalization(AppLanguage.VIETNAMESE) {
        FastToWinTheme(preferences = AppPreferences(themeMode = AppThemeMode.DARK)) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                content()
            }
        }
    }
}
