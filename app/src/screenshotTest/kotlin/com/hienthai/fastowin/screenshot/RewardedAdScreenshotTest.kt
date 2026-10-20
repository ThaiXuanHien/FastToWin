package com.hienthai.fastowin.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.localization.*
import com.hienthai.fastowin.platform.RewardedAdGatewayState
import com.hienthai.fastowin.protocol.RewardedAdAvailability
import com.hienthai.fastowin.ui.components.*
import com.hienthai.fastowin.ui.screens.SettingsScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme

@Composable
private fun RewardedAdPreview(content: @Composable () -> Unit) {
    ProvideLocalization(AppLanguage.VIETNAMESE) {
        FastToWinTheme(AppPreferences(languageCode = "vi", visualEffectsEnabled = false)) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground, content = content)
        }
    }
}
@PreviewTest
@Preview(name = "age_phone", widthDp = 375, heightDp = 812)
@Preview(name = "age_large_text", widthDp = 320, heightDp = 812, fontScale = 1.6f)
@Preview(name = "age_desktop", widthDp = 1024, heightDp = 768)
@Composable
fun rewardedAdAge() = RewardedAdPreview { RewardedAdAgeDialog {} }

@PreviewTest
@Preview(name = "pending_phone", widthDp = 375, heightDp = 812)
@Preview(name = "pending_landscape", widthDp = 844, heightDp = 390, fontScale = 1.3f)
@Composable
fun rewardedAdPending() = RewardedAdPreview {
    PlayQuotaExhaustedDialog(null,
        RewardedAdGatewayState(availability = RewardedAdAvailability.MOBILE_PRODUCTION, isReady = true,
            isPendingVerification = true, notice = LocalizedText(TextKey.RewardedAdPending)), false, {}, {})
}
@PreviewTest
@Preview(name = "loading_phone", widthDp = 375, heightDp = 812)
@Composable
fun rewardedAdLoading() = RewardedAdPreview {
    PlayQuotaExhaustedDialog(null,
        RewardedAdGatewayState(availability = RewardedAdAvailability.MOBILE_PRODUCTION, isReady = true,
            isLoading = true, notice = LocalizedText(TextKey.RewardedAdLoading)), false, {}, {})
}
@PreviewTest
@Preview(name = "privacy_settings", widthDp = 1024, heightDp = 1000)
@Composable
fun rewardedAdPrivacySettings() = RewardedAdPreview {
    SettingsScreen(AppPreferences(languageCode = "vi", visualEffectsEnabled = false), {}, {}, {}, {},
        adPrivacyOptionsRequired = true, systemLanguageTags = listOf("vi-VN"))
}
