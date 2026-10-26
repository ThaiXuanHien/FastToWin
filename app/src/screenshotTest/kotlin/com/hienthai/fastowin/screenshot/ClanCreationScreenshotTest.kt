package com.hienthai.fastowin.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.ui.screens.CreateClanDialog

@PreviewTest
@Preview(name = "rich_level9_vi_phone", widthDp = 375, heightDp = 812)
@Preview(name = "rich_level9_vi_large_text", widthDp = 320, heightDp = 932, fontScale = 1.6f)
@Composable
fun clanCreationBelowLevel() = ArcadeScreenshotFrame {
    CreateClanDialog(10_000, 100, {}, { _, _ -> }, playerLevel = 9)
}

@PreviewTest
@Preview(name = "eligible_level10_vi_phone", widthDp = 375, heightDp = 812)
@Composable
fun clanCreationEligible() = ArcadeScreenshotFrame {
    CreateClanDialog(10_000, 100, {}, { _, _ -> }, playerLevel = 10)
}

@PreviewTest
@Preview(name = "missing_gem_level10_en_desktop", widthDp = 1024, heightDp = 768)
@Composable
fun clanCreationMissingGems() = ArcadeScreenshotFrame {
    ProvideLocalization(AppLanguage.ENGLISH) {
        CreateClanDialog(10_000, 99, {}, { _, _ -> }, playerLevel = 10)
    }
}
