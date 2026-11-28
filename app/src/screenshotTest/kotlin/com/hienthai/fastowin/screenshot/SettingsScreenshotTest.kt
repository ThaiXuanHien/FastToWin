package com.hienthai.fastowin.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.ui.screens.SettingsScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme

@PreviewTest
@Preview(name = "phone", widthDp = 375, heightDp = 1200, showSystemUi = false)
@Preview(name = "desktop", widthDp = 1024, heightDp = 1000, showSystemUi = false)
@Preview(name = "large_text", widthDp = 375, heightDp = 1500, fontScale = 1.6f, showSystemUi = false)
@Preview(name = "landscape", widthDp = 844, heightDp = 700, showSystemUi = false)
@Composable
fun darkOnlySettings() {
    val preferences = AppPreferences(languageCode = "vi", visualEffectsEnabled = false)
    ProvideLocalization(AppLanguage.VIETNAMESE) {
        FastToWinTheme(preferences) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground
            ) {
                SettingsScreen(preferences, {}, {}, {}, {}, systemLanguageTags = listOf("vi-VN"))
            }
        }
    }
}

/** Kích thước bảng thiết kế A1 (390dp), thông báo đẩy đang bật để đối chiếu với bảng "Cài đặt". */
@PreviewTest
@Preview(name = "design_390", widthDp = 390, heightDp = 1180, showSystemUi = false)
@Preview(name = "small_large_text", widthDp = 360, heightDp = 1800, fontScale = 1.6f, showSystemUi = false)
@Composable
fun settingsA1Design() {
    val preferences = AppPreferences(languageCode = "vi")
    ProvideLocalization(AppLanguage.VIETNAMESE) {
        FastToWinTheme(preferences) {
            SettingsScreen(
                preferences, {}, {}, {}, {},
                pushStatus = com.hienthai.fastowin.platform.AppPushStatus.ENABLED,
                systemLanguageTags = listOf("vi-VN")
            )
        }
    }
}

@PreviewTest
@Preview(name = "design_390", widthDp = 390, heightDp = 844, showSystemUi = false)
@Composable
fun pushOptInA1Design() {
    ProvideLocalization(AppLanguage.VIETNAMESE) {
        FastToWinTheme {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                com.hienthai.fastowin.ui.components.PushOptInDialog(onEnable = {}, onLater = {})
            }
        }
    }
}
