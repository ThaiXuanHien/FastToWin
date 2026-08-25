package com.hienthai.fastowin

import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.data.preferences.AppThemeMode
import com.hienthai.fastowin.protocol.LeaderboardSnapshot
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.PlayerProgressionSnapshot
import com.hienthai.fastowin.protocol.RankedTier
import com.hienthai.fastowin.protocol.SeasonHistoryEntrySnapshot
import com.hienthai.fastowin.protocol.SeasonRewardReceiptSnapshot
import com.hienthai.fastowin.protocol.seasonCosmeticReward
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.ui.screens.LeaderboardScreen
import com.hienthai.fastowin.ui.screens.SeasonHistoryScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SeasonHistoryUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun seasonHistoryRemainsReachableOnSmallPhoneWithLargeText() {
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 568.dp)) then
                    DeviceConfigurationOverride.FontScale(1.4f)
            ) {
                FastToWinTheme {
                    SeasonHistoryScreen(
                        state = seasonHistoryState(),
                        onBack = {},
                        onRefresh = {},
                        onOpenNotifications = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("season_history_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("season_history_list")
            .performScrollToNode(hasTestTag("season_history_item:3"))
        composeRule.onNodeWithTag("season_history_item:3").assertIsDisplayed()
        composeRule.onNodeWithTag("season_history_list")
            .performScrollToNode(hasTestTag("season_history_item:2"))
        composeRule.onNodeWithTag("season_history_item:2").assertIsDisplayed()
        composeRule.onNodeWithTag("season_history_reward:2").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Khung Mùa Bứt Phá • Vàng").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun seasonHistoryRendersInLandscapeDarkTheme() {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(720.dp, 400.dp))) {
                FastToWinTheme(preferences = AppPreferences(themeMode = AppThemeMode.DARK)) {
                    SeasonHistoryScreen(
                        state = seasonHistoryState(),
                        onBack = {},
                        onRefresh = {},
                        onOpenNotifications = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("season_history_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("season_history_list")
            .performScrollToNode(hasTestTag("season_history_item:3"))
        composeRule.onNodeWithTag("season_history_item:3").assertIsDisplayed()
        composeRule.onNodeWithTag("season_history_list")
            .performScrollToNode(hasTestTag("season_history_item:2"))
        composeRule.onNodeWithTag("season_history_reward:2").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun leaderboardHistoryActionUsesDedicatedNavigationCallback() {
        var opened = false
        composeRule.setContent {
            FastToWinTheme {
                LeaderboardScreen(
                    state = seasonHistoryState().copy(leaderboard = LeaderboardSnapshot()),
                    onBack = {},
                    onRefresh = {},
                    onOpenFriendProfile = {},
                    onOpenSeasonHistory = { opened = true },
                    showBackButton = false
                )
            }
        }

        composeRule.onNodeWithTag("leaderboard_players_list")
            .performScrollToNode(hasTestTag("open_season_history"))
        composeRule.onNodeWithTag("open_season_history").performClick()
        composeRule.runOnIdle { assertTrue(opened) }
    }
}

private fun seasonHistoryState(): GameState {
    val rewardedSeason = SeasonHistoryEntrySnapshot(
        seasonNumber = 2,
        seasonName = "Mùa Bứt Phá",
        endedAtEpochMillis = System.currentTimeMillis() - 30 * 86_400_000L,
        finalRating = 1_320,
        peakRating = 1_380,
        finalRank = 7,
        matchesPlayed = 24,
        placementMatchesPlayed = 5,
        reward = SeasonRewardReceiptSnapshot(
            seasonNumber = 2,
            seasonName = "Mùa Bứt Phá",
            tier = RankedTier.GOLD,
            peakRating = 1_380,
            gold = 1_000,
            gems = 1,
            awardedAtEpochMillis = System.currentTimeMillis() - 30 * 86_400_000L,
            cosmetic = seasonCosmeticReward(2, "Mùa Bứt Phá", RankedTier.GOLD),
            acknowledged = true
        )
    )
    val unrankedSeason = SeasonHistoryEntrySnapshot(
        seasonNumber = 3,
        seasonName = "Mùa Tăng Tốc",
        endedAtEpochMillis = System.currentTimeMillis() - 86_400_000L,
        finalRating = 1_080,
        peakRating = 1_120,
        matchesPlayed = 4,
        placementMatchesPlayed = 4
    )
    return GameState(
        profile = PlayerProfileSnapshot(
            userId = "player-hien",
            displayName = "Hiền",
            playerCode = "HIEN001",
            progression = PlayerProgressionSnapshot(
                gold = 2_500,
                gems = 12,
                seasonHistory = listOf(unrankedSeason, rewardedSeason)
            )
        )
    )
}
