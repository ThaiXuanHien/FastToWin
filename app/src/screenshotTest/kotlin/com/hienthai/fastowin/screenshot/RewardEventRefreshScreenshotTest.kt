package com.hienthai.fastowin.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.data.preferences.AppThemeMode
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.RewardEventPublicView
import com.hienthai.fastowin.protocol.RewardEventState
import com.hienthai.fastowin.state.RewardEventLoad
import com.hienthai.fastowin.state.RewardEventUiState
import com.hienthai.fastowin.ui.components.RewardEventBanner
import com.hienthai.fastowin.ui.theme.FastToWinTheme

@PreviewTest
@Preview(name = "phone_vi_light", widthDp = 375, heightDp = 380, showSystemUi = false)
@Composable
fun rewardEventRefreshPhone() = RefreshComparison(AppLanguage.VIETNAMESE, AppThemeMode.LIGHT)

@PreviewTest
@Preview(name = "desktop_en_dark", widthDp = 1024, heightDp = 380, showSystemUi = false)
@Composable
fun rewardEventRefreshDesktop() = RefreshComparison(AppLanguage.ENGLISH, AppThemeMode.DARK)

@PreviewTest
@Preview(name = "large_text_vi_dark", widthDp = 320, heightDp = 560, fontScale = 1.6f, showSystemUi = false)
@Composable
fun rewardEventRefreshLargeText() = RefreshComparison(AppLanguage.VIETNAMESE, AppThemeMode.DARK)

@PreviewTest
@Preview(name = "landscape_en_light", widthDp = 844, heightDp = 380, showSystemUi = false)
@Composable
fun rewardEventRefreshLandscape() = RefreshComparison(AppLanguage.ENGLISH, AppThemeMode.LIGHT)

@Composable
private fun RefreshComparison(language: AppLanguage, theme: AppThemeMode) {
    val event = RewardEventPublicView("october", 1, RewardEventState.ACTIVE,
        "Tháng Mười bứt tốc", "October sprint", "Tăng thưởng", "Bonus rewards", "Asia/Bangkok",
        ArcadeScreenshotFixtures.NOW_MILLIS - 1000, ArcadeScreenshotFixtures.NOW_MILLIS + 3600000,
        2, 2, setOf(MatchType.CASUAL, MatchType.RANKED))
    val ready = RewardEventUiState(load = RewardEventLoad.READY, items = listOf(event),
        total = 1, serverNowEpochMillis = ArcadeScreenshotFixtures.NOW_MILLIS)
    ProvideLocalization(language) {
        FastToWinTheme(preferences = AppPreferences(themeMode = theme, visualEffectsEnabled = false)) {
            Surface {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Before refresh")
                    RewardEventBanner(ready, {}, {})
                    Text("During refresh")
                    RewardEventBanner(ready.copy(isRefreshing = true), {}, {})
                }
            }
        }
    }
}
