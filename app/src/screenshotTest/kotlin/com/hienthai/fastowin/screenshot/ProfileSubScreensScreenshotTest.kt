package com.hienthai.fastowin.screenshot

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.protocol.AchievementSnapshot
import com.hienthai.fastowin.protocol.CosmeticSnapshot
import com.hienthai.fastowin.protocol.CosmeticType
import com.hienthai.fastowin.protocol.GameModeStatisticsSnapshot
import com.hienthai.fastowin.protocol.MatchHistoryOutcome
import com.hienthai.fastowin.protocol.MatchHistorySnapshot
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.PlayerStatisticsSnapshot
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.ui.components.A1SkyBackdrop
import com.hienthai.fastowin.ui.components.LocalAvatarFramePreview
import com.hienthai.fastowin.ui.screens.ProfileSection
import com.hienthai.fastowin.ui.screens.ProfileSectionScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme

/** Dữ liệu giống bảng thiết kế "Tài khoản – màn con 1" để đối chiếu từng chi tiết. */
private fun designProfile(): PlayerProfileSnapshot {
    val now = System.currentTimeMillis()
    fun match(id: String, name: String, mode: ProtocolGameMode, outcome: MatchHistoryOutcome, ago: Long, elo: Int, ranked: Boolean) =
        MatchHistorySnapshot(id, "Phòng", mode, name, 40, 30, outcome, now - ago, elo, if (ranked) MatchType.RANKED else MatchType.CASUAL)
    fun ach(code: String, unlocked: Boolean) = AchievementSnapshot(code, code, "Mô tả $code", if (unlocked) now - 1 else 0L)
    val base = ArcadeScreenshotFixtures.profile()
    return base.copy(
        statistics = PlayerStatisticsSnapshot(
            totalMatches = 1284, wins = 784, losses = 480, draws = 20, highestScore = 50,
            bestWinStreak = 14, averageReactionMillis = 920, eloRating = 1742
        ),
        modeStatistics = listOf(
            GameModeStatisticsSnapshot(ProtocolGameMode.ORDER, 612, 386, 220, 6, 41, 33),
            GameModeStatisticsSnapshot(ProtocolGameMode.COMBO, 280, 163, 110, 7, 14, 10),
            GameModeStatisticsSnapshot(ProtocolGameMode.RANDOM_TARGET, 201, 121, 78, 2, 58, 40),
            GameModeStatisticsSnapshot(ProtocolGameMode.SPEED_UP, 191, 113, 75, 3, 47, 35)
        ),
        recentMatches = listOf(
            match("m1", "Minh", ProtocolGameMode.ORDER, MatchHistoryOutcome.WIN, 5 * 60_000L, 18, true),
            match("m2", "Lan", ProtocolGameMode.SPEED_UP, MatchHistoryOutcome.WIN, 3_600_000L, 15, true),
            match("m3", "TocDoAnh", ProtocolGameMode.ORDER, MatchHistoryOutcome.LOSS, 26 * 3_600_000L, -9, true),
            match("m4", "Khoa", ProtocolGameMode.COMBO, MatchHistoryOutcome.DRAW, 30 * 3_600_000L, 0, false),
            match("m5", "Vy", ProtocolGameMode.RANDOM_TARGET, MatchHistoryOutcome.WIN, 50 * 3_600_000L, 0, false)
        ),
        achievements = listOf(
            ach("FIRST_WIN", true), ach("SPEED_50", true), ach("PERFECT_GAME", true), ach("WIN_10", true), ach("DAILY_STREAK_7", false)
        ),
        progression = base.progression.copy(
            cosmetics = listOf(
                CosmeticSnapshot("frame_diamond", "Kim Cương", CosmeticType.FRAME, true, true),
                CosmeticSnapshot("frame_gold", "Vàng", CosmeticType.FRAME, true, false),
                CosmeticSnapshot("frame_default", "Cơ bản", CosmeticType.FRAME, true, false),
                CosmeticSnapshot("frame_emperor", "Đế Vương", CosmeticType.FRAME, false, false),
                CosmeticSnapshot("frame_legend", "Huyền Thoại", CosmeticType.FRAME, false, false),
                CosmeticSnapshot("title_rookie", "Khai Chiến", CosmeticType.TITLE, true, false),
                CosmeticSnapshot("title_godspeed", "Tốc Độ Ánh Sáng", CosmeticType.TITLE, true, true),
                CosmeticSnapshot("title_unbeaten", "Bất Bại", CosmeticType.TITLE, false, false)
            )
        )
    )
}

@Composable
private fun SubScreenFrame(section: ProfileSection, language: AppLanguage = AppLanguage.VIETNAMESE) {
    ProvideLocalization(language) {
        FastToWinTheme {
            CompositionLocalProvider(LocalAvatarFramePreview provides screenshotFramePainter) {
                A1SkyBackdrop(Modifier.fillMaxSize(), showHills = false) {
                    val profile = designProfile()
                    ProfileSectionScreen(
                        state = ArcadeScreenshotFixtures.homeState().copy(profile = profile),
                        profile = profile, section = section, isExternalProfile = false,
                        canEdit = true, onBack = {}, onRefresh = {}, onOpenMatchDetail = {},
                        onCloseMatchDetail = {}, onEquipCosmetics = { _, _ -> }, onClaimMissionReward = {},
                        onSave = { _, _ -> }, onOpenNotifications = {},
                    )
                }
            }
        }
    }
}

@PreviewTest
@Preview(name = "design_390", widthDp = 390, heightDp = 844)
@Preview(name = "small_large_text", widthDp = 360, heightDp = 740, fontScale = 1.6f)
@Composable
fun profileStatisticsA1() = SubScreenFrame(ProfileSection.STATISTICS)

@PreviewTest
@Preview(name = "design_390", widthDp = 390, heightDp = 844)
@Preview(name = "small_large_text", widthDp = 360, heightDp = 740, fontScale = 1.6f)
@Composable
fun profileMatchHistoryA1() = SubScreenFrame(ProfileSection.RECENT_MATCHES)

@PreviewTest
@Preview(name = "design_390", widthDp = 390, heightDp = 844)
@Preview(name = "small_large_text", widthDp = 360, heightDp = 740, fontScale = 1.6f)
@Preview(name = "tablet", widthDp = 840, heightDp = 900)
@Composable
fun profileCollectionA1() = SubScreenFrame(ProfileSection.COLLECTION)
