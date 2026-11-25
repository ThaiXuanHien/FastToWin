package com.hienthai.fastowin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class LeaderboardClanLogoUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun playerRowsHideClanIdentityAndOpenThePublicPlayerPopup() {
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
        assertNoPlayerClan()
        composeRule.onNodeWithTag("leaderboard_period_previous").performScrollTo().performClick()
        assertNoPlayerClan()
        composeRule.onNodeWithTag("leaderboard_period_all_time").performScrollTo().performClick()
        assertNoPlayerClan()
        composeRule.onNodeWithTag("leaderboard_player_metric_gold").performClick()
        assertNoPlayerClan()
        composeRule.onNodeWithTag("leaderboard_player_metric_gems").performClick()
        composeRule.onNodeWithTag("leaderboard_players_list").performScrollToNode(hasText("Solo"))
        composeRule.onNodeWithText("Solo").performClick()
        composeRule.onNodeWithTag("leaderboard_player_profile_dialog").assertIsDisplayed()
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

    @Test
    fun outsideTopPlayerUsesTheSameDetailsBelowNameInEveryPeriodAndMetric() {
        val top = LeaderboardEntrySnapshot(1, "Leader", "TOP01", 4, 5, 50, eloRating = 1800,
            lifetimeEarnedGold = 1200, lifetimeEarnedGems = 800)
        val current = top.copy(rank = 42, displayName = "Current player", playerCode = "SELF01")
        setLeaderboard(LeaderboardSnapshot(
            topPlayers = listOf(top), currentPlayer = current,
            seasonTopPlayers = listOf(top), seasonCurrentPlayer = current,
            previousSeasonName = "Previous", previousSeasonTopPlayers = listOf(top), previousSeasonCurrentPlayer = current,
            topGoldPlayers = listOf(top), currentGoldPlayer = current,
            topGemPlayers = listOf(top), currentGemPlayer = current
        ))
        assertPlayerDetails("1800")
        composeRule.onNodeWithTag("leaderboard_period_previous").performScrollTo().performClick()
        assertPlayerDetails("1800")
        composeRule.onNodeWithTag("leaderboard_period_all_time").performScrollTo().performClick()
        assertPlayerDetails("1800")
        composeRule.onNodeWithTag("leaderboard_player_metric_gold").performClick()
        assertPlayerDetails("1.200")
        composeRule.onNodeWithTag("leaderboard_player_metric_gems").performClick()
        assertPlayerDetails("800")
    }

    @Test
    fun outsideTopClanUsesRegularRowsAndBothRowsOpenClanInEveryMetric() {
        val top = ClanLeaderboardEntrySnapshot(1, "top", "Top clan", 9000, 12,
            level = 7, experiencePoints = 12500, donatedGold = 4200, donatedGems = 300)
        val current = top.copy(rank = 42, clanId = "own", clanName = "Own clan")
        val opened = mutableListOf<String>()
        setLeaderboard(LeaderboardSnapshot(
            topLevelClans = listOf(top), currentLevelClan = current,
            topGoldClans = listOf(top), currentGoldClan = current,
            topGemClans = listOf(top), currentGemClan = current
        ), onOpenClan = { opened += it })
        composeRule.onNodeWithTag("leaderboard_tab_clans").performClick()
        listOf("level" to "12.500", "gold" to "4.200", "gems" to "300")
            .forEach { (metric, value) ->
                composeRule.onNodeWithTag("leaderboard_clan_metric_$metric").performClick()
                // Thẻ "bang của bạn" ghim ở đáy màn hình; dưới tên là cấp bang, điểm ở cột phải.
                assertDetailsBelowName(null, "leaderboard_current_clan", "Level 7")
                assertValue("leaderboard_current_clan", value)
                composeRule.onNodeWithTag("leaderboard_current_clan").assertIsSelected().performClick()
                assertDetailsBelowName("leaderboard_clans_list", "leaderboard_clan_top", "Level 7")
                assertValue("leaderboard_clan_top", value)
                composeRule.onNodeWithTag("leaderboard_clan_top").performClick()
                composeRule.onNodeWithTag("leaderboard_clans_list")
                    .performScrollToNode(hasTestTag("leaderboard_clan_metric_filters"))
            }
        composeRule.runOnIdle { assertEquals(listOf("own", "top", "own", "top", "own", "top"), opened) }
    }

    private fun assertPlayerDetails(value: String) {
        // Thẻ "vị trí của bạn" ghim ở đáy: "#42", tên, bậc bên dưới tên và điểm ở cột phải.
        assertDetailsBelowName(null, "leaderboard_current_player", "Diamond")
        assertValue("leaderboard_current_player", value)
        composeRule.onNodeWithTag("leaderboard_current_player").assertIsSelected()
            .assertTextContains("#42", substring = true).assertHasClickAction().performClick()
        composeRule.onNodeWithTag("leaderboard_player_profile_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()
        assertDetailsBelowName("leaderboard_players_list", "leaderboard_player_TOP01", "Diamond")
        assertValue("leaderboard_player_TOP01", value)
        composeRule.onNodeWithTag("leaderboard_players_list")
            .performScrollToNode(hasTestTag("leaderboard_player_metric_filters"))
    }

    private fun assertValue(rowTag: String, value: String) {
        composeRule.onNodeWithTag("${rowTag}_value", useUnmergedTree = true).assertTextContains(value, substring = true)
    }

    private fun assertDetailsBelowName(listTag: String?, rowTag: String, detail: String) {
        if (listTag != null) composeRule.onNodeWithTag(listTag).performScrollToNode(hasTestTag(rowTag))
        val name = composeRule.onNodeWithTag("${rowTag}_name", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val details = composeRule.onNodeWithTag("${rowTag}_details", useUnmergedTree = true)
            .assertTextContains(detail, substring = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Ranking details must appear below the name", details.top >= name.bottom)
        assertEquals("Ranking details must align with the name", name.left, details.left, 0.5f)
    }

    private fun assertNoPlayerClan() {
        composeRule.onNodeWithTag("leaderboard_players_list")
            .performScrollToNode(hasText("Solo"))
        composeRule.onNodeWithText("Dragon Clan").assertDoesNotExist()
        composeRule.onNodeWithText("Member · You").assertDoesNotExist()
        // Return to filters so the next metric/period remains tappable on small phones.
        composeRule.onNodeWithTag("leaderboard_players_list")
            .performScrollToNode(hasTestTag("leaderboard_player_metric_filters"))
    }

    private fun assertClanIcons() {
        // Thẻ bang của bạn ghim ở đáy màn hình (ngoài danh sách cuộn).
        composeRule.onNodeWithTag("leaderboard_current_clan_logo_clan-a", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("leaderboard_clans_list", useUnmergedTree = true)
            .performScrollToNode(hasTestTag("leaderboard_clan_logo_clan-a"))
        composeRule.onNodeWithTag("leaderboard_clan_logo_clan-a", useUnmergedTree = true)
            .assertIsDisplayed().assertContentDescriptionEquals("Clan emblem: Dragon Clan")
        composeRule.onNodeWithTag("leaderboard_clans_list")
            .performScrollToNode(hasTestTag("leaderboard_clan_metric_filters"))
    }

    private fun setLeaderboard(leaderboard: LeaderboardSnapshot, onOpenClan: (String) -> Unit = {}) {
        composeRule.setContent {
            ProvideLocalization(AppLanguage.ENGLISH) {
                FastToWinTheme {
                    LeaderboardScreen(GameState(leaderboard = leaderboard), {}, {}, showBackButton = false,
                        onOpenClan = onOpenClan)
                }
            }
        }
    }
}
