package com.hienthai.fastowin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import com.hienthai.fastowin.protocol.ClanLeaderboardEntrySnapshot
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.protocol.LeaderboardEntrySnapshot
import com.hienthai.fastowin.protocol.LeaderboardSnapshot
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.ui.screens.LeaderboardScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Rule
import org.junit.Test

class LeaderboardClanLogoUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun playerClanIdentityAppearsAcrossSeasonAndCurrencyRankings() {
        val member = LeaderboardEntrySnapshot(1, "Member", "MEMBER01", 4, 5, 50,
            clanId = "clan-a", clanName = "Dragon Clan", clanLogoId = "dragon")
        val solo = LeaderboardEntrySnapshot(2, "Solo", "SOLO01", 2, 5, 40)
        val entries = listOf(member, solo)
        setLeaderboard(LeaderboardSnapshot(
            topPlayers = entries, currentPlayer = member,
            seasonTopPlayers = entries, seasonCurrentPlayer = member,
            previousSeasonName = "Previous", previousSeasonTopPlayers = entries, previousSeasonCurrentPlayer = member,
            topGoldPlayers = entries, currentGoldPlayer = member,
            topGemPlayers = entries, currentGemPlayer = member
        ))
        assertMemberClan()
        composeRule.onNodeWithTag("leaderboard_period_previous").performScrollTo().performClick()
        assertMemberClan()
        composeRule.onNodeWithTag("leaderboard_period_all_time").performScrollTo().performClick()
        assertMemberClan()
        composeRule.onNodeWithTag("leaderboard_player_metric_gold").performClick()
        assertMemberClan()
        composeRule.onNodeWithTag("leaderboard_player_metric_gems").performClick()
        assertMemberClan()
        composeRule.onNodeWithTag("leaderboard_players_list").performScrollToNode(hasText("Solo"))
        composeRule.onNodeWithTag("leaderboard_player_clan_SOLO01").assertDoesNotExist()
    }

    @Test
    fun clanLogoAppearsInCurrentPositionAndEveryClanMetric() {
        val clan = ClanLeaderboardEntrySnapshot(1, "clan-a", "Dragon Clan", 9000, 12, logoId = "dragon")
        setLeaderboard(LeaderboardSnapshot(topLevelClans = listOf(clan), currentLevelClan = clan,
            topGoldClans = listOf(clan), currentGoldClan = clan,
            topGemClans = listOf(clan), currentGemClan = clan))
        composeRule.onNodeWithTag("leaderboard_tab_clans").performClick()
        assertClanIcons()
        composeRule.onNodeWithTag("leaderboard_clan_metric_gold").performClick()
        assertClanIcons()
        composeRule.onNodeWithTag("leaderboard_clan_metric_gems").performClick()
        assertClanIcons()
    }

    private fun assertMemberClan() {
        composeRule.onNodeWithTag("leaderboard_players_list")
            .performScrollToNode(hasTestTag("leaderboard_current_player_clan_MEMBER01"))
        composeRule.onNodeWithTag("leaderboard_current_player_clan_MEMBER01").assertIsDisplayed()
        composeRule.onNodeWithTag("leaderboard_players_list")
            .performScrollToNode(hasTestTag("leaderboard_player_clan_MEMBER01"))
        composeRule.onNodeWithTag("leaderboard_player_clan_MEMBER01").assertIsDisplayed()
        // Return to filters so the next metric/period remains tappable on small phones.
        composeRule.onNodeWithTag("leaderboard_players_list")
            .performScrollToNode(hasTestTag("leaderboard_player_metric_filters"))
    }

    private fun assertClanIcons() {
        composeRule.onNodeWithTag("leaderboard_clans_list")
            .performScrollToNode(hasTestTag("leaderboard_current_clan_logo_clan-a"))
        composeRule.onNodeWithTag("leaderboard_current_clan_logo_clan-a", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("leaderboard_clans_list")
            .performScrollToNode(hasTestTag("leaderboard_clan_logo_clan-a"))
        composeRule.onNodeWithTag("leaderboard_clan_logo_clan-a", useUnmergedTree = true)
            .assertIsDisplayed().assertContentDescriptionEquals("Clan emblem: Dragon Clan")
        composeRule.onNodeWithTag("leaderboard_clans_list")
            .performScrollToNode(hasTestTag("leaderboard_clan_metric_filters"))
    }

    private fun setLeaderboard(leaderboard: LeaderboardSnapshot) {
        composeRule.setContent {
            ProvideLocalization(AppLanguage.ENGLISH) {
                FastToWinTheme {
                    LeaderboardScreen(GameState(leaderboard = leaderboard), {}, {}, {}, showBackButton = false)
                }
            }
        }
    }
}
