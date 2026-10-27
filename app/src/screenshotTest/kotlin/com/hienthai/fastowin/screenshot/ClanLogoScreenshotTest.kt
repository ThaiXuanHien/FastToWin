package com.hienthai.fastowin.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.ui.screens.ClanLogoDialog

@PreviewTest
@Preview(name = "locked_level1_vi_phone", widthDp = 375, heightDp = 812)
@Preview(name = "locked_level1_vi_large_text", widthDp = 320, heightDp = 932, fontScale = 1.6f)
@Composable
fun clanLogoLockedChoices() = ArcadeScreenshotFrame {
    ClanLogoDialog("shield", clanLevel = 1, onSelect = {}, onDismiss = {})
}

@PreviewTest
@Preview(name = "legacy_selected_crown_vi_phone", widthDp = 375, heightDp = 812)
@Composable
fun clanLogoLegacySelected() = ArcadeScreenshotFrame {
    ClanLogoDialog("crown", clanLevel = 1, onSelect = {}, onDismiss = {})
}

@PreviewTest
@Preview(name = "all_unlocked_level30_en_desktop", widthDp = 1024, heightDp = 768)
@Composable
fun clanLogoAllUnlocked() = ArcadeScreenshotFrame {
    ProvideLocalization(AppLanguage.ENGLISH) {
        ClanLogoDialog("kirin", clanLevel = 30, onSelect = {}, onDismiss = {})
    }
}
