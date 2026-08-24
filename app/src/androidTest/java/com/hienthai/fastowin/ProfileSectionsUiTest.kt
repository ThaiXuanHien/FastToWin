package com.hienthai.fastowin

import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.then
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.protocol.CosmeticSnapshot
import com.hienthai.fastowin.protocol.CosmeticType
import com.hienthai.fastowin.protocol.AchievementSnapshot
import com.hienthai.fastowin.protocol.GameModeStatisticsSnapshot
import com.hienthai.fastowin.protocol.MatchHistoryOutcome
import com.hienthai.fastowin.protocol.MatchHistorySnapshot
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.MissionDifficulty
import com.hienthai.fastowin.protocol.MissionSnapshot
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.PlayerProgressionSnapshot
import com.hienthai.fastowin.protocol.PlayerStatisticsSnapshot
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.WalletTransactionSnapshot
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.ui.screens.ProfileScreen
import com.hienthai.fastowin.ui.screens.ProfileSection
import com.hienthai.fastowin.ui.screens.ProfileSectionScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ProfileSectionsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun profile_routesActivityItemsAndKeepsSettingsAtTheBottom() {
        var openedSection: ProfileSection? = null
        var openedSettings = false
        val profile = profileFixture()

        setAdaptiveContent(430.dp, 932.dp) {
            ProfileScreen(
                serverUrl = "",
                state = GameState(profile = profile),
                onBack = {},
                onRefresh = {},
                onOpenMatchDetail = {},
                onCloseMatchDetail = {},
                onEquipCosmetics = { _, _ -> },
                onClaimMissionReward = {},
                onSave = { _, _ -> },
                onUploadAvatar = {},
                canEdit = true,
                isAccountLoading = false,
                accountError = null,
                accountNotice = null,
                accountSessions = emptyList(),
                areSessionsLoading = false,
                onChangePassword = { _, _ -> },
                onDeleteAccount = {},
                onClearAccountFeedback = {},
                onLoadSessions = {},
                onRevokeSession = {},
                onRevokeAllSessions = {},
                onLogout = {},
                onOpenSettings = { openedSettings = true },
                onOpenSection = { openedSection = it },
                showBackButton = false
            )
        }

        val cardBounds = composeRule.onNodeWithTag("profile_identity_card").fetchSemanticsNode().boundsInRoot
        val editBounds = composeRule.onNodeWithTag("profile_edit").fetchSemanticsNode().boundsInRoot
        assertTrue(editBounds.left >= cardBounds.left && editBounds.top >= cardBounds.top)
        assertTrue(editBounds.right <= cardBounds.right && editBounds.bottom <= cardBounds.bottom)
        composeRule.onNodeWithContentDescription("Chỉnh sửa hồ sơ").assertIsDisplayed()

        composeRule.onNodeWithTag("profile_section_statistics").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(ProfileSection.STATISTICS, openedSection) }

        composeRule.onNodeWithTag("profile_section_wallet").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(ProfileSection.WALLET, openedSection) }

        composeRule.onNodeWithTag("profile_section_missions").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(ProfileSection.MISSIONS, openedSection) }

        composeRule.onNodeWithTag("profile_section_collection").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(ProfileSection.COLLECTION, openedSection) }

        composeRule.onNodeWithTag("profile_section_recent_matches").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(ProfileSection.RECENT_MATCHES, openedSection) }

        composeRule.onNodeWithTag("profile_settings").performScrollTo().assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(openedSettings) }
    }

    @Test
    fun walletHistory_smallPhoneShowsIncomeAndSpending() {
        val profile = profileFixture()
        val transactions = listOf(
            WalletTransactionSnapshot(
                id = "reward",
                sourceType = "MATCH",
                sourceId = "match-1",
                goldDelta = 100,
                xpDelta = 30,
                createdAtEpochMillis = System.currentTimeMillis()
            ),
            WalletTransactionSnapshot(
                id = "purchase",
                sourceType = "COSMETIC_PURCHASE",
                sourceId = "frame-gold",
                goldDelta = -500,
                createdAtEpochMillis = System.currentTimeMillis() - 60_000L
            )
        )

        setAdaptiveContent(320.dp, 568.dp, fontScale = 1.4f) {
            ProfileSectionScreen(
                state = GameState(profile = profile, walletTransactions = transactions),
                profile = profile,
                section = ProfileSection.WALLET,
                isExternalProfile = false,
                canEdit = true,
                onBack = {},
                onRefresh = {},
                onOpenMatchDetail = {},
                onCloseMatchDetail = {},
                onEquipCosmetics = { _, _ -> },
                onClaimMissionReward = {},
                onSave = { _, _ -> },
                onOpenNotifications = {}
            )
        }

        composeRule.onNodeWithTag("profile_section_screen:WALLET").assertIsDisplayed()
        composeRule.onNodeWithText("Thưởng trận đấu").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("nhận 100 vàng, nhận 30 XP").assertIsDisplayed()
        composeRule.onNodeWithText("Mua vật phẩm").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("dùng 500 vàng").assertIsDisplayed()
    }

    @Test
    fun statisticsAndAchievements_smallPhoneAndLargeText_remainReachable() {
        val profile = profileFixture().copy(
            statistics = PlayerStatisticsSnapshot(
                totalMatches = 20,
                wins = 12,
                losses = 6,
                draws = 2,
                highestScore = 50,
                currentWinStreak = 3,
                bestWinStreak = 7,
                correctSelections = 480,
                wrongSelections = 20,
                averageReactionMillis = 612,
                eloRating = 1_240
            ),
            modeStatistics = listOf(
                GameModeStatisticsSnapshot(
                    gameMode = ProtocolGameMode.ORDER,
                    totalMatches = 10,
                    wins = 6,
                    losses = 3,
                    draws = 1,
                    highestScore = 50,
                    averageScore = 34
                )
            ),
            achievements = listOf(
                AchievementSnapshot(
                    code = "FIRST_WIN",
                    title = "Chiến thắng đầu tiên",
                    description = "Thắng trận đầu tiên của bạn",
                    unlockedAtEpochMillis = 1L
                )
            )
        )

        setAdaptiveContent(320.dp, 568.dp, fontScale = 1.6f) {
            ProfileSectionScreen(
                state = GameState(profile = profile),
                profile = profile,
                section = ProfileSection.STATISTICS,
                isExternalProfile = false,
                canEdit = true,
                onBack = {},
                onRefresh = {},
                onOpenMatchDetail = {},
                onCloseMatchDetail = {},
                onEquipCosmetics = { _, _ -> },
                onClaimMissionReward = {},
                onSave = { _, _ -> },
                onOpenNotifications = {}
            )
        }

        composeRule.onNodeWithTag("profile_section_screen:STATISTICS").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_statistics_content").assertExists()
        composeRule.onNodeWithText("Thống kê theo chế độ").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Chiến thắng đầu tiên").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun missions_smallPhoneAndLargeText_remainsScrollableAndClaimsReward() {
        var claimedCode: String? = null
        var wentBack = false
        val profile = profileFixture()

        setAdaptiveContent(320.dp, 568.dp, fontScale = 1.6f) {
            ProfileSectionScreen(
                state = GameState(profile = profile),
                profile = profile,
                section = ProfileSection.MISSIONS,
                isExternalProfile = false,
                canEdit = true,
                onBack = { wentBack = true },
                onRefresh = {},
                onOpenMatchDetail = {},
                onCloseMatchDetail = {},
                onEquipCosmetics = { _, _ -> },
                onClaimMissionReward = { claimedCode = it },
                onSave = { _, _ -> },
                onOpenNotifications = {}
            )
        }

        composeRule.onNodeWithTag("profile_section_screen:MISSIONS").assertIsDisplayed()
        composeRule.onNodeWithText("Nhiệm vụ").assertIsDisplayed()
        composeRule.onNodeWithTag("mission_difficulty_normal").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("mission_difficulty_elite").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("claim_mission:DAILY_WIN_1").performScrollTo().assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("DAILY_WIN_1", claimedCode) }

        assertTrue(composeRule.onAllNodesWithText("Trang chủ").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithContentDescription("Cài đặt").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithContentDescription("Quay lại").performClick()
        composeRule.runOnIdle { assertTrue(wentBack) }
    }

    @Test
    fun missions_claimedRewardBecomesDisabledClaimedButton() {
        val baseProfile = profileFixture()
        val claimedMission = baseProfile.progression.dailyMissions.single().copy(rewardClaimed = true)
        val profile = baseProfile.copy(
            progression = baseProfile.progression.copy(dailyMissions = listOf(claimedMission))
        )

        setAdaptiveContent(430.dp, 932.dp) {
            ProfileSectionScreen(
                state = GameState(profile = profile),
                profile = profile,
                section = ProfileSection.MISSIONS,
                isExternalProfile = false,
                canEdit = true,
                onBack = {},
                onRefresh = {},
                onOpenMatchDetail = {},
                onCloseMatchDetail = {},
                onEquipCosmetics = { _, _ -> },
                onClaimMissionReward = {},
                onSave = { _, _ -> },
                onOpenNotifications = {}
            )
        }

        composeRule.onNodeWithTag("claim_mission:${claimedMission.code}")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeRule.onNodeWithText("Đã nhận").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithContentDescription("Đã nhận thưởng").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun collection_landscapeAndLargeText_keepsAllGroupsReachable() {
        var equippedFrame: Pair<String, String>? = null
        val profile = profileFixture()

        setAdaptiveContent(720.dp, 400.dp, fontScale = 1.5f) {
            ProfileSectionScreen(
                state = GameState(profile = profile),
                profile = profile,
                section = ProfileSection.COLLECTION,
                isExternalProfile = false,
                canEdit = true,
                onBack = {},
                onRefresh = {},
                onOpenMatchDetail = {},
                onCloseMatchDetail = {},
                onEquipCosmetics = { frame, title -> equippedFrame = frame to title },
                onClaimMissionReward = {},
                onSave = { _, _ -> },
                onOpenNotifications = {}
            )
        }

        composeRule.onNodeWithTag("profile_section_screen:COLLECTION").assertIsDisplayed()
        composeRule.onNodeWithText("Khung").assertIsDisplayed()
        composeRule.onNodeWithText("Khung Bền bỉ").performClick()
        composeRule.runOnIdle {
            assertEquals("frame_persistent" to "title_rookie", equippedFrame)
        }
        composeRule.onNodeWithText("Danh hiệu").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Ảnh đại diện đặc biệt").performScrollTo().assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Trang chủ").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun recentMatches_landscape_filtersAndOpensMatchDetail() {
        var openedMatchId: String? = null
        val profile = profileFixture()

        setAdaptiveContent(720.dp, 400.dp, fontScale = 1.3f) {
            ProfileSectionScreen(
                state = GameState(profile = profile),
                profile = profile,
                section = ProfileSection.RECENT_MATCHES,
                isExternalProfile = false,
                canEdit = true,
                onBack = {},
                onRefresh = {},
                onOpenMatchDetail = { openedMatchId = it },
                onCloseMatchDetail = {},
                onEquipCosmetics = { _, _ -> },
                onClaimMissionReward = {},
                onSave = { _, _ -> },
                onOpenNotifications = {}
            )
        }

        composeRule.onNodeWithTag("profile_section_screen:RECENT_MATCHES").assertIsDisplayed()
        composeRule.onNodeWithTag("history_filter_loss").performScrollTo().performClick()
        composeRule.onNodeWithTag("match_history:match-loss").performScrollTo().assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("match-loss", openedMatchId) }
        composeRule.onNodeWithTag("match_history:match-win").assertDoesNotExist()
    }

    private fun setAdaptiveContent(
        width: Dp,
        height: Dp,
        fontScale: Float = 1f,
        content: @androidx.compose.runtime.Composable () -> Unit
    ) {
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(width, height)) then
                    DeviceConfigurationOverride.FontScale(fontScale)
            ) {
                FastToWinTheme { content() }
            }
        }
    }
}

private fun profileFixture(): PlayerProfileSnapshot = PlayerProfileSnapshot(
    userId = "player-hien",
    displayName = "Hiền với biệt danh khá dài để kiểm tra",
    playerCode = "HIEN001",
    progression = PlayerProgressionSnapshot(
        gold = 12_500,
        gems = 25,
        dailyMissions = listOf(
            MissionSnapshot(
                code = "DAILY_WIN_1",
                title = "Thắng một trận hôm nay mà không bấm sai",
                progress = 1,
                target = 1,
                completed = true,
                rewardXp = 25,
                rewardGold = 150,
                difficulty = MissionDifficulty.NORMAL
            )
        ),
        weeklyMissions = listOf(
            MissionSnapshot(
                code = "WEEKLY_SELECT_100",
                title = "Chọn đúng một trăm số trong tuần",
                progress = 42,
                target = 100,
                completed = false,
                rewardXp = 75,
                rewardGold = 400,
                rewardGems = 2,
                difficulty = MissionDifficulty.ELITE
            )
        ),
        cosmetics = listOf(
            CosmeticSnapshot("frame_default", "Khung mặc định", CosmeticType.FRAME, unlocked = true, equipped = true),
            CosmeticSnapshot("frame_persistent", "Khung Bền bỉ", CosmeticType.FRAME, unlocked = true, equipped = false),
            CosmeticSnapshot("title_rookie", "Tân binh", CosmeticType.TITLE, unlocked = true, equipped = true),
            CosmeticSnapshot("title_diligent", "Chuyên cần", CosmeticType.TITLE, unlocked = true, equipped = false),
            CosmeticSnapshot("avatar_checkin_50", "Chuyên cần 50", CosmeticType.AVATAR, unlocked = true, equipped = false)
        )
    ),
    recentMatches = listOf(
        MatchHistorySnapshot(
            matchId = "match-win",
            roomName = "Phòng chung kết cuối tuần",
            gameMode = ProtocolGameMode.ORDER,
            opponentName = "Hiếu",
            playerScore = 50,
            opponentScore = 43,
            outcome = MatchHistoryOutcome.WIN,
            endedAtEpochMillis = 1L,
            eloChange = 18,
            matchType = MatchType.RANKED
        ),
        MatchHistorySnapshot(
            matchId = "match-loss",
            roomName = "Phòng thử thách tốc độ",
            gameMode = ProtocolGameMode.TIME_ATTACK,
            opponentName = "Người chơi có tên rất dài",
            playerScore = 21,
            opponentScore = 29,
            outcome = MatchHistoryOutcome.LOSS,
            endedAtEpochMillis = 2L,
            eloChange = -12,
            matchType = MatchType.RANKED
        )
    )
)
