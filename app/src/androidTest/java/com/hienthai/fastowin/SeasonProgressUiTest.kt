package com.hienthai.fastowin

import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.then
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.data.preferences.AppThemeMode
import com.hienthai.fastowin.protocol.LeaderboardSnapshot
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.PlayerProgressionSnapshot
import com.hienthai.fastowin.protocol.RankedTier
import com.hienthai.fastowin.protocol.SeasonRewardReceiptSnapshot
import com.hienthai.fastowin.protocol.SeasonSnapshot
import com.hienthai.fastowin.protocol.seasonCosmeticReward
import com.hienthai.fastowin.protocol.seasonTierRewards
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.ui.screens.LeaderboardScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SeasonProgressUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun seasonProgressAndAllTierRewardsRemainReachableOnSmallPhoneWithLargeText() {
        val profile = seasonProfileFixture()

        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 568.dp)) then
                    DeviceConfigurationOverride.FontScale(1.4f)
            ) {
                FastToWinTheme {
                    LeaderboardScreen(
                        state = GameState(
                            profile = profile,
                            leaderboard = LeaderboardSnapshot(previousSeasonName = "Mùa Khởi Đầu")
                        ),
                        onBack = {},
                        onRefresh = {},
                        onOpenFriendProfile = {},
                        showBackButton = false
                    )
                }
            }
        }

        composeRule.onNodeWithTag("season_progress_card").assertIsDisplayed()
        composeRule.onNodeWithTag("season_progress").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "Còn 100 Elo để lên Vàng"
            )
        )
        composeRule.onNodeWithTag("season_rewards_toggle").performClick()
        composeRule.onNodeWithTag("season_reward:CHALLENGER")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("season_reward_cosmetic:CHALLENGER").assertIsDisplayed()
        composeRule.onNodeWithTag("leaderboard_players_list")
            .performScrollToNode(hasTestTag("leaderboard_period_previous"))
        composeRule.onNodeWithTag("leaderboard_period_previous").performClick()
        composeRule.onNodeWithTag("leaderboard_players_list")
            .performScrollToNode(hasTestTag("season_reward_receipt"))
        composeRule.onNodeWithTag("season_reward_receipt").assertIsDisplayed()
        composeRule.onNodeWithTag("season_reward_receipt_cosmetic").assertIsDisplayed()
        composeRule.onNodeWithTag("avatar_frame:season_1_gold").assertIsDisplayed()
    }

    @Test
    fun seasonProgressRendersInLandscapeDarkTheme() {
        val profile = seasonProfileFixture()

        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(720.dp, 400.dp))
            ) {
                FastToWinTheme(preferences = AppPreferences(themeMode = AppThemeMode.DARK)) {
                    LeaderboardScreen(
                        state = GameState(
                            profile = profile,
                            leaderboard = LeaderboardSnapshot(previousSeasonName = "Mùa Khởi Đầu")
                        ),
                        onBack = {},
                        onRefresh = {},
                        onOpenFriendProfile = {},
                        showBackButton = false
                    )
                }
            }
        }

        composeRule.onNodeWithTag("season_progress_card").assertIsDisplayed()
        composeRule.onNodeWithTag("season_rewards_toggle").performClick()
        composeRule.onNodeWithTag("season_reward:CHALLENGER")
            .performScrollTo()
            .assertIsDisplayed()
    }
}

private fun seasonProfileFixture(): PlayerProfileSnapshot {
    val season = SeasonSnapshot(
        name = "Mùa Bứt Phá",
        tier = "Bạc",
        rating = 1_200,
        endsAtEpochMillis = System.currentTimeMillis() + 2 * 86_400_000L,
        rewardDescription = "Thưởng theo bậc cao nhất",
        placementMatchesPlayed = 5,
        peakRating = 1_350,
        tierRewards = seasonTierRewards(2, "Mùa Bứt Phá")
    )
    return PlayerProfileSnapshot(
        userId = "player-hien",
        displayName = "Hiền",
        playerCode = "HIEN001",
        progression = PlayerProgressionSnapshot(
            season = season,
            latestSeasonReward = SeasonRewardReceiptSnapshot(
                seasonName = "Mùa Khởi Đầu",
                tier = RankedTier.GOLD,
                peakRating = 1_380,
                gold = 1_000,
                gems = 1,
                awardedAtEpochMillis = System.currentTimeMillis() - 86_400_000L,
                cosmetic = seasonCosmeticReward(1, "Mùa Khởi Đầu", RankedTier.GOLD)
            )
        )
    )
}
