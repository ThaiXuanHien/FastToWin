package com.hienthai.fastowin.screenshot

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.protocol.WalletTransactionSnapshot
import com.hienthai.fastowin.ui.screens.ProfileSection
import com.hienthai.fastowin.ui.screens.ProfileSectionScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme

private fun walletRow(source: String, gold: Int = 0, gems: Int = 0, xp: Int = 0) =
    WalletTransactionSnapshot(source, source, "historical-source", gold, gems, xp, Long.MAX_VALUE)

@Composable
private fun WalletFrame(language: AppLanguage, refunds: Boolean = false) {
    val rows = if (refunds) listOf(
        walletRow("TOURNAMENT_ENTRY_REVERSED", -500),
        walletRow("TOURNAMENT_ENTRY_REFUND", 500),
        walletRow("TOURNAMENT_MAINTENANCE_REFUND", 500),
        walletRow("UNKNOWN_LEGACY", 100),
    ) else listOf(
        walletRow("CLAN_DONATION", -30),
        walletRow("COMPENSATION", 100),
        walletRow("CLAN_CREATION", -10_000, -100),
        walletRow("GOLD_EXCHANGE", 2_000, -10),
        walletRow("ADMIN_GIFT", 100, 5),
        walletRow("ACHIEVEMENT", 250, 0, 50),
        walletRow("MISSION", 50),
    )
    ProvideLocalization(language) {
        FastToWinTheme {
            Surface(Modifier.fillMaxSize()) {
                val profile = ArcadeScreenshotFixtures.profile()
                ProfileSectionScreen(
                    state = ArcadeScreenshotFixtures.homeState().copy(walletTransactions = rows),
                    profile = profile, section = ProfileSection.WALLET, isExternalProfile = false,
                    canEdit = true, onBack = {}, onRefresh = {}, onOpenMatchDetail = {},
                    onCloseMatchDetail = {}, onEquipCosmetics = { _, _ -> }, onClaimMissionReward = {},
                    onSave = { _, _ -> }, onOpenNotifications = {},
                )
            }
        }
    }
}

@PreviewTest
@Preview(name = "sources_vi_phone", widthDp = 375, heightDp = 1200)
@Composable
fun walletKnownSourcesPhone() = WalletFrame(AppLanguage.VIETNAMESE)

@PreviewTest
@Preview(name = "sources_en_desktop", widthDp = 1024, heightDp = 1000)
@Composable
fun walletKnownSourcesDesktop() = WalletFrame(AppLanguage.ENGLISH)

@PreviewTest
@Preview(name = "refunds_vi_phone", widthDp = 375, heightDp = 1000)
@Preview(name = "refunds_vi_large_text", widthDp = 320, heightDp = 1300, fontScale = 1.6f)
@Composable
fun walletRefundSources() = WalletFrame(AppLanguage.VIETNAMESE, refunds = true)
